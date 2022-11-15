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

    // -------------------------------------------------------------------------
    //  Interface
    // -------------------------------------------------------------------------
    val ibus              = IO(new ibus_t(c))
    // Pipeline command interface form control unit
    val cmd               = IO(Input(chiselTypeOf(fetch_cmd.none)))
    val new_pc            = IO(Input(c.reset_vector.U(c.apLen.W)))
    val cmd_ready         = IO(Output(Bool()))
    val busy              = IO(Output(Bool()))

    // Fetch to decode bus
    val fetch_uop         = IO(Output(new fetch_uop_t(c)))
    val fetch_uop_valid   = IO(Output(Bool()))
    val fetch_uop_accept  = IO(Input (Bool()))

    // From CSR
    val mem_priv          = IO(Input(new MemoryPrivilegeState(c)))

    // -------------------------------------------------------------------------
    //  Submodules
    // -------------------------------------------------------------------------

    val ptw = new PTW(c)
    val cache = new Cache(is_icache = true, c)
    val tlb = new TLB(is_itlb = true, c)
    val pagefault = new Pagefault(c)

    // -------------------------------------------------------------------------
    //  State
    // -------------------------------------------------------------------------

    val pc                    = RegInit(c.reset_vector.U(c.apLen.W))
    val pc_plus_4             = pc + 4.U

    val hold_fetch_uop        = Reg(new fetch_uop_t(c))
    val hold_fetch_uop_valid  = RegInit(false.B)

    val output_stage_active   = RegInit(false.B)
    val output_stage_pc       = Reg(UInt(c.xLen.W))
    val cache_refill_active   = RegInit(false.B)
    val tlb_refill_active     = RegInit(false.B)
    
    // -------------------------------------------------------------------------
    //  Combinational
    // -------------------------------------------------------------------------
    val start_new_request   = Wire(Bool())

    ptw.vaddr               := output_stage_pc
    ptw.mem_priv            := mem_priv

    tlb.s0.virt_address     := pc
    tlb.s0.write_data.meta  := ptw.meta
    tlb.s0.write_data.ptag  := ptw.physical_address_top

    pagefault.mem_priv      := mem_priv
    pagefault.tlbdata       := tlb.s1.read_data.meta
    pagefault.cmd           := pagefault_cmd.execute

    // TODO: Cache connections
    cache.s1.paddr := Cat(tlb.s1.read_data.ptag)


    start_new_request       := false.B
    busy                    := false.B

    fetch_uop.ifetch_access_fault := false.B
    fetch_uop.ifetch_page_fault   := false.B

    when(hold_fetch_uop_valid) { 
      /**************************************************************************/
      /* holding, because pipeline is not ready                                 */
      /**************************************************************************/
      fetch_uop := hold_fetch_uop
      fetch_uop_valid := true.B
      when(fetch_uop_accept) {
        hold_fetch_uop_valid := false.B
        start_new_request := true.B
      }
      busy := true.B
    } .elsewhen (output_stage_active) {
      /**************************************************************************/
      /* Outputing/Comparing/checking access permissions                        */
      /**************************************************************************/
      fetch_uop.pc := output_stage_pc

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
      output_stage_active         := false.B

      // TODO: Add pc checks for missalignment
      // TODO: RV64 Add pc checks for sign bit to be properly extended to xlen, otherwise throw exception
      when(tlb.s1.miss) {           // TLB Miss, go to refill
        fetch_uop_valid             := false.B
        tlb_refill_active           := true.B
      } .elsewhen(pagefault.fault) { // Pagefault, output the error to the next stages
        fetch_uop_valid             := true.B
        fetch_uop.ifetch_page_fault := true.B
      } .elsewhen(cache.s1.response.miss) { // Cache Miss, go to refill
        fetch_uop_valid             := false.B
        cache_refill_active         := true.B
      }
      
      // Remeber the fetch_uop. Only read if hold_fetch_uop_valid is set
      hold_fetch_uop              := fetch_uop

      when(fetch_uop_accept) { // Accepted start new fetch
        start_new_request           := true.B
      } .otherwise { // Not accepted, dont start new fetch. Hold the output value
        hold_fetch_uop_valid        := true.B
      }

      busy := true.B
    } .elsewhen(tlb_refill_active) {
      ibus <> ptw.bus
      
      tlb.s0.virt_address     := output_stage_pc

      when(ptw.cplt) {
        tlb.s0.cmd                          := tlb_cmd.write
        hold_fetch_uop.ifetch_page_fault    := ptw.page_fault
        hold_fetch_uop.ifetch_access_fault  := ptw.access_fault
        hold_fetch_uop_valid                := ptw.access_fault || ptw.page_fault

        tlb_refill_active := false.B
      }

      busy := true.B
    } .elsewhen(cache_refill_active) {
      // TODO: icache refill
      // TODO: cache_refill_active := false.B
      busy := true.B
      // TODO: If fails, then produce uop with error
    } .otherwise {
      start_new_request := true.B
    }

    when(start_new_request) {
      when(cmd === fetch_cmd.kill) {
        busy := false.B
        cmd_ready := true.B
      } .elsewhen (cmd === fetch_cmd.flush) {
        busy := false.B
        cmd_ready := true.B
        // TODO: Flush the cache and TLB
      } .elsewhen(cmd === fetch_cmd.set_pc) {
        busy := false.B
        cmd_ready := true.B
        pc := new_pc
        // TODO: start a fetch request
        // output_stage_active := true.B
      } .elsewhen(cmd === fetch_cmd.none) {
        // TODO: start a fetch request
        // (pc + 4)
        // output_stage_active := true.B
      }

      output_stage_pc := pc + 4.U
    }
    
    
}