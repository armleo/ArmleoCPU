package armleocpu

import CacheConsts._
import Consts._
import chisel3._
import chisel3.util._
/*

class RoundRobin(n: Int) extends Module {
    val io = IO(new Bundle{
        val req = Input(Vec(n, Bool()))
        val grant = Output(Vec(n, Bool()))
        val next = Input(Bool())
    })
    val rotate_ptr = RegInit(U0.U((log2Ceil(n)).W))

    val shift_req = Cat(io.req, io.req) >> rotate_ptr

    val shift_grant = Wire(UInt(n.W))

    for(i <- 0 until n) {
        shift_grant := 0.U
        shift_grant(i) := PriorityEncoder(shift_req) == i.U
    }

    val grant_comb = (Cat(shift_grant, shift_grant) << rotate_ptr)(n-1, 0)
    println(grant_comb)

    io.grant := grant_comb

    when(io.next) {
        rotate_ptr := PriorityEncoder(grant_comb) + 1
    }
}
*/


class CCXInterconnect(n: Int) extends Module {
    val io = IO(new Bundle{
        //val mbus = new AXIHostIF(new AXIParams(64, 64, 1))
        //val pbus = new AXIHostIF(new AXIParams(64, 64, 1))

        val corebus = Vec(n, Flipped(new ACEHostIF(new AXIParams(64, 64, 1))))
    })
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