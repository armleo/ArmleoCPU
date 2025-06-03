package armleocpu

import chisel3._
import chisel3.util._


import chisel3.util._
import chisel3.experimental.dataview._
import coursier.util.Sync
import chisel3.util.experimental.loadMemoryFromFile
import chisel3.util.experimental.loadMemoryFromFileInline


// FETCH
class fetch_uop_t(val ccx: CCXParams) extends Bundle {
  val pc                  = UInt(ccx.avLen.W)
  val pc_plus_4           = UInt(ccx.avLen.W)
  val instr               = UInt(ccx.iLen.W)
  val ifetch_pagefault   = Bool()
  val ifetch_accessfault = Bool()
  
  // TODO: Add Instruction PTE storage for RVFI
}
class PipelineControlIO(ccx: CCXParams) extends Bundle {
    val kill              = Input(Bool())
    val jump              = Input(Bool())
    val flush             = Input(Bool())
    val busy              = Output(Bool())

    val newPc            = Input(UInt(ccx.apLen.W)) // It can be either physical or virtual address
}


class Fetch(ccx: CCXParams) extends CCXModule(ccx = ccx) {
  /**************************************************************************/
  /*  Interface                                                             */
  /**************************************************************************/
  val ibus              = IO(new dbus_t(ccx))

  val ctrl              = IO(new PipelineControlIO(ccx))
  // Pipeline command interface form control unit

  // Fetch to decode bus
  val uop_o         = IO(DecoupledIO(new fetch_uop_t(ccx)))


  // For reset vectors
  val dynRegs       = IO(Input(new DynamicROCsrRegisters(ccx)))

  // From CSR
  val csr          = IO(Input(new CsrRegsOutput(ccx)))

  /**************************************************************************/
  /*  Submodules                                                            */
  /**************************************************************************/

  /*
  val itlb      = Module(new AssociativeMemory(
    t = new tlb_entry_t(c, lvl = 2), sets = ccx.itlb.sets, ways = ccx.itlb.ways, flushLatency = ccx.itlb.flushLatency,
    verbose = ccx.itlb_verbose, instName = "itlb    ", c = c))
  */
  //val pagefault = Module(new Pagefault(c = c))
  
  
  val cache     = Module(new Cache    (ccx = ccx, cp = ccx.core.icache))

  /*
  val ptw       = Module(new PTW      (c = c, verbose = ccx.iptw_verbose, instName = "iptw    "))
  
  
  
  val refill    = Module(new Refill   (c = c, cp = ccx.icache, cache = cache))
  */


  // TODO: Add PTE storage for RVFI
  /**************************************************************************/
  /*  Combinational declarations                                            */
  /**************************************************************************/

  val pcNext                = Wire(UInt(ccx.apLen.W))
  val new_request_allowed   = Wire(Bool())
  val start_new_request     = Wire(Bool())
  

  /**************************************************************************/
  /*  State                                                                 */
  /**************************************************************************/
  
  // FIXME: Reset vector

  val pc                    = RegInit(dynRegs.resetVector)
  // Next pc should be PC register
  val pc_restart            = RegInit(true.B)
  
  val pc_plus_4             = RegInit(dynRegs.resetVector + 4.U)

  val hold_uop              = Reg(new fetch_uop_t(ccx))
  val busy_reg              = RegInit(false.B)
  val csrRegs               = Reg(new CsrRegsOutput(ccx))

  val IDLE          = 1.U(4.W)
  val HOLD          = 2.U(4.W)
  val ACTIVE        = 3.U(4.W)
  val FLUSH         = 4.U(4.W)

  val state         = RegInit(IDLE)

  //val ppn  = Reg(chiselTypeOf(itlb.io.s0.wentry.ppn))
    // TLB.s1 is only valid in output stage, but not in refill.
    // Q: Why?
    // A: Turns out not every memory cell supports keeping output after read
    //    Yep, that is literally why we are wasting preciouse chip area... Portability

  
  
  //val tlb_invalidate_counter = RegInit(0.U(log2Ceil(ccx.itlb.entries).W))
  //val cache_invalidate_counter = RegInit(0.U(log2Ceil(ccx.icache.entries).W))

  
  /**************************************************************************/
  /*  Combinational                                                         */
  /**************************************************************************/
  pcNext                   := pc
  val pcNextPlus4 = pcNext + 4.U

  
  //val (vm_enabled, vm_privilege) = output_stage_csr_regs.getVmSignals()

  /*
  /**************************************************************************/
  /*  Module connections                                                    */
  /**************************************************************************/
  cache.s0 <> refill.s0
  ibus <> refill.ibus
  ibus <> ptw.bus

  */

  ibus <> cache.corebus

  /*
  /**************************************************************************/
  /*  Module permanent assigments                                           */
  /**************************************************************************/
  ptw.vaddr                 := pc
  
  
  tlb.s0.write_data.meta    := ptw.meta
  tlb.s0.write_data.ptag    := ptw.physical_address_top
  

  pagefault.csrRegs        := csrRegs
  pagefault.tlbdata         := tlb.s1.read_data

  val saved_paddr = Mux(vm_enabled, 
    Cat(saved_tlb_ptag, pc(ccx.pgoff_len - 1, 0)), // Virtual addressing use tlb data
    Cat(pccx.asSInt.pad(ccx.apLen))
  )
  refill.vaddr := pc
  refill.paddr := saved_paddr
  
  // FIXME: Need correction, as it seems that virtual address is incorrect?

  /**************************************************************************/
  /*  Module default assigments                                             */
  /**************************************************************************/
  tlb.s0.cmd                := tlb_cmd.none
  tlb.s0.virt_address_top   := pcNext(ccx.avLen - 1, ccx.pgoff_len)

  pagefault.cmd             := pagefault_cmd.execute

  cache.s0.cmd              := cache_cmd.none
  cache.s0.vaddr            := pcNext
  
  refill.req := false.B

  val s1_paddr = Mux(vm_enabled, 
    Cat(tlb.s1.read_data.ptag, pc(ccx.pgoff_len - 1, 0)), // Virtual addressing use tlb data
    Cat(pccx.asSInt.pad(ccx.apLen))
  )

  cache.s1.paddr := s1_paddr
  */

  /**************************************************************************/
  /*  Internal Combinational                                                */
  /**************************************************************************/
  new_request_allowed       := false.B
  start_new_request         := false.B
  busy_reg                  := false.B
  //cmd_ready                 := false.B
  

  
  uop_o.valid                   := false.B
  uop_o.bits                    := hold_uop
  uop_o.bits.ifetch_accessfault := false.B
  uop_o.bits.ifetch_pagefault   := false.B
  
  //ptw.resolve_req               := false.B
  


  when(pc_restart) {
    pcNext := pc
  } .otherwise {
    pcNext := pc_plus_4
  }

  
/*
  when(state === FLUSH) {
    /**************************************************************************/
    /* Cache/TLB Flush                                                        */
    /**************************************************************************/
  

    cache.s0.cmd              := cache_cmd.invalidate
    cache.s0.vaddr            := cache_invalidate_counter << log2Ceil(ccx.icache.entry_bytes)
    val cache_invalidate_counter_ovfl = (((cache_invalidate_counter + 1.U) % ((ccx.icache.entries).U)) === 0.U)
    when(!cache_invalidate_counter_ovfl) {
      cache_invalidate_counter  := ((cache_invalidate_counter + 1.U) % ((ccx.icache.entries).U))
    }

    

    tlb.s0.cmd                := tlb_cmd.invalidate
    tlb.s0.virt_address_top   := tlb_invalidate_counter
    val tlb_invalidate_counter_ovfl = (((tlb_invalidate_counter + 1.U) % ccx.itlb.entries.U) === 0.U)
    when(!tlb_invalidate_counter_ovfl) {
      tlb_invalidate_counter    := (tlb_invalidate_counter + 1.U) % ccx.itlb.entries.U
    }

    when(tlb_invalidate_counter_ovfl && cache_invalidate_counter_ovfl) {
      state := IDLE
      new_request_allowed := true.B
      tlb_invalidate_counter := 0.U
      cache_invalidate_counter := 0.U

      log(cf"Flush done")
    }
  } .elsewhen(state === TLB_REFILL) {
    /**************************************************************************/
    /* TLB Refill logic                                                       */
    /**************************************************************************/
    
    ibus <> ptw.bus

    tlb.s0.virt_address_top     := pc(ccx.avLen - 1, ccx.pgoff_len)
    ptw.resolve_req             := true.B

    when(ptw.cplt) {
      tlb.s0.cmd                          := tlb_cmd.write
      hold_uop.ifetch_pagefault    := ptw.pagefault
      hold_uop.ifetch_accessfault  := ptw.accessfault
      when(ptw.accessfault || ptw.pagefault) {
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
  } .else*/
  when(state === HOLD) {
    /**************************************************************************/
    /* holding, because pipeline is not ready                                 */
    /**************************************************************************/
    uop_o.bits := hold_uop
    uop_o.valid := true.B
    when(uop_o.ready) {
      state := IDLE
      new_request_allowed := true.B
      log(cf"Instruction holding, accepted pc=0x${pc}%x")
    }
    busy_reg := true.B
  } .elsewhen (state === ACTIVE) {
    /**************************************************************************/
    /* Outputing/Comparing/checking access permissions                        */
    /**************************************************************************/
    uop_o.bits.pc := pc
    uop_o.bits.pc_plus_4 := pc_plus_4

    /**************************************************************************/
    /* Instruction output selection logic                                     */
    /**************************************************************************/

    /*
    if (ccx.busBytes == ccx.iLen / 8) {
      // If bus is as wide as the instruction then just output that
      uop.instr := cache.s1.response.bus_aligned_data.asUInt.asTypeOf(Vec(ccx.busBytes / (ccx.iLen / 8), UInt(ccx.iLen.W)))(0)
    } else {
      // Otherwise select the section of the bus that corresponds to the PC
      val vector_select = pc(log2Ceil(ccx.busBytes) - 1, log2Ceil(ccx.iLen / 8))
      uop.instr := cache.s1.response.bus_aligned_data.asUInt.asTypeOf(Vec(ccx.busBytes / (ccx.iLen / 8), UInt(ccx.iLen.W)))(vector_select)
    }
    */

    //val vector_select = pc(log2Ceil(ccx.busBytes) - 1, log2Ceil(ccx.iLen / 8))
    //uop_o.bits.instr := ibus.r.bits.data.asUInt.asTypeOf(Vec(ccx.busBytes / (ccx.iLen / 8), UInt(ccx.iLen.W)))(vector_select)

    
    
    
    //uop_o.bits.instr := memory_rdata
    uop_o.bits.instr := DontCare // TODO: Add instruction fetch from cache
    // Unconditionally leave output stage. If pipeline accepts the response
    // then new request will set this register below
    state := IDLE

    // TODO: Add pc checks for missalignment
    // TODO: RV64 Add pc checks for sign bit to be properly extended to xlen, otherwise throw exception
    // FIXME: Add PMA_PMP checking
    /*when(vm_enabled && tlb.s1.miss) {           // TLB Miss, go to refill
      /**************************************************************************/
      /* TLB Miss                                                               */
      /**************************************************************************/
      uop_valid             := false.B
      state                       := TLB_REFILL
      log(cf"tlb miss, pc=0x%x", pc)
    } .elsewhen(vm_enabled && pagefault.fault) { // Pagefault, output the error to the next stages
      /**************************************************************************/
      /* Pagefault                                                              */
      /**************************************************************************/
      uop_valid             := true.B
      uop.ifetch_pagefault := true.B

      log(cf"Pagefault, pc=0x%x", pc)
    } .elsewhen(cache.s1.response.miss) { // Cache Miss, go to refill
      /**************************************************************************/
      /* Cache Miss                                                             */
      /**************************************************************************/
      uop_valid                   := false.B
      state                       := CACHE_REFILL

      saved_tlb_ptag              := tlb.s1.read_data.ptag

      log(cf"Cache miss, pc=0x%x", pc)
    } .otherwise {
      */
      /**************************************************************************/
      /* TLB Hit, Cache hit                                                     */
      /**************************************************************************/

    uop_o.valid             := true.B
    log(cf"Outputing instruction, instr=0x${uop_o.bits.instr}%x, pc=0x${uop_o.bits.pc}%x")
    //}
    
    /**************************************************************************/
    /* HOLD write logic                                                       */
    /**************************************************************************/
    // Unconditionally remember the uop.
    // Only read if hold_uop_valid is set below
    hold_uop                := uop_o.bits

    when(uop_o.valid && uop_o.ready) { // Accepted start new fetch
      new_request_allowed         := true.B
      log(cf"Instruction accepted, pc=0x${pc}%x")
      state                       := IDLE
    } .elsewhen (uop_o.valid && !uop_o.ready) { // Not accepted, dont start new fetch. Hold the output value
      state                       := HOLD
      log(cf"Instruction holding, pc=0x${pc}%x")
    }

    busy_reg := true.B
  } .otherwise {
    /**************************************************************************/
    /* Idle state                                                             */
    /**************************************************************************/
    new_request_allowed := true.B
  }

  import ctrl._

  when(new_request_allowed) {
    /**************************************************************************/
    /* Command logic                                                          */
    /**************************************************************************/
    when(kill) {
      /**************************************************************************/
      /* Kill                                                                   */
      /**************************************************************************/
      busy_reg := false.B
      log(cf"Killing pc=0x${pc}%x")
    } .elsewhen (flush) {
      /**************************************************************************/
      /* Flush                                                                  */
      /**************************************************************************/
      state := FLUSH
      pc := newPc
      pc_plus_4 := newPc + 4.U
      pc_restart := true.B
      log(cf"Flushing newPc=0x${newPc}%x")
    } .elsewhen(jump) {
      // Note how pc_restart is not used here
      // It is because then the PC instruction would have been fetched and provided to pipeline twice
      // pcNext := newPc
      // start_new_request := true.B
      pc_restart := true.B
      pc := newPc
      pc_plus_4 := newPc + 4.U
      busy_reg := false.B
      log(cf"Accepted command (cmd === set_pc) from newPc=0x${newPc}%x")
      // TODO: Benchmark the synced next_pc vs not syncex next_pc
    } .otherwise {
      
      start_new_request := true.B
      log(cf"Starting fetch (cmd === none) from pcNext=0x${pcNext}%x")

      busy_reg := true.B
    }

    
  }
  
  cache.s0.valid := false.B
  cache.s0.bits.flush := false.B
  cache.s0.bits.read := true.B
  cache.s0.bits.write := false.B

  cache.s0.bits.vaddr := pcNext

  // Never written
  cache.s1.writeData := VecInit(Seq.fill(ccx.xLenBytes)(0.U(8.W)))
  cache.s1.writeMask := 0.U(ccx.xLenBytes.W)


  cache.corebus <> ibus
  cache.s0.bits.csrRegs := csrRegs

  cache.s1.kill := ctrl.kill
  cache.s1.read := false.B
  cache.s1.write := false.B
  cache.s1.flush := false.B // TODO: Add flush support
  
  when(start_new_request) {
    //cache.s0.cmd              := cache_cmd.request
    //tlb.s0.cmd                := tlb_cmd.resolve
    cache.s0.valid            := true.B
    cache.s0.bits.vaddr       := pcNext

    when(cache.s0.ready) {
      state                     := ACTIVE
      pc_restart                := false.B
      pc                        := pcNext
      pc_plus_4                 := pcNextPlus4
    } .otherwise {
      state                     := IDLE
      pc_restart                := true.B
      pc                        := pcNext
      pc_plus_4                 := pcNextPlus4
    }


    
  }
  
  busy := busy_reg
}

import _root_.circt.stage.ChiselStage
import chisel3.stage.ChiselGeneratorAnnotation

object FetchGenerator extends App {
  (new ChiselStage).execute(Array("--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new Fetch(new CCXParams()))))
}

