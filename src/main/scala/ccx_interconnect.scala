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
// Note: ReadShared is used by Cache to reserve line for load operations
//              If ReadShared is received then possibly dirty line is passed to requester
//              Each Cache can respond with Cache line in any state
//              Providing cache must invalidate it's line if it's returning unique line
// Note: WriteClean is used to perform write of dirty line to Main memory.
//              WriteClean does not transmit data on snoop bus, because line is unique
// TODO: Add Barrier support
// TODO: Add statistics

// N: Shows amount of caches. Not amount of cores
class CCXInterconnect(n: Int) extends Module {
    val p = new AXIParams(64, 64, 1)
    val mparams = new AXIParams(64, 64, 1)
    val io = IO(new Bundle{
        val mbus = new AXIHostIF(mparams)
        val pbus = new AXIHostIF(mparams)

        val corebus = Vec(n, Flipped(new ACEHostIF(p)))
        // Even tho it's called corebus, it's actually connection to a cache of the core, which is double the amount of cores
    })
    val aw = VecInit(Seq.tabulate(n) (i => Queue(io.corebus(i).aw, 1)))
    val ar = VecInit(Seq.tabulate(n) (i => Queue(io.corebus(i).ar, 1)))
    val w = VecInit(Seq.tabulate(n) (i => Queue(io.corebus(i).w, 1)))
    val cr = VecInit(Seq.tabulate(n) (i => Queue(io.corebus(i).cr, 1)))
    val cd = VecInit(Seq.tabulate(n) (i => Queue(io.corebus(i).cd, 1)))
    
    
    val arb = Module(new RoundRobin(n))

    arb.io.next := false.B

    for(i <- 0 until n) {
        arb.io.req(i) := aw(i).valid || ar(i).valid
        
        
        

                aw(i).ready         := false.B
                ar(i).ready         := false.B
                w(i).ready          := false.B
                cr(i).ready         := false.B
                cd(i).ready         := false.B

        io.corebus(i).b.valid       := false.B
        io.corebus(i).b.bits.id     := 0.U
        io.corebus(i).b.bits.resp   := 0.U

        io.corebus(i).r.valid       := false.B
        io.corebus(i).r.bits.data   := 0.U
        io.corebus(i).r.bits.id     := 0.U
        io.corebus(i).r.bits.last   := 0.U
        io.corebus(i).r.bits.resp   := 0.U

        io.corebus(i).ac.valid      := false.B
        io.corebus(i).ac.bits.addr  := 0.U
        io.corebus(i).ac.bits.snoop := 0.U
        io.corebus(i).ac.bits.prot  := 0.U
    }

    val current_active = RegInit(false.B)
    val current_active_num = RegInit(0.U(log2Ceil(n).W))
    val current_active_bus = RegInit(false.B)
    val ADDRESS_READ = false.B
    val ADDRESS_WRITE = true.B

    val address_write_req = Reg(new ACEWriteAddress(p));
    val address_read_req = Reg(new ACEReadAddress(p));

    val polled = Reg(Vec(n, Bool()))


    // AC Prot is same for all instances and independed of input, only registered data
    ac(i).bits.prot :=
        Mux(current_active_bus === ADDRESS_WRITE,
            address_write_req.prot, 
            address_read_req.prot)
                    
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
                when(polled.asUInt().andR) {
                    // All transactions done
                    // Time to check answers
                    cr(i).valid
                    
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
    }
}