package armleocpu

import chisel3._
import chisel3.util._


import chisel3.experimental.ChiselEnum
import chisel3.experimental.dataview._


// FETCH
class fetch_uop_t(val c: CoreParams) extends Bundle {
  val pc                  = UInt(c.archParams.avLen.W)
  val pc_plus_4           = UInt(c.archParams.avLen.W)
  val instr               = UInt(c.archParams.iLen.W)
  val ifetch_page_fault   = Bool()
  val ifetch_access_fault = Bool()
}


object fetch_cmd extends ChiselEnum {
    val none    = 0.U(2.W)
    val kill    = 1.U(2.W)
    val set_pc  = 2.U(2.W)
    val flush   = 3.U(2.W)
}
  

class Fetch(val c: CoreParams) extends Module {
    /**************************************************************************/
    /*  Interface                                                             */
    /**************************************************************************/
    val ibus              = IO(new ibus_t(c))
    // Pipeline command interface form control unit
    val cmd               = IO(Input(chiselTypeOf(fetch_cmd.none)))
    val new_pc            = IO(Input(UInt(c.archParams.avLen.W)))
    val cmd_ready         = IO(Output(Bool()))
    val busy              = IO(Output(Bool()))

    // Fetch to decode bus
    val uop         = IO(Output(new fetch_uop_t(c)))
    val uop_valid   = IO(Output(Bool()))
    val uop_accept  = IO(Input (Bool()))

    // From CSR
    val mem_priv          = IO(Input(new MemoryPrivilegeState(c)))



    /**************************************************************************/
    /*  Logging logic                                                         */
    /**************************************************************************/

    val cycle = IO(Input(UInt(c.lp.verboseCycleWidth.W)))
    val log = new Logger(c.lp.coreName, "fetch", c.fetch_verbose, cycle)

    /**************************************************************************/
    /*  Submodules                                                            */
    /**************************************************************************/

    val ptw = Module(new PTW(instName = "iptw ", c = c, tp = c.itlb))
    val tlb = Module(new TLB(verbose = c.itlb_verbose, instName = "itlb ", c = c, tp = c.itlb))
    val cache = Module(new Cache(verbose = c.icache_verbose, c = c, instName = "inst$", cp = c.icache))
    
    val pagefault = Module(new Pagefault(c = c))
    val refill = Module(new Refill(c = c, cp = c.icache, cache))

    // TODO: Add PTE storage for RVFI
  


    /**************************************************************************/
    /*  Combinational declarations                                            */
    /**************************************************************************/

    val pc_next               = Wire(UInt(c.archParams.avLen.W))
    val new_request_allowed   = Wire(Bool())
    val start_new_request     = Wire(Bool())
    

    /**************************************************************************/
    /*  State                                                                 */
    /**************************************************************************/

    val pc                    = RegInit(c.reset_vector.U(c.archParams.avLen.W))
    // Next pc should be PC register
    val pc_restart            = RegInit(true.B)
    
    val pc_plus_4             = pc + 4.U

    val hold_uop              = Reg(new fetch_uop_t(c))
    val busy_reg              = RegInit(false.B)
    val output_stage_mem_priv = Reg(new MemoryPrivilegeState(c))
  
    val FLUSH         = 0.U(4.W)
    val IDLE          = 1.U(4.W)
    val HOLD          = 2.U(4.W)
    val ACTIVE        = 3.U(4.W)
    val TLB_REFILL    = 4.U(4.W)
    val CACHE_REFILL  = 5.U(4.W)

    val state         = RegInit(FLUSH)

    val saved_tlb_ptag  = Reg(chiselTypeOf(tlb.s1.read_data.ptag))
      // TLB.s1 is only valid in output stage, but not in refill.
      // Q: Why?
      // A: Turns out not every memory cell supports keeping output after read
      //    Yep, that is literally why we are wasting preciouse chip area... Portability

    
    
    val tlb_invalidate_counter = RegInit(0.U(log2Ceil(c.itlb.entries).W))
    val cache_invalidate_counter = RegInit(0.U(log2Ceil(c.icache.entries).W))

    
    /**************************************************************************/
    /*  Combinational                                                         */
    /**************************************************************************/
    pc_next                   := pc
    
    val (vm_enabled, vm_privilege) = output_stage_mem_priv.getVmSignals()

    cache.s0 <> refill.s0
    ibus <> refill.ibus

    ptw.vaddr                 := pc
    ptw.mem_priv              := mem_priv
    ibus <> ptw.bus
    ptw.cycle                 := cycle
    
    tlb.s0.cmd                := tlb_cmd.none
    tlb.s0.virt_address_top   := pc_next(c.archParams.avLen - 1, 12)
    tlb.s0.write_data.meta    := ptw.meta
    tlb.s0.write_data.ptag    := ptw.physical_address_top
    tlb.cycle                 := cycle

    pagefault.mem_priv        := mem_priv
    pagefault.tlbdata         := tlb.s1.read_data
    pagefault.cmd             := pagefault_cmd.execute


    cache.s0.cmd              := cache_cmd.none
    cache.s0.vaddr            := pc_next
    
    refill.req := false.B
    refill.vaddr := pc
    refill.paddr := Mux(vm_enabled, 
      Cat(saved_tlb_ptag, pc(c.archParams.avLen - 1, c.archParams.pgoff_len), pc(c.archParams.pgoff_len - 1, 0)), // Virtual addressing use tlb data
      Cat((VecInit.tabulate(c.archParams.apLen - c.archParams.avLen) {n => pc(c.archParams.avLen - 1)}).asUInt, pc.asSInt)
    )
    //refill.ibus := ibus // So no void erros will be issued
    
    
    // TODO: Write bus mask proper value, depending on counter
    //cache.s0.writepayload.bus_mask   := writepayload.bus_mask
    

    when(vm_enabled) {
      cache.s1.paddr          := Cat(tlb.s1.read_data.ptag, pc(c.archParams.avLen - 1, c.archParams.pgoff_len)) // Virtual addressing use tlb data
    } .otherwise {
      cache.s1.paddr          := Cat((VecInit.tabulate(c.archParams.apLen - c.archParams.avLen) {n => pc(c.archParams.avLen - 1)}).asUInt, pc.asSInt)
    }
    cache.cycle               := cycle

    /**************************************************************************/
    /*  Internal Combinational                                                */
    /**************************************************************************/
    new_request_allowed       := false.B
    start_new_request         := false.B
    busy_reg                  := false.B
    cmd_ready                 := false.B
    

    
    uop_valid               := false.B
    uop                     := hold_uop
    uop.ifetch_access_fault := false.B
    uop.ifetch_page_fault   := false.B
    
    ptw.resolve_req               := false.B
    


    when(pc_restart) {
      pc_next := pc
    } .otherwise {
      pc_next := pc_plus_4
    }

    

    when(state === FLUSH) {
      /**************************************************************************/
      /* Cache/TLB Flush                                                        */
      /**************************************************************************/

      cache.s0.cmd              := cache_cmd.invalidate
      cache.s0.vaddr            := cache_invalidate_counter << log2Ceil(c.icache.entry_bytes)
      val cache_invalidate_counter_ovfl = (((cache_invalidate_counter + 1.U) % ((c.icache.entries).U)) === 0.U)
      when(!cache_invalidate_counter_ovfl) {
        cache_invalidate_counter  := ((cache_invalidate_counter + 1.U) % ((c.icache.entries).U))
      }

      

      tlb.s0.cmd                := tlb_cmd.invalidate
      tlb.s0.virt_address_top   := tlb_invalidate_counter
      val tlb_invalidate_counter_ovfl = (((tlb_invalidate_counter + 1.U) % c.itlb.entries.U) === 0.U)
      when(!tlb_invalidate_counter_ovfl) {
        tlb_invalidate_counter    := (tlb_invalidate_counter + 1.U) % c.itlb.entries.U
      }

      when(tlb_invalidate_counter_ovfl && cache_invalidate_counter_ovfl) {
        state := IDLE
        new_request_allowed := true.B
        tlb_invalidate_counter := 0.U
        cache_invalidate_counter := 0.U

        log("Flush done")
      }
    } .elsewhen(state === TLB_REFILL) {
      /**************************************************************************/
      /* TLB Refill logic                                                       */
      /**************************************************************************/
      
      ibus <> ptw.bus

      tlb.s0.virt_address_top     := pc(c.archParams.avLen - 1, c.archParams.pgoff_len)
      ptw.resolve_req             := true.B

      when(ptw.cplt) {
        tlb.s0.cmd                          := tlb_cmd.write
        hold_uop.ifetch_page_fault    := ptw.page_fault
        hold_uop.ifetch_access_fault  := ptw.access_fault
        when(ptw.access_fault || ptw.page_fault) {
          state := HOLD
        } .otherwise {
          state := IDLE
          pc_restart := true.B
        }
      }

      busy_reg := true.B
    } .elsewhen(state === CACHE_REFILL) {
      /**************************************************************************/
      /* Cache refill logic                                                     */
      /**************************************************************************/

      refill.req := true.B
      ibus <> refill.ibus
      cache.s0 <> refill.s0

      when(refill.cplt) {
        state := IDLE
        pc_restart := true.B
        when(refill.err) {
          // TODO: Produce Uop with error
        }
      }

      busy_reg := true.B
      // TODO: If fails, then produce uop with error
    } .elsewhen(state === HOLD) {
      /**************************************************************************/
      /* holding, because pipeline is not ready                                 */
      /**************************************************************************/
      uop := hold_uop
      uop_valid := true.B
      when(uop_accept) {
        state := IDLE
        new_request_allowed := true.B
        log("Instruction holding, accepted pc=0x%x", pc)
      }
      busy_reg := true.B
    } .elsewhen (state === ACTIVE) {
      /**************************************************************************/
      /* Outputing/Comparing/checking access permissions                        */
      /**************************************************************************/
      uop.pc := pc
      uop.pc_plus_4 := pc_plus_4

      /**************************************************************************/
      /* Instruction output selection logic                                     */
      /**************************************************************************/
      if (c.bp.data_bytes == c.archParams.iLen / 8) {
        // If bus is as wide as the instruction then just output that
        uop.instr := cache.s1.response.bus_aligned_data.asUInt.asTypeOf(Vec(c.bp.data_bytes / (c.archParams.iLen / 8), UInt(c.archParams.iLen.W)))(0)
      } else {
        // Otherwise select the section of the bus that corresponds to the PC
        val vector_select = pc(log2Ceil(c.bp.data_bytes) - 1, log2Ceil(c.archParams.iLen / 8))
        uop.instr := cache.s1.response.bus_aligned_data.asUInt.asTypeOf(Vec(c.bp.data_bytes / (c.archParams.iLen / 8), UInt(c.archParams.iLen.W)))(vector_select)
      }
      

      // Unconditionally leave output stage. If pipeline accepts the response
      // then new request will set this register below
      state := IDLE

      // TODO: Add pc checks for missalignment
      // TODO: RV64 Add pc checks for sign bit to be properly extended to xlen, otherwise throw exception
      // FIXME: Add PMA_PMP checking
      when(vm_enabled && tlb.s1.miss) {           // TLB Miss, go to refill
        /**************************************************************************/
        /* TLB Miss                                                               */
        /**************************************************************************/
        uop_valid             := false.B
        state                       := TLB_REFILL
        log("tlb miss, pc=0x%x", pc)
      } .elsewhen(vm_enabled && pagefault.fault) { // Pagefault, output the error to the next stages
        /**************************************************************************/
        /* Pagefault                                                              */
        /**************************************************************************/
        uop_valid             := true.B
        uop.ifetch_page_fault := true.B

        log("Pagefault, pc=0x%x", pc)
      } .elsewhen(cache.s1.response.miss) { // Cache Miss, go to refill
        /**************************************************************************/
        /* Cache Miss                                                             */
        /**************************************************************************/
        uop_valid                   := false.B
        state                       := CACHE_REFILL

        saved_tlb_ptag              := tlb.s1.read_data.ptag

        log("Cache miss, pc=0x%x", pc)
      } .otherwise {
        /**************************************************************************/
        /* TLB Hit, Cache hit                                                     */
        /**************************************************************************/
        uop_valid             := true.B
        log("TLB/Cache hit, instr=0x%x, pc=0x%x", uop.instr, uop.pc)
      }
      
      /**************************************************************************/
      /* HOLD write logic                                                       */
      /**************************************************************************/
      // Unconditionally remember the uop.
      // Only read if hold_uop_valid is set below
      hold_uop                := uop

      when(uop_valid && uop_accept) { // Accepted start new fetch
        new_request_allowed         := true.B
        log("Instruction accepted, pc=0x%x", pc)
        state                       := IDLE
      } .elsewhen (uop_valid && !uop_accept) { // Not accepted, dont start new fetch. Hold the output value
        state                       := HOLD
        log("Instruction holding, pc=0x%x", pc)
      }

      busy_reg := true.B
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
        log("Killing pc=0x%x", pc)
      } .elsewhen (cmd === fetch_cmd.flush) {
        /**************************************************************************/
        /* Flush                                                                  */
        /**************************************************************************/
        state := FLUSH
        pc := new_pc
        pc_restart := true.B
        log("Flushing new_pc=0x%x", new_pc)
      } .elsewhen(cmd === fetch_cmd.set_pc) {
        // Note how pc_restart is not used here
        // It is because then the PC instruction would have been fetched and provided to pipeline twice
        // pc_next := new_pc
        // start_new_request := true.B
        pc_restart := true.B
        pc := new_pc
        busy_reg := false.B
        cmd_ready := true.B
        log("Accepted command (cmd === set_pc) from new_pc=0x%x", new_pc)
        // TODO: Benchmark the synced next_pc vs not syncex next_pc
      } .elsewhen(cmd === fetch_cmd.none) {
        
        start_new_request := true.B
        log("Starting fetch (cmd === none) from pc_next=0x%x", pc_next)

        busy_reg := true.B
      }

      
    }
    
    when(start_new_request) {
      cache.s0.cmd              := cache_cmd.request
      tlb.s0.cmd                := tlb_cmd.resolve
      output_stage_mem_priv     := mem_priv
      state                     := ACTIVE

      // Reset these state variables here
      // This reduces the reset fanout
      pc_restart                := false.B
      pc                        := pc_next
    }
    
    busy := busy_reg
}


import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

object FetchGenerator extends App {
  (new ChiselStage).execute(Array("--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new Fetch(new CoreParams(bp = new BusParams(data_bytes = 4))))))
}


