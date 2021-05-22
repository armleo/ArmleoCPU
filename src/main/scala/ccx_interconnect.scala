package armleocpu

import CacheConsts._
import Consts._
import chisel3._
import chisel3.util._

class RoundRobin(n: Int) extends Module {
    val io = IO(new Bundle{
        val req = Input(Vec(n, Bool()))
        val grant = Output(Vec(n, Bool()))
        val next = Input(Bool())
        val choice = Output(UInt(n.W))
    })
    val rotate_ptr = RegInit(0.U((log2Ceil(n)).W))
    val shift_req = (Cat(io.req.asUInt(), io.req.asUInt()) >> rotate_ptr)(n-1, 0)
    val shift_grant = Wire(Vec(n, Bool()))

    //shift_grant := 0.U

    for(i <- 0 until n) {
        shift_grant(i) := PriorityEncoder(shift_req) === i.U
    }
    val grant_comb = (Cat(shift_grant.asUInt(), shift_grant.asUInt()) << rotate_ptr)(n+n-1, n)
    
    io.choice := PriorityEncoder(grant_comb)

    io.grant := grant_comb.asBools()
    when(io.next) {
        when(io.choice === (n - 1).U) {
            rotate_ptr := 0.U
        }.otherwise {
            rotate_ptr := io.choice + 1.U
        }
    }
}


// Note: Only write-through is supported
// Note: Cache line size is 64 bytes and is fixed
// Note: This interconnect does not implement full ACE, but minimal set required by core
// Note: ReadNoSnoop is used for peripheral bus loads
//              It does not cause any Cache access
// Note: WriteNoSnoop is used to write to peripheral bus stores
// Note: The reason is that peripheral address space is separated from Cache address space
// Coherency protocols:
// Note: WriteUnique is used to write to memory. Only write-through is supported
//      CleanInvalid is issued to Cache memory
// Note: ReadShared is used by Cache to reserve line for load operations
// Note: Barrier is just transfered to all Cache lines
// Note: CRRESP[1] (ECC Error) is ignored

// TODO: Add statistics

// Note: It is expected for memory to prioritize write requests for this interconnect

// N: Shows amount of caches. Not amount of cores
class CCXInterconnect(n: Int, debug: Boolean = true, StatisticsBaseAddr: BigInt = BigInt("FFFFFFFF", 16), core_id_width: Int = 1) extends Module {
    val mbus_id_width = core_id_width + log2Ceil(n)

    val p = new AXIParams(64, 64, core_id_width)
    val mparams = new AXIParams(64, 64, mbus_id_width)
    

    val io = IO(new Bundle{
        val mbus = new AXIHostIF(mparams)

        val corebus = Vec(n, Flipped(new ACEHostIF(p)))
        // Even tho it's called corebus, it's actually connection to a cache of the core, which is double the amount of cores
    })

    // TODO: Keep statistics and return it when requested StatisticsBaseAddr
    
    

    // Shorthands:
    // TODO: In future replace with registered buffer
    val aw = VecInit(Seq.tabulate(n) (i => io.corebus(i).aw))
    val ar = VecInit(Seq.tabulate(n) (i => io.corebus(i).ar))
    val w = VecInit(Seq.tabulate(n) (i => io.corebus(i).w))
    val cr = VecInit(Seq.tabulate(n) (i => io.corebus(i).cr))
    val cd = VecInit(Seq.tabulate(n) (i => io.corebus(i).cd))

    // TODO: Add shorthands for other buses
    // val b = 
    // val r = 
    // val ac = 

    val mbus = io.mbus
    
    val arb = Module(new RoundRobin(n))

    arb.io.next := false.B

    for(i <- 0 until n) {
        arb.io.req(i)               := ar(i).valid || aw(i).valid


                aw(i).ready         := false.B
                ar(i).ready         := false.B
                w(i).ready          := false.B
                cr(i).ready         := false.B
                cd(i).ready         := false.B

        io.corebus(i).b.valid       := false.B
        io.corebus(i).b.bits        := 0.U.asTypeOf(io.corebus(i).b.bits)
        io.corebus(i).b.bits        := mbus.b.bits

        io.corebus(i).r.valid       := false.B
        io.corebus(i).r.bits        := 0.U.asTypeOf(io.corebus(i).r.bits)

        io.corebus(i).ac.valid      := false.B
        io.corebus(i).ac.bits       := 0.U.asTypeOf(io.corebus(i).ac.bits)
    }


    mbus.ar.bits            := 0.U.asTypeOf(mbus.ar.bits)
    mbus.ar.valid           := false.B
    
    mbus.r.ready            := false.B
    mbus.w.bits             := 0.U.asTypeOf(mbus.w.bits)
    mbus.w.valid            := false.B
    mbus.b.ready            := false.B

    val STATE_WIDTH             = 4

    val STATE_IDLE              = 0.U(STATE_WIDTH.W) // Wait for request, branch to READ/WRITE

    // Branch WRITE
    val STATE_WRITE_INVALIDATE      = 1.U(STATE_WIDTH.W) // Only for WriteUnique: Send invalidate to all cache
    val STATE_WRITE_INVALIDATE_RESP = 2.U(STATE_WIDTH.W) // Wait for response from all caches
    val STATE_WRITE_ADDRESS         = 3.U(STATE_WIDTH.W) // Send write request to mbus
    val STATE_WRITE_DATA            = 4.U(STATE_WIDTH.W) // Send write data to mbus
    val STATE_WRITE_RESPONSE        = 5.U(STATE_WIDTH.W) // Return response to requesting host
    val STATE_WRITE_WACK            = 6.U(STATE_WIDTH.W) // Wait for writeackowledge


    // Branch READ
    val STATE_READ_POLL             =  7.U(STATE_WIDTH.W) // Only for ReadShared
    val STATE_READ_RETURN_RESPONSE  =  8.U(STATE_WIDTH.W) // Only for ReadShared
    val STATE_READ_RETURN_DATA      =  9.U(STATE_WIDTH.W) // Only for ReadShared
    val STATE_READ_ADDRESS          = 10.U(STATE_WIDTH.W) // ReadShared failed cache hit /ReadNoSnoop
    val STATE_READ_RESPONSE         = 11.U(STATE_WIDTH.W) // ReadShared failed cache hit /ReadNoSnoop
    val STATE_READ_RACK             = 12.U(STATE_WIDTH.W) // Common for both paths


    val state = RegInit(STATE_IDLE)

    // address write channel
    val current_active_num = RegInit(0.U(log2Ceil(n).W))
    
    val ac_sent = Reg(Vec(n, Bool()))
    val cr_done = Reg(Vec(n, Bool()))
    val cd_done = Reg(Vec(n, Bool()))

    val data_available = RegInit(false.B) // Is data available in cache?
    val data_available_host = Reg(Vec(n, Bool()))
    
    val return_host_select = RegInit(0.U(log2Ceil(n).W))
    val return_host_select_valid = RegInit(false.B)

    val rid = Reg(UInt(core_id_width.W))

    val atomic_reserved = RegInit(false.B)
    val atomic_op = RegInit(false.B)
    val atomic_error = RegInit(false.B)
    val atomic_reservation_address = Reg(UInt(64.W))

    mbus.aw.valid       := false.B
    // Just pass data to memory
    mbus.aw.bits.addr  := aw(current_active_num).bits.addr
    mbus.aw.bits.size  := aw(current_active_num).bits.size
    mbus.aw.bits.len   := aw(current_active_num).bits.len
    mbus.aw.bits.burst := aw(current_active_num).bits.burst
    mbus.aw.bits.id    := Cat(current_active_num, aw(current_active_num).bits.id)
    mbus.aw.bits.lock  := aw(current_active_num).bits.lock
    mbus.aw.bits.cache := aw(current_active_num).bits.cache
    // TODO: In future set or clear CACHE bits accrodingly to config
    mbus.aw.bits.prot  := aw(current_active_num).bits.prot
    mbus.aw.bits.qos   := aw(current_active_num).bits.qos

    mbus.w.bits        :=  w(current_active_num).bits
    
    mbus.ar.bits       := ar(current_active_num).bits
    mbus.ar.bits.id    := Cat(current_active_num, ar(current_active_num).bits.id)
    // TODO: make WriteUnique
    // TODO: Add ReadShared
    // TODO: Atomic operations

    // Cases for requests:
    // WriteNoSnoop 8/4/2/1
    // WriteUnique 8/4/2/1
    // WriteUnique 8/4 atomic
    // ReadShared  Burst 8x8
    // ReadShared 8/4 atomic reserve
    // ReadNoSnoop 8/4/2/1

    // TODO: Do atomics response return
    for(i <- 0 until n) {
        io.corebus(i).b.bits.id := mbus.b.bits.id(core_id_width-1, 0)
        // Address write
        when(state === STATE_IDLE) {
            ac_sent(i) := false.B
            cd_done(i) := false.B
            cr_done(i) := false.B
            data_available_host(i) := false.B
            data_available := false.B
            return_host_select := 0.U
            return_host_select_valid := false.B
            atomic_error := false.B
            atomic_op := false.B

            // Make decision on which bus to process and register request
            // Prioritize write requests
            when(aw(arb.io.choice).valid) { // If any transactions are active and granted
                when(aw(arb.io.choice).bits.isWriteUnique()) {
                    state := STATE_WRITE_INVALIDATE
                    when(aw(arb.io.choice).bits.lock) {
                        chisel3.assert(
                            (aw(arb.io.choice).bits.size === "b010".U) ||
                            (aw(arb.io.choice).bits.size === "b011".U)
                        ) // 8/4 bytes per beat
                        
                        atomic_op := true.B
                        if(debug) {
                            when(i.U === arb.io.choice) {
                                printf("Atomic WriteUnique: 0x%x\n", aw(arb.io.choice).bits.addr)
                            }
                        }
                        
                    } .otherwise {
                        chisel3.assert(
                            (aw(arb.io.choice).bits.size <= "b011".U)
                        )// 8/4/2/1 bytes per beat
                        if(debug) {
                            when(i.U === arb.io.choice) {
                                printf("WriteUnique start: 0x%x\n", aw(arb.io.choice).bits.addr)
                            }
                        }
                    }
                    // TODO: Implement properly, what is this????
                    // If locking && not reserved => fail with no write
                    // If locking && reserved && reservation address does not match => fail with no write
                    // If locking && reserved && reservation address matches => success
                    // If not locking && reserved && reservation address matches => success write, remove reservetion
                    // If not locking && not reserved / resrvation does not match => success write
                    chisel3.assert(!aw(arb.io.choice).bits.lock) // Temp TODO: Add proper implementation
                    // If locking and success => EXOKAY
                    // If locking and fail => OKAY
                    // If not locking  => OKAY

                    when(atomic_reserved && 
                        (atomic_reservation_address === Cat(
                                aw(current_active_num).bits.addr(63, 3),
                                0.U(3.W)
                            )
                        )
                    ) {
                        atomic_reserved := false.B

                    } .otherwise {
                        atomic_error := aw(arb.io.choice).bits.lock
                        // Error only if request was locking
                        // Otherwise it's not an atomic error but just ordinary write
                        // That will trigger reservation fail
                    }
                } .elsewhen (aw(arb.io.choice).bits.isWriteNoSnoop()) {
                    state := STATE_WRITE_ADDRESS
                    if(debug) {
                        when(i.U === arb.io.choice) {
                            printf("WriteNoSnoop start: 0x%x\n", aw(arb.io.choice).bits.addr)
                        }
                    }
                    chisel3.assert(aw(arb.io.choice).bits.size <= "b011".U) // 8/4/2/1 bytes
                    
                    chisel3.assert(!aw(arb.io.choice).bits.lock, "!ERROR! Interconnect: atomics WriteNoSnoop not supported")
                } .otherwise {
                    chisel3.assert(false.B)
                    printf("!ERROR! Interconnect: Error wrong write transaction\n")
                }
                chisel3.assert(aw(arb.io.choice).bits.burst === 0.U) // Fixed
                chisel3.assert(aw(arb.io.choice).bits.len === 0.U) // One beat
                
                // TODO: Add assertion for Cache
            } .elsewhen(ar(arb.io.choice).valid) {
                when(ar(arb.io.choice).bits.isReadNoSnoop()) {
                    state := STATE_READ_ADDRESS
                    chisel3.assert(ar(arb.io.choice).bits.burst === 0.U) // Fixed
                    chisel3.assert(ar(arb.io.choice).bits.len === 0.U) // One beat
                    chisel3.assert(ar(arb.io.choice).bits.size <= "b011".U) // 8/4/2/1 bytes
                    chisel3.assert(!ar(arb.io.choice).bits.lock)
                    if(debug) {
                        when(i.U === arb.io.choice) {
                            printf("ReadNoSnoop (len=0) start: addr=0x%x\n", ar(arb.io.choice).bits.addr)
                        }
                    }
                    rid := ar(arb.io.choice).bits.id
                } .elsewhen (ar(arb.io.choice).bits.isReadClean()) {
                    
                    // TODO: Reservation for atomics
                    
                    when(ar(arb.io.choice).bits.len === 7.U) {
                        state := STATE_READ_POLL
                        chisel3.assert(ar(arb.io.choice).bits.burst === 2.U) // Assert its wrap
                        //assert(!ar(arb.io.choice).bits.lock, "!ERROR! Interconnect: atomics read shared required len = 0")
                        chisel3.assert(ar(arb.io.choice).bits.size === "b011".U) // 8 bytes per beat
                        // TODO: If atomic request respond with atomic error OKAY response with valid data
                        atomic_error := true.B
                        atomic_op := ar(arb.io.choice).bits.lock
                        if(debug) {
                            when(i.U === arb.io.choice) {
                                printf("ReadShared (len=8) start: addr=0x%x, lock=%b\n", ar(arb.io.choice).bits.addr, ar(arb.io.choice).bits.lock)
                            }
                        }
                        rid := ar(arb.io.choice).bits.id
                    } .elsewhen (ar(arb.io.choice).bits.len === 0.U) {
                        // If len === 0 then read it from memory, because only wrapped full page is supported on AC bus
                        state := STATE_READ_ADDRESS
                        when(ar(arb.io.choice).bits.lock) {
                            atomic_reserved := true.B
                            atomic_reservation_address := Cat(ar(arb.io.choice).bits.addr(63, 3), 0.U(3.W))
                            chisel3.assert(// Singular transfer
                                        (ar(arb.io.choice).bits.size === "b010".U) // 4 bytes
                                        || (ar(arb.io.choice).bits.size === "b011".U) // 8 bytes
                            )
                            atomic_op := true.B
                            // Atomic read cant, fail
                            //atomic_error := 
                        }
                        if(debug) {
                            when(i.U === arb.io.choice) {
                                printf("ReadShared (len=1) start: addr=0x%x, lock=%b\n", ar(arb.io.choice).bits.addr, ar(arb.io.choice).bits.lock)
                            }
                        }
                        // TODO: Respond with EXOKAY
                        chisel3.assert(
                            (ar(arb.io.choice).bits.size <= "b011".U))
                        chisel3.assert(ar(arb.io.choice).bits.burst === 0.U) // Assert its fixed
                    } .otherwise {
                        printf("!ERROR! Interconnect: Error wrong read transaction len\n")
                        chisel3.assert(false.B)
                    }
                    
                    rid := ar(arb.io.choice).bits.id
                } .otherwise {
                    printf("!ERROR! Interconnect: Error wrong read transaction\n")
                    chisel3.assert(false.B)
                }
                
                //assert(!ar(arb.io.choice).bits.lock, "!ERROR! Interconnect: atomics not supported yet")
            }
            arb.io.next := true.B
            current_active_num := arb.io.choice
        } .elsewhen (state === STATE_WRITE_INVALIDATE) {
            // Send invalidate to all cache
            // TODO: Test
            io.corebus(i).ac.bits.addr := aw(current_active_num).bits.addr
            io.corebus(i).ac.bits.snoop := "b1001".U
            // TODO: Maybe MakeInvalid would be better?
            // This should work because Cache is write-through so dirty bits are impossible
            io.corebus(i).ac.bits.prot := aw(current_active_num).bits.prot

            when(current_active_num =/= i.U) {
                // For snooped hosts wait for ready
                io.corebus(i).ac.valid := !ac_sent(i)
                when(io.corebus(i).ac.ready) {
                    ac_sent(i) := true.B
                }
                
            } .otherwise {
                // For requester host do not send snoop request
                ac_sent(i) := true.B
            }

            when(ac_sent.asUInt().andR) {
                    state := STATE_WRITE_INVALIDATE_RESP
                }
            
        } .elsewhen (state === STATE_WRITE_INVALIDATE_RESP) {
            // TODO: Wait for all responses
            // Then porcess them
            cr_done(current_active_num) := true.B
            when(cr(i).valid) {
                cr(i).ready := true.B
                chisel3.assert(!cr(i).bits.resp(0), "!ERROR! Interconnect: Snoop response unexpected data transfer")
                chisel3.assert(!cr(i).bits.resp(1), "!ERROR! Interconnect: Snoop response unexpected ecc error")
                chisel3.assert(!cr(i).bits.resp(2), "!ERROR! Interconnect: Snoop response unexpected dirty")
                cr_done(i) := true.B
            }
            when(cr_done.asUInt().andR) {
                state := STATE_WRITE_ADDRESS
            }
            // TODO: When all responses are done jump to write address state
        } .elsewhen (state === STATE_WRITE_ADDRESS) {
            

            // afterwards 
            mbus.aw.valid := true.B
            
            when(mbus.aw.ready) {
                state := STATE_WRITE_DATA
            }
            aw(current_active_num).ready := mbus.aw.ready
            chisel3.assert(aw(current_active_num).valid) // Assert that valid is asserted
            // It's AXI violation to de assert it
        } .elsewhen (state === STATE_WRITE_DATA) {
            mbus.w.valid := w(current_active_num).valid
            w(current_active_num).ready := mbus.w.ready

            when(w(current_active_num).valid &&
                w(current_active_num).ready &&
                w(current_active_num).bits.last) {
                    // Last transaction
                    state := STATE_WRITE_RESPONSE
            }
        } .elsewhen (state === STATE_WRITE_RESPONSE) {
            // TODO: Do proper response generation for atomic
            io.corebus(current_active_num).b.valid := mbus.b.valid
            mbus.b.ready := io.corebus(current_active_num).b.ready
            when(mbus.b.valid && mbus.b.ready) {
                state := STATE_WRITE_WACK
            }
        } .elsewhen (state === STATE_WRITE_WACK) {
            when(io.corebus(current_active_num).wack) {
                state := STATE_IDLE
            }
        } .elsewhen (state === STATE_READ_POLL) {
            // Send polls to caches
            io.corebus(i).ac.bits.addr := ar(current_active_num).bits.addr
            io.corebus(i).ac.bits.snoop := "b0010".U // ReadClean
            io.corebus(i).ac.bits.prot := aw(current_active_num).bits.prot
            when(current_active_num =/= i.U) {
                io.corebus(i).ac.valid := !ac_sent(i)
            } .otherwise {
                ac_sent(i) := true.B // For active one no need to poll it
            }
            when(io.corebus(i).ac.ready) {
                ac_sent(i) := true.B
            }
            when(ac_sent.asUInt().andR) {
                state := STATE_READ_RETURN_RESPONSE
            }
            // TODO: Test it
        } .elsewhen (state === STATE_READ_RETURN_RESPONSE) {
            // TODO: Do proper response generation for atomic
            when(current_active_num =/= i.U) {
                // Wait for responses
                when(cr(i).valid) {
                    cr_done(i) := true.B
                    cr(i).ready := true.B
                    data_available := data_available | cr(i).bits.resp(0)
                    chisel3.assert(!cr(i).bits.resp(1), "!ERROR! Interconnect: Read request ECC error not supported") // ECC is not supported
                    chisel3.assert(!cr(i).bits.resp(2), "!ERROR! Interconnect: Read request snoop response dirty bit set") // Dirty is not supported
                    data_available_host(i) := cr(i).bits.resp(0)
                }
            } .otherwise {
                cr_done(i) := true.B // For active one no need to wait for response
            }

            when(cr_done.asUInt().andR) {
                when(data_available) {
                    state := STATE_READ_RETURN_DATA
                    ar(current_active_num).ready := true.B
                } .otherwise {
                    state := STATE_READ_ADDRESS
                }
            }
            
            // If any responses have Return Data set then go to return data
            // Else go to STATE_READ_ADDRESS
            
            // TODO: 
        } .elsewhen (state === STATE_READ_RETURN_DATA) {
            // If any data is valid and no selection wad done, select it
            // Cases for hosts:

            
            
            // Unselected no data
            // Unselected data
            // Selected data
            // 1. Requester is not requested so cd(i).valid cant be set for requester
            
            // 2. No selection
            when(cd(i).valid && !return_host_select_valid) { // MSB highest priority to LSB lowest
                return_host_select := i.U
                return_host_select_valid := true.B
            }
            // Selected
            when(return_host_select_valid) {
                // Unselected hosts, no data, nothing to do
                // For requester data_available_host === 0, so this is also case for requester host
                when(!data_available_host(i)) {
                    cd_done(i) := true.B
                }
                
                when(data_available_host(i)) {
                    // Unselected hosts, data
                    when(return_host_select =/= i.U) {
                        cd(i).ready := true.B
                        when(cd(i).valid && cd(i).ready && cd(i).bits.last) {
                            cd_done(i) := true.B
                        }
                    }

                    // Selected host
                    when(return_host_select === i.U) {
                        when(cd(i).valid && cd(i).ready && cd(i).bits.last) {
                            cd_done(i) := true.B
                        }
                        io.corebus(current_active_num).r.valid := cd(return_host_select).valid
                        cd(return_host_select).ready := io.corebus(current_active_num).r.ready
                        io.corebus(current_active_num).r.bits.last := cd(return_host_select).bits.last
                        io.corebus(current_active_num).r.bits.data := cd(return_host_select).bits.data
                        io.corebus(current_active_num).r.bits.resp := "b1000".U // TODO: Atomic access
                        io.corebus(current_active_num).r.bits.id := rid
                    }
                }
                

                // All memories done
                when(cd_done.asUInt().andR) {
                    state := STATE_READ_RACK
                }
            }
            

            io.corebus(i).r.bits.resp := "b1000".U // Shared not dirty and available
            io.corebus(i).r.bits.id   := rid
            io.corebus(i).r.bits.data := cd(return_host_select).bits.data
            io.corebus(i).r.bits.last := cd(return_host_select).bits.last
            
        } .elsewhen (state === STATE_READ_ADDRESS) {
            // afterwards 
            mbus.ar.valid := true.B
            
            when(mbus.ar.ready) {
                state := STATE_READ_RESPONSE
            }
            ar(current_active_num).ready := mbus.ar.ready
            chisel3.assert(ar(current_active_num).valid) // Assert that valid is asserted
            // TODO:
        } .elsewhen (state === STATE_READ_RESPONSE) {
            // TODO: Do proper response generation for atomic
            assert(io.corebus(i).r.bits.id === rid)

            io.corebus(i).r.bits.data := io.mbus.r.bits.data
            io.corebus(i).r.bits.last := io.mbus.r.bits.last
            io.corebus(i).r.bits.id   := io.mbus.r.bits.id(core_id_width-1, 0)
            io.corebus(i).r.bits.resp := Cat("b10".U, io.mbus.r.bits.resp)

            // TODO: Atomic access response generation


            io.mbus.r.ready := io.corebus(current_active_num).r.ready
            io.corebus(current_active_num).r.valid := io.mbus.r.valid

            when(io.corebus(current_active_num).r.valid && io.corebus(current_active_num).r.ready && io.corebus(current_active_num).r.bits.last) {
                state := STATE_READ_RACK
            }
        } .elsewhen (state === STATE_READ_RACK) {
            when(io.corebus(current_active_num).rack) {
                state := STATE_IDLE
            }
            // TODO: 
        } .otherwise {
            printf("!ERROR! Interconnect: Invalid state\n")
            chisel3.assert(false.B)
        }
    }
}