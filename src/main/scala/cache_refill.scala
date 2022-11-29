package armleocpu

import chisel3._
import chisel3.util._


import chisel3.experimental.ChiselEnum
import chisel3.experimental.dataview._


class Refill(val c: coreParams, cache: Cache) extends Module {
  /**************************************************************************/
  /*  Constants                                                             */
  /**************************************************************************/

  // How many beats is needed to write to cache
  val burst_len             = (c.icache_entry_bytes / c.bus_data_bytes)

  /**************************************************************************/
  /*  Interface                                                             */
  /**************************************************************************/
  
  val req   = IO(Input(Bool()))
  val cplt  = IO(Output(Bool()))
  val err   = IO(Output(Bool()))

  val ibus  = IO(new ibus_t(c))

  val s0    = IO(Flipped(chiselTypeOf(cache.s0)))

  val vaddr = IO(chiselTypeOf(cache.s0.vaddr))
  val paddr = IO(chiselTypeOf(cache.s0.writepayload.paddr))

  val cache_victim_way  = Reg(chiselTypeOf(s0.writepayload.way_idx_in))
  when(reset.asBool()) {
    cache_victim_way := 0.U
  }
  s0.writepayload.paddr := paddr
  s0.writepayload.way_idx_in := cache_victim_way
  s0.writepayload.bus_mask   := VecInit(-1.S(cache.s0.writepayload.bus_mask.getWidth.W).asBools)
  s0.writepayload.bus_aligned_data := ibus.r.data.asTypeOf(chiselTypeOf(cache.s0.writepayload.bus_aligned_data))
  s0.writepayload.valid      := true.B

  val ar_done         = Reg(Bool())
  val burst_counter   = new Counter(burst_len)
  val any_errors      = Reg(Bool())


  // Contains the counter for refill.
  // If bus has same width as the entry then hardcode zero
  val cache_refill_counter =
        if(c.bus_data_bytes == c.icache_entry_bytes)
          Wire(0.U)
        else
          RegInit(0.U(c.icache_entry_bytes / c.bus_data_bytes))
  
  ibus.ar.len    := (burst_len - 1).U
  ibus.ar.size   := log2Ceil(c.bus_data_bytes).U
  ibus.ar.lock   := false.B
  ibus.ar.valid  := false.B
  ibus.ar.addr  := Cat(s0.writepayload.paddr(c.apLen - 1, log2Ceil(c.icache_entry_bytes)), burst_counter.value, 0.U(log2Ceil(c.bus_data_bytes).W)).asSInt
  ibus.r.ready   := false.B

  s0.vaddr            := Cat(vaddr(c.avLen - 1, log2Ceil(c.icache_entry_bytes)), burst_counter.value, 0.U(log2Ceil(c.bus_data_bytes).W))
  
  
  s0.cmd              := cache_cmd.none

  cplt := false.B
  err := false.B

  when(req) {
    ibus.ar.valid := !ar_done
    
    when(ibus.ar.ready) {
      ar_done := true.B
    }
    
    when(ibus.ar.ready || ar_done) {
      when(ibus.r.valid) {
        s0.cmd              := cache_cmd.write

        any_errors := any_errors || (ibus.r.resp =/= bus_resp_t.OKAY)
        burst_counter.inc()

        // TODO: This depends on the vaddr and counter of beats
        // Q: Why there is two separate ports?
        // A: Because paddr is used in cptag writing
        //    Meanwhile vaddr is used to calculate the entry_bus_num and entry index

        when(ibus.r.last) {
          burst_counter.reset()
          any_errors := false.B
          ar_done := false.B
          
          // Count from zero to icache_ways
          cache_victim_way := (cache_victim_way + 1.U) % c.icache_ways.U
          cplt := true.B
          err := any_errors || (ibus.r.resp =/= bus_resp_t.OKAY)
          
        }
      }
      ibus.r.ready := true.B
    }
  }
}