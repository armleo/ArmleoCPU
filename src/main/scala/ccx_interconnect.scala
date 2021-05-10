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
    val grant_comb = (Cat(shift_grant.asUInt(), shift_grant.asUInt()) << rotate_ptr)(n-1, 0)
    
    io.choice := PriorityEncoder(grant_comb)

    io.grant := grant_comb.asBools()
    when(io.next) {
        when(PriorityEncoder(grant_comb) === (n - 1).U) {
            rotate_ptr := 0.U
        }.otherwise {
            rotate_ptr := PriorityEncoder(grant_comb) + 1.U
        }
    }
}


// Note: Cache line size is 64 bytes and is fixed
// Note: This interconnect does not implement full ACE, but minimal set required by core
// Note: ReadNoSnoop is used for peripheral bus loads
//              It does not cause any Cache access
//              Instead data is requested from PBUS
// Note: WriteNoSnoop is used to write to peripheral bus stores
// Note: The reason is that peripheral address space is separated from Cache address space
// Coherency protocols:
// Note: ReadUnique for reserving a Cache line in store operations
//              It issues ReadUnique to every Cache, and if not Cache responds with data,
//              Then return it to requester
//              If no cache contains data read it from memory
//              Providing cache must invalidate it's line
//              All caches containing data invalidate that line
// Note: CleanUnique for reserving already cached line
//              Interconnect issues CleanUnique and if any Cache contains data it is returned to interconnect, and invalidating it
//              If no data is returned, read it from memory
//              Returned Line might be dirty
// Note: ReadShared is used by Cache to reserve line for load operations
//              If ReadShared is received then possibly dirty line is passed to requester
//              Each Cache can respond with Cache line in any state
//              Providing cache must invalidate it's line if it's returning unique line
// Note: WriteClean is used to perform write of dirty line to Main memory.
//              WriteClean does not transmit data on snoop bus, because line is unique
// Note: Barrier is just transfered to all Cache lines
// Note: CRRESP[1] (ECC Error) is ignored

// TODO: Add statistics

// N: Shows amount of caches. Not amount of cores
class CCXInterconnect(n: Int, StatisticsBaseAddr: BigInt = BigInt("FFFFFFFF", 16)) extends Module {
    val p = new AXIParams(64, 64, 1)
    val mparams = new AXIParams(64, 64, 1 + log2Ceil(n))
    val io = IO(new Bundle{
        val mbus = new AXIHostIF(mparams)
        val pbus = new AXIHostIF(mparams)

        val corebus = Vec(n, Flipped(new ACEHostIF(p)))
        // Even tho it's called corebus, it's actually connection to a cache of the core, which is double the amount of cores
    })

    // TODO: Keep statistics and return it when requested StatisticsBaseAddr

    val aw = VecInit(Seq.tabulate(n) (i => Queue(io.corebus(i).aw, 1)))
    val ar = VecInit(Seq.tabulate(n) (i => Queue(io.corebus(i).ar, 1)))
    val w = VecInit(Seq.tabulate(n) (i => Queue(io.corebus(i).w, 1)))
    val cr = VecInit(Seq.tabulate(n) (i => Queue(io.corebus(i).cr, 1)))
    val cd = VecInit(Seq.tabulate(n) (i => Queue(io.corebus(i).cd, 1)))
    

    val pbus = io.pbus
    val mbus = io.mbus

    
    
    val arb = Module(new RoundRobin(n))
    val warb = Module(new RoundRobin(n))

    warb.io.next := false.B
    arb.io.next := false.B

    for(i <- 0 until n) {
        arb.io.req(i)               := ar(i).valid
        warb.io.req(i)              := aw(i).valid

                aw(i).ready         := false.B
                ar(i).ready         := false.B
                w(i).ready          := false.B
                cr(i).ready         := false.B
                cd(i).ready         := false.B

        io.corebus(i).b.valid       := false.B
        io.corebus(i).b.bits        := 0.U.asTypeOf(io.corebus(i).b.bits)

        io.corebus(i).r.valid       := false.B
        io.corebus(i).r.bits        := 0.U.asTypeOf(io.corebus(i).r.bits)

        io.corebus(i).ac.valid      := false.B
        io.corebus(i).ac.bits       := 0.U.asTypeOf(io.corebus(i).ac.bits)
    }


    pbus.ar.bits            := 0.U.asTypeOf(pbus.ar.bits)
    pbus.ar.valid           := false.B
    pbus.r.ready            := false.B
    pbus.w.bits             := 0.U.asTypeOf(pbus.w.bits)
    pbus.w.valid            := false.B
    pbus.b.ready            := false.B

    mbus.ar.bits            := 0.U.asTypeOf(mbus.ar.bits)
    mbus.ar.valid           := false.B
    mbus.r.ready            := false.B
    mbus.w.bits             := 0.U.asTypeOf(mbus.w.bits)
    mbus.w.valid            := false.B
    mbus.b.ready            := false.B



    val write_current_active = RegInit(false.B)
    val write_current_active_num = RegInit(0.U(log2Ceil(n).W))
    val address_write_req = Reg(new ACEWriteAddress(p));
    val write_done = RegInit(false.B)

    pbus.aw.valid       := false.B
    // Just pass data to memory
    pbus.aw.bits.addr  := address_write_req.addr
    pbus.aw.bits.size  := address_write_req.size
    pbus.aw.bits.len   := address_write_req.len
    pbus.aw.bits.burst := address_write_req.burst
    pbus.aw.bits.id    := Cat(write_current_active_num, address_write_req.id)
    pbus.aw.bits.lock  := address_write_req.lock
    pbus.aw.bits.cache := address_write_req.cache
    // TODO: In future set or clear CACHE bits accrodingly to config
    pbus.aw.bits.prot  := address_write_req.prot
    pbus.aw.bits.qos   := address_write_req.qos
    

    mbus.aw.valid       := false.B
    // Just pass data to memory
    mbus.aw.bits.addr  := address_write_req.addr
    mbus.aw.bits.size  := address_write_req.size
    mbus.aw.bits.len   := address_write_req.len
    mbus.aw.bits.burst := address_write_req.burst
    mbus.aw.bits.id    := Cat(write_current_active_num, address_write_req.id)
    mbus.aw.bits.lock  := address_write_req.lock
    mbus.aw.bits.cache := address_write_req.cache
    // TODO: In future set or clear CACHE bits accrodingly to config
    mbus.aw.bits.prot  := address_write_req.prot
    mbus.aw.bits.qos   := address_write_req.qos
    
    for(i <- 0 until n) {
        when(!write_current_active) {
            when(warb.io.grant.asUInt().orR) { // If any transactions are active and granted
                write_current_active := true.B
                write_current_active_num := warb.io.choice
            }
            warb.io.next := true.B
            address_write_req := aw(i).bits
            write_done := false.B
        } .elsewhen (write_current_active) {
            when(write_current_active_num === i.U) {
                when(address_write_req.isWriteClean()) {
                    mbus.aw.valid := !write_done

                    when(mbus.aw.ready) {
                        write_done := true.B
                    }
                    when(write_done && io.corebus(i).wack) {
                        write_current_active := false.B
                    } .elsewhen(!write_done && io.corebus(i).wack) {
                        printf("!ERROR! Interconnect: Early wack")
                    }
                } .elsewhen (address_write_req.isWriteNoSnoop()) {
                    pbus.aw.valid := !write_done
                    when(pbus.aw.ready) {
                        write_done := true.B
                    }

                    when(write_done && io.corebus(i).wack) {
                        write_current_active := false.B
                    } .elsewhen(!write_done && io.corebus(i).wack) {
                        printf("!ERROR! Interconnect: Early wack")
                    }
                } .otherwise {
                    printf("!ERROR! Interconnect: Invalid AW command by core")
                }
            }
        } .otherwise {
            printf("!ERROR! Interconnect: Invalid state")
        }
    }



    /*
    val current_active = RegInit(false.B)
    val current_active_num = RegInit(0.U(log2Ceil(n).W))
    val current_active_bus = RegInit(false.B)
    val ADDRESS_READ = false.B
    val ADDRESS_WRITE = true.B

    
    val address_read_req = Reg(new ACEReadAddress(p));

    val polled = Reg(Vec(n, Bool()))


    // AC Prot is same for all instances and independed of input, only registered data
    ac(i).bits.prot :=
        Mux(current_active_bus === ADDRESS_WRITE,
            address_write_req.prot, 
            address_read_req.prot)*/
    // TODO: Write transactions are passed anyway without stalling
    // Note: For read transactions Cache has responsibility to respond to AC bus transaction no matter what AR transaction is active
    /*
    when(!current_active && arb.io.grant.asUInt().orR) {
        // No active bus
        arb.io.next := true.B
        when(aw(arb.io.choice).valid) {
            current_active_bus := ADDRESS_WRITE
            address_write_req := aw(arb.io.choice).bits
        } .elsewhen(ar(arb.io.choice).valid) {
            current_active_bus := ADDRESS_READ
            address_read_req := ar(arb.io.choice).bits
        } .otherwise {
            printf("!ERROR! Error: Neither write or read request")
        }
        current_active := true.B
        current_active_num := arb.io.choice
        polled.asUInt := 0.U
    } .otherwise {
        for(i <- 0 until n) {
            when (current_active_bus === ADDRESS_READ && address_read_req.isReadNoSnoop()) {
                // Just pass data to memory
                pbus.ar.addr  := address_read_req.addr
                pbus.ar.size  := address_read_req.size
                pbus.ar.len   := address_read_req.len
                pbus.ar.burst := address_read_req.burst
                pbus.ar.id    := address_read_req.id
                pbus.ar.lock  := address_read_req.lock
                pbus.ar.cache := address_read_req.cache
                // TODO: In future set or clear CACHE bits accrodingly to config
                pbus.ar.prot  := address_read_req.prot
                pbus.ar.qos   := address_read_req.qos
                // TODO: Set valid, wait for ready and RACK
            } .elsewhen (current_active_bus === ADDRESS_WRITE && address_write_req.isWriteNoSnoop()) {
                // Just pass data to memory
                pbus.aw.addr  := address_write_req.addr
                pbus.aw.size  := address_write_req.size
                pbus.aw.len   := address_write_req.len
                pbus.aw.burst := address_write_req.burst
                pbus.aw.id    := address_write_req.id
                pbus.aw.lock  := address_write_req.lock
                pbus.aw.cache := address_write_req.cache
                // TODO: In future set or clear CACHE bits accrodingly to config
                pbus.aw.prot  := address_write_req.prot
                pbus.aw.qos   := address_write_req.qos
                // TODO: Set valid, wait for ready and WACK
            } .elsewhen (current_active_bus === ADDRESS_READ && address_read_req.isReadUnique()){
                when(!polled(i)) {
                    if(current_active_num =/= i) {
                        io.corebus(i).io.ac.valid := true.B
                    }
                    when(ac(i).ready) {
                        polled(i) := true.B
                    }
                } .elsewhen (polled(i)) {
                    io.corebus(i).io.ac.valid := false.B
                }
                when(polled.asUInt().andR && !snoop_response_accepted(i)) {
                    // All transactions done
                    // Time to check answers
                    when(cr(i).valid) {
                        snoop_response_accepted(i) := true.B
                        snoop_response(i) := cr(i).bits
                    } .elsewhen (((snoop_response_accepted.asUInt()) || (1 << current_active_num)).andR){
                        // All responses accepted
                        // snoop_response(i).resp(0) // Data transfer
                        when(cd(i).valid) {
                            // Return to requester
                        }
                    }
                    
                }

                // TODO: Wait for RACK


                io.corebus(i).io.ac.bits.snoop := address_read_req.snoop
                // Cache will remove cache lines will return unique 

                // TODO: ac, Set valid, wait for ready
                // TODO: Wait for valid on cr/cd and issue ready when done
                // TODO: Set valid, wait for ready and WACK
            } .elsewhen (current_active_bus === ADDRESS_READ && address_read_req.isReadShared()) {
                // TODO:
            } .elsewhen (current_active_bus === ADDRESS_WRITE && address_write_req.isWriteClean()) {
                // TODO:
            } .otherwise {
                printf("!ERROR! Error: Incorrect read or write interconnect request")
            }
        }
    }*/
}