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



// N: Shows amount of caches. Not amount of cores
class CCXInterconnect(n: Int) extends Module {
    val io = IO(new Bundle{
        //val mbus = new AXIHostIF(new AXIParams(64, 64, 1))
        //val pbus = new AXIHostIF(new AXIParams(64, 64, 1))

        val corebus = Vec(n, Flipped(new ACEHostIF(new AXIParams(64, 64, 1))))
        // Even tho it's called corebus, it's actually connection to a cache of the core, which is double the amount of cores
    })
    val aw = Seq.tabulate(n) (i => Queue(io.corebus(i).aw, 1))
    val ar = Seq.tabulate(n) (i => Queue(io.corebus(i).ar, 1))
    val w = Seq.tabulate(n) (i => Queue(io.corebus(i).w, 1))
    val cr = Seq.tabulate(n) (i => Queue(io.corebus(i).cr, 1))
    val cd = Seq.tabulate(n) (i => Queue(io.corebus(i).cd, 1))
    
    
    //val arb = Module(new RoundRobin(n))

    // Initialization
    for(i <- 0 until n) {
        //arb(i).io.req := aw(i).valid || ar(i).valid
        
        
        

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
    

/*
    val rr = Module(new RoundRobin)
    val current_active = RegInit(false.B)
    val current_active_num = RegInit(0.U(log2Ceil(n).W))
    val current_active_bus = RegInit(false.B)
    val ADDRESS_READ = false.B
    val ADDRESS_WRITE = true.B

    rr.next := false.B

    for(i <- 0 until n) {
        rr.io.req(i) = corebus(i).ar.valid || corebus(i).aw.valid

        when(corebus(i).ar.valid && rr.io.grant(i) && !current_active) {
            rr.next := true.B
            current_active := true.B
            current_active_num := i.U
            current_active_bus := ADDRESS_READ
        }

    }
    */
}