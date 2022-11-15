package armleocpu

import chisel3._
import chisel3.util._


import chisel3.experimental.ChiselEnum
import chisel3.experimental.dataview._

object fetch_cmd extends ChiselEnum {
    val none, kill, set_pc, flush = Value
}


  
// FETCH
class fetch_uop_t(val c: coreParams) extends Bundle {
  val pc                  = UInt(c.xLen.W)
  val instr               = UInt(c.iLen.W)
  val ifetch_page_fault   = Bool()
  val ifetch_access_fault = Bool()
}


class Fetch(val c: coreParams) extends Module {

    /**************************************************************************/
    /*  Interface                                                             */
    /**************************************************************************/
    val ibus              = IO(new ibus_t(c))
    // Pipeline command interface form control unit
    val cmd               = IO(Input(chiselTypeOf(fetch_cmd.none)))
    val new_pc            = IO(Input(UInt(c.apLen.W)))
    val cmd_ready         = IO(Output(Bool()))
    val busy              = IO(Output(Bool()))

    // Fetch to decode bus
    val fetch_uop         = IO(Output(new fetch_uop_t(c)))
    val fetch_uop_valid   = IO(Output(Bool()))
    val fetch_uop_accept  = IO(Input (Bool()))

    // From CSR
    val mem_priv          = IO(Input(new MemoryPrivilegeState(c)))

    /**************************************************************************/
    /*  Submodules                                                            */
    /**************************************************************************/

    val ptw = Module(new PTW(c))
    val cache = Module(new Cache(is_icache = true, c))
    val tlb = Module(new TLB(is_itlb = true, c))
    val pagefault = Module(new Pagefault(c))

    /**************************************************************************/
    /*  State                                                                 */
    /**************************************************************************/

    val pc                    = RegInit(c.reset_vector.U(c.apLen.W))
    val pc_plus_4             = pc + 4.U

    val hold_fetch_uop        = Reg(new fetch_uop_t(c))
    val busy_reg              = RegInit(false.B)
  
    val IDLE          = 0.U(4.W)
    val HOLD          = 1.U(4.W)
    val ACTIVE        = 2.U(4.W)
    val TLB_REFILL    = 3.U(4.W)
    val CACHE_REFILL  = 4.U(4.W)

    val state         = RegInit(IDLE)

    val cache_victim_way  = Reg(chiselTypeOf(cache.s0.write_way_idx_in))
    when(reset.asBool()) {
      cache_victim_way := 0.U
    }
    
    /**************************************************************************/
    /*  Combinational                                                         */
    /**************************************************************************/
    val new_request_allowed   = Wire(Bool())
    val start_new_request     = Wire(Bool())

    ptw.vaddr                 := pc
    ptw.mem_priv              := mem_priv
    ibus <> ptw.bus
    
    tlb.s0.cmd                := tlb_cmd.none
    tlb.s0.virt_address_top   := pc(c.avLen, 12)
    tlb.s0.write_data.meta    := ptw.meta
    tlb.s0.write_data.ptag    := ptw.physical_address_top

    pagefault.mem_priv        := mem_priv
    pagefault.tlbdata         := tlb.s1.read_data
    pagefault.cmd             := pagefault_cmd.execute

    cache.s0.cmd              := cache_cmd.none
    // TODO: Review, maybe this needs to be pc_next?
    cache.s0.vaddr            := pc

    cache.s0.write_way_idx_in := cache_victim_way
    cache.s0.write_paddr      := Cat(tlb.s1.read_data.ptag, pc(c.pgoff_len - 1, 0))
    cache.s0.write_bus_aligned_data := ibus.r.data.asTypeOf(chiselTypeOf(cache.s0.write_bus_aligned_data))
    println(cache.s0.write_bus_aligned_data)
    cache.s0.write_bus_mask   := VecInit(0.U(cache.s0.write_bus_mask.getWidth.W).asBools)
    // TODO: Write bus mask proper value, depending on counter
    //cache.s0.write_bus_mask   := write_bus_mask
    cache.s0.write_valid      := true.B

    // This needs proper testing
    cache.s1.paddr            := Cat(tlb.s1.read_data.ptag, pc(c.pgoff_len - 1, 0))


    /**************************************************************************/
    /*  Internal Combinational                                                */
    /**************************************************************************/
    new_request_allowed       := false.B
    start_new_request         := false.B
    busy                      := false.B
    cmd_ready                 := false.B
    
    fetch_uop_valid               := false.B
    fetch_uop                     := hold_fetch_uop
    fetch_uop.ifetch_access_fault := false.B
    fetch_uop.ifetch_page_fault   := false.B
    
    ptw.resolve_req               := false.B
    when(state === TLB_REFILL) {
      /**************************************************************************/
      /* TLB Refill logic                                                       */
      /**************************************************************************/
      
      tlb.s0.virt_address_top     := pc(c.avLen - 1, c.pgoff_len)
      ptw.resolve_req             := true.B

      when(ptw.cplt) {
        tlb.s0.cmd                          := tlb_cmd.write
        hold_fetch_uop.ifetch_page_fault    := ptw.page_fault
        hold_fetch_uop.ifetch_access_fault  := ptw.access_fault
        when(ptw.access_fault || ptw.page_fault) {
          state := HOLD
        }
      }

      busy_reg := true.B
    } .elsewhen(state === CACHE_REFILL) {
      /**************************************************************************/
      /* Cache refill logic                                                     */
      /**************************************************************************/
      ibus.ar.valid := true.B

      // TODO: icache refill
      // TODO: cache_refill_active := false.B
      busy_reg := true.B
      // TODO: If fails, then produce uop with error
    } .elsewhen(state === HOLD) { 
      /**************************************************************************/
      /* holding, because pipeline is not ready                                 */
      /**************************************************************************/
      fetch_uop := hold_fetch_uop
      fetch_uop_valid := true.B
      when(fetch_uop_accept) {
        state := IDLE
        new_request_allowed := true.B
      }
      busy_reg := true.B
    } .elsewhen (state === ACTIVE) {
      /**************************************************************************/
      /* Outputing/Comparing/checking access permissions                        */
      /**************************************************************************/
      fetch_uop.pc := pc

      /**************************************************************************/
      /* Instruction output selection logic                                     */
      /**************************************************************************/
      if (c.bus_data_bytes == c.iLen / 8) {
        // If bus is as wide as the instruction then just output that
        fetch_uop.instr := cache.s1.response.bus_aligned_data.asUInt.asTypeOf(Vec(c.bus_data_bytes / (c.iLen / 8), UInt(c.iLen.W)))(0)
      } else {
        // Otherwise select the section of the bus that corresponds to the PC
        val vector_select = pc(log2Ceil(c.bus_data_bytes) - 1, 2)
        fetch_uop.instr := cache.s1.response.bus_aligned_data.asUInt.asTypeOf(Vec(c.bus_data_bytes / (c.iLen / 8), UInt(c.iLen.W)))(vector_select)
      }
      

      // Unconditionally leave output stage. If pipeline accepts the response
      // then new request will set this register below
      state := IDLE

      // TODO: Add pc checks for missalignment
      // TODO: RV64 Add pc checks for sign bit to be properly extended to xlen, otherwise throw exception
      when(tlb.s1.miss) {           // TLB Miss, go to refill
        /**************************************************************************/
        /* TLB Miss                                                               */
        /**************************************************************************/
        fetch_uop_valid             := false.B
        state                       := TLB_REFILL
      } .elsewhen(pagefault.fault) { // Pagefault, output the error to the next stages
        /**************************************************************************/
        /* Pagefault                                                              */
        /**************************************************************************/
        fetch_uop_valid             := true.B
        fetch_uop.ifetch_page_fault := true.B
      } .elsewhen(cache.s1.response.miss) { // Cache Miss, go to refill
        /**************************************************************************/
        /* Cache Miss                                                             */
        /**************************************************************************/
        fetch_uop_valid             := false.B
        state                       := CACHE_REFILL
      } .otherwise {
        /**************************************************************************/
        /* TLB Hit, Cache hit                                                     */
        /**************************************************************************/
        fetch_uop_valid             := true.B
      }
      
      /**************************************************************************/
      /* HOLD write logic                                                       */
      /**************************************************************************/
      // Remeber the fetch_uop. Only read if hold_fetch_uop_valid is set
      hold_fetch_uop                := fetch_uop

      when(fetch_uop_valid && fetch_uop_accept) { // Accepted start new fetch
        new_request_allowed           := true.B
      } .elsewhen (fetch_uop_valid && !fetch_uop_accept) { // Not accepted, dont start new fetch. Hold the output value
        state                       := HOLD
      }

      busy := true.B
    } .otherwise {
      /**************************************************************************/
      /* Idle state                                                             */
      /**************************************************************************/
      new_request_allowed := true.B
    }

    when(new_request_allowed) {
      /**************************************************************************/
      /* Command logic                                                          */
      /**************************************************************************/
      when(cmd === fetch_cmd.kill) {
        /**************************************************************************/
        /* Kill                                                                   */
        /**************************************************************************/
        busy_reg := false.B
        cmd_ready := true.B
      } .elsewhen (cmd === fetch_cmd.flush) {
        /**************************************************************************/
        /* Flush                                                                  */
        /**************************************************************************/
        busy_reg := false.B
        cmd_ready := true.B
        // TODO: Flush the cache and TLB
      } .elsewhen(cmd === fetch_cmd.set_pc) {
        busy_reg := false.B
        cmd_ready := true.B
        pc := new_pc
        
        start_new_request := true.B
        state := ACTIVE

        // TODO: start a fetch request
      } .elsewhen(cmd === fetch_cmd.none) {
        start_new_request := true.B
        // TODO: start a fetch request
        // (pc + 4)
        // output_stage_active := true.B

        pc := pc + 4.U
        busy_reg := true.B
      }

      
    }
    
    busy := busy_reg
}


import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

object FetchGenerator extends App {
  (new ChiselStage).execute(Array("--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new Fetch(new coreParams(bus_data_bytes = 16)))))
}


