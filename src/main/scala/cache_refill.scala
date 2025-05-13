package armleocpu

import chisel3._
import chisel3.util._


import chisel3.util._
import chisel3.experimental.dataview._


class Refill(val c: CoreParams = new CoreParams, cp: CacheParams = new CacheParams, cache: Cache) extends Module {
  /**************************************************************************/
  /*  Constants                                                             */
  /**************************************************************************/

  // How many beats is needed to write to cache
  val burst_len             = (cp.entry_bytes / c.bp.data_bytes)

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


  /**************************************************************************/
  /*  State                                                                 */
  /**************************************************************************/
  val cache_victim_way  = Reg(chiselTypeOf(s0.writepayload.way_idx_in))
  
  val ar_done         = RegInit(false.B)
  val burst_counter   = new Counter(burst_len)
  val burst_counter_val = burst_counter.value
  dontTouch(burst_counter_val)
  val any_errors      = RegInit(false.B)

  when(reset.asBool) {
    cache_victim_way := 0.U
    burst_counter.reset()
  }


  /**************************************************************************/
  /*  Combinational                                                         */
  /**************************************************************************/

  // Contains the counter for refill.
  // If bus has same width as the entry then hardcode zero
  val cache_refill_counter =
        if(c.bp.data_bytes == cp.entry_bytes)
          Wire(0.U)
        else
          RegInit(0.U((cp.entry_bytes / c.bp.data_bytes).W))

  /**************************************************************************/
  /*  Cache writepayload                                                    */
  /**************************************************************************/
  s0.writepayload.paddr := paddr
  s0.writepayload.way_idx_in := cache_victim_way
  s0.writepayload.bus_mask   := VecInit(-1.S(cache.s0.writepayload.bus_mask.getWidth.W).asBools)
  s0.writepayload.bus_aligned_data := ibus.r.bits.data.asTypeOf(chiselTypeOf(cache.s0.writepayload.bus_aligned_data))
  s0.writepayload.valid      := true.B

  /**************************************************************************/
  /*  IBUS                                                                  */
  /**************************************************************************/   
  ibus.ar.bits.len    := (burst_len - 1).U
  ibus.ar.bits.size   := log2Ceil(c.bp.data_bytes).U
  ibus.ar.bits.lock   := false.B
  ibus.ar.valid  := false.B
  ibus.ar.bits.addr  := Cat(s0.writepayload.paddr(c.apLen - 1, log2Ceil(cp.entry_bytes)), burst_counter_val, 0.U(log2Ceil(c.bp.data_bytes).W)).asSInt
  ibus.r.ready   := false.B


  /**************************************************************************/
  /*  Cache S0                                                              */
  /**************************************************************************/
  s0.vaddr            := Cat(vaddr(c.avLen - 1, log2Ceil(cp.entry_bytes)), burst_counter_val, 0.U(log2Ceil(c.bp.data_bytes).W))
  s0.cmd              := cache_cmd.none

  /**************************************************************************/
  /*  Pipeline's output                                                     */
  /**************************************************************************/
  cplt := false.B
  err := false.B


  /**************************************************************************/
  /*  Primary logic                                                         */
  /**************************************************************************/
  when(req) {
    /**************************************************************************/
    /*  AR section                                                            */
    /**************************************************************************/
    ibus.ar.valid := !ar_done
    
    when(ibus.ar.ready) {
      ar_done := true.B
    }
    
    /**************************************************************************/
    /*  R section                                                             */
    /**************************************************************************/
    when(ibus.ar.ready || ar_done) {
      ibus.r.ready := true.B // Signal that we are ready to accept the RBus output

      when(ibus.r.valid) {
        s0.cmd              := cache_cmd.write
        any_errors := any_errors || (ibus.r.bits.resp =/= bus_resp_t.OKAY)
        burst_counter.inc()

        // TODO: This depends on the vaddr and counter of beats
        // Q: Why there is two separate ports?
        // A: Because paddr is used in cptag writing
        //    Meanwhile vaddr is used to calculate the entry_bus_num and entry_index
        //    we use vaddr so we dont have to mux the entry_bus_num and entry_index

        when(ibus.r.bits.last) {
          /**************************************************************************/
          /*  State reset or commitment                                             */
          /**************************************************************************/
          burst_counter.reset()
          any_errors := false.B
          ar_done := false.B
          
          // Count from zero to icache_ways
          cache_victim_way := (cache_victim_way + 1.U) % cp.ways.U
          printf("val = 0x%x, len=0x%x", burst_counter_val, burst_len.U)
          chisel3.assert(burst_counter_val === (burst_len.U - 1.U))



          /**************************************************************************/
          /*  Pipeline outputs                                                      */
          /**************************************************************************/
          cplt := true.B
          err := any_errors || (ibus.r.bits.resp =/= bus_resp_t.OKAY)
        }
      }
      
    }
  }
}