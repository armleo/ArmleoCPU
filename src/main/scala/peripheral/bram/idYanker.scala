package armleocpu.peripheral.bram

import chisel3._
import chisel3.util._
import armleocpu._

class IdYanker(depth: Int)(implicit outerBp: BusParams, innerBp: BRAMBusParams) extends Module {
  require(depth > 0)
  require(innerBp.idWidth == 0)
  require(outerBp.addrWidth == innerBp.addrWidth)
  require(outerBp.busBytes == innerBp.busBytes)
  require(outerBp.lenWidth == innerBp.lenWidth)

  val io = IO(new IdYankerIO(depth, outerBp.idWidth))

  val readIds = Module(new Queue(UInt(outerBp.idWidth.W), depth, pipe = true, flow = true))
  val writeIds = Module(new Queue(UInt(outerBp.idWidth.W), depth, pipe = true, flow = true))

  io.out.ar.valid := io.in.ar.valid && readIds.io.enq.ready
  io.out.ar.bits := 0.U.asTypeOf(io.out.ar.bits)
  io.out.ar.bits.op := io.in.ar.bits.op
  io.out.ar.bits.addr := io.in.ar.bits.addr
  io.out.ar.bits.len := io.in.ar.bits.len
  io.in.ar.ready := io.out.ar.ready && readIds.io.enq.ready

  readIds.io.enq.valid := io.in.ar.fire
  readIds.io.enq.bits := io.in.ar.bits.id
  readIds.io.deq.ready := io.in.r.fire && io.in.r.bits.last

  io.out.aw.valid := io.in.aw.valid && writeIds.io.enq.ready
  io.out.aw.bits := 0.U.asTypeOf(io.out.aw.bits)
  io.out.aw.bits.op := io.in.aw.bits.op
  io.out.aw.bits.addr := io.in.aw.bits.addr
  io.out.aw.bits.len := io.in.aw.bits.len
  io.in.aw.ready := io.out.aw.ready && writeIds.io.enq.ready

  writeIds.io.enq.valid := io.in.aw.fire
  writeIds.io.enq.bits := io.in.aw.bits.id
  writeIds.io.deq.ready := io.in.b.fire

  io.out.w <> io.in.w

  io.in.r.valid := io.out.r.valid && readIds.io.deq.valid
  io.in.r.bits := io.out.r.bits
  io.in.r.bits.id := readIds.io.deq.bits
  io.out.r.ready := io.in.r.ready && readIds.io.deq.valid

  io.in.b.valid := io.out.b.valid && writeIds.io.deq.valid
  io.in.b.bits := io.out.b.bits
  io.in.b.bits.id := writeIds.io.deq.bits
  io.out.b.ready := io.in.b.ready && writeIds.io.deq.valid
}
