package armleocpu

import chisel3._
import chisel3.util._


import chisel3.experimental.ChiselEnum
import chisel3.experimental.dataview._

object fetch_cmd extends ChiselEnum {
    val none    = 0.U(2.W)
    val kill    = 1.U(2.W)
    val set_pc  = 2.U(2.W)
    val flush   = 3.U(2.W)
}
  
// FETCH
class fetch_uop_t(val c: coreParams) extends Bundle {
  val pc                  = UInt(c.avLen.W)
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
    val new_pc            = IO(Input(UInt(c.avLen.W)))
    val cmd_ready         = IO(Output(Bool()))
    val busy              = IO(Output(Bool()))

    // Fetch to decode bus
    val uop         = IO(Output(new fetch_uop_t(c)))
    val uop_valid   = IO(Output(Bool()))
    val uop_accept  = IO(Input (Bool()))

    // From CSR
    val mem_priv          = IO(Input(new MemoryPrivilegeState(c)))

    /**************************************************************************/
    /*  Submodules                                                            */
    /**************************************************************************/

    val ptw = Module(new PTW(c))
    val cache = Module(new Cache(is_icache = true, c))
    val tlb = Module(new TLB(is_itlb = true, c))
    val pagefault = Module(new Pagefault(c))

    // TODO: Add PTE storage for RVFI
  

    /**************************************************************************/
    /*  Constants                                                             */
    /**************************************************************************/

    // How many beats is needed to write to cache
    val burst_len             = (c.icache_entry_bytes / c.bus_data_bytes)


    /**************************************************************************/
    /*  Combinational declarations                                            */
    /**************************************************************************/

    val pc_next               = Wire(UInt(c.avLen.W))
    val new_request_allowed   = Wire(Bool())
    val start_new_request     = Wire(Bool())
    

    /**************************************************************************/
    /*  State                                                                 */
    /**************************************************************************/

    val pc                    = RegInit(c.reset_vector.U(c.avLen.W))
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

    val ar_done         = Reg(Bool())
    val burst_counter   = new Counter(burst_len)
    

    val cache_victim_way  = Reg(chiselTypeOf(cache.s0.write_way_idx_in))
    when(reset.asBool()) {
      cache_victim_way := 0.U
    }

    val tlb_invalidate_counter = RegInit(0.U(log2Ceil(c.itlb_entries).W))
    val cache_invalidate_counter = RegInit(0.U(log2Ceil(c.icache_entries * c.icache_entry_bytes / c.bus_data_bytes).W))
    println(c.icache_entries * c.icache_entry_bytes / c.bus_data_bytes)

    // Contains the counter for refill.
    // If bus has same width as the entry then hardcode zero
    val cache_refill_counter =
          if(c.bus_data_bytes == c.icache_entry_bytes)
            Wire(0.U)
          else
            RegInit(0.U(c.icache_entry_bytes / c.bus_data_bytes))
    
    /**************************************************************************/
    /*  Combinational                                                         */
    /**************************************************************************/
    pc_next                   := pc
    
    val vm_privilege = Mux(((output_stage_mem_priv.privilege === privilege_t.M) && output_stage_mem_priv.mprv), output_stage_mem_priv.mpp,  output_stage_mem_priv.privilege)
    val vm_enabled = ((vm_privilege === privilege_t.S) || (vm_privilege === privilege_t.USER)) && output_stage_mem_priv.mode.orR

    ptw.vaddr                 := pc
    ptw.mem_priv              := mem_priv
    ibus <> ptw.bus
    
    tlb.s0.cmd                := tlb_cmd.none
    tlb.s0.virt_address_top   := pc_next(c.avLen - 1, 12)
    tlb.s0.write_data.meta    := ptw.meta
    tlb.s0.write_data.ptag    := ptw.physical_address_top

    pagefault.mem_priv        := mem_priv
    pagefault.tlbdata         := tlb.s1.read_data
    pagefault.cmd             := pagefault_cmd.execute

    cache.s0.cmd              := cache_cmd.none
    cache.s0.vaddr            := pc_next

    cache.s0.write_way_idx_in := cache_victim_way
    when(vm_enabled) {
      cache.s0.write_paddr          := Cat(saved_tlb_ptag, pc(c.avLen - 1, c.pgoff_len), pc(c.pgoff_len - 1, 0)) // Virtual addressing use tlb data
    } .otherwise {
      cache.s0.write_paddr          := Cat((VecInit.tabulate(c.apLen - c.avLen) {n => pc(c.avLen - 1)}).asUInt, pc.asSInt)
    }
    cache.s0.write_bus_aligned_data := ibus.r.data.asTypeOf(chiselTypeOf(cache.s0.write_bus_aligned_data))

    cache.s0.write_bus_mask   := VecInit(-1.S(cache.s0.write_bus_mask.getWidth.W).asBools)
    // TODO: Write bus mask proper value, depending on counter
    //cache.s0.write_bus_mask   := write_bus_mask
    cache.s0.write_valid      := true.B

    when(vm_enabled) {
      cache.s1.paddr          := Cat(tlb.s1.read_data.ptag, pc(c.avLen - 1, c.pgoff_len)) // Virtual addressing use tlb data
    } .otherwise {
      cache.s1.paddr          := Cat((VecInit.tabulate(c.apLen - c.avLen) {n => pc(c.avLen - 1)}).asUInt, pc.asSInt)
    }


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
      cache.s0.vaddr            := cache_invalidate_counter << log2Ceil(c.icache_entry_bytes)
      val cache_invalidate_counter_ovfl = (((cache_invalidate_counter + 1.U) % ((c.icache_entries * c.icache_entry_bytes / c.bus_data_bytes).U)) === 0.U)
      when(!cache_invalidate_counter_ovfl) {
        cache_invalidate_counter  := ((cache_invalidate_counter + 1.U) % ((c.icache_entries * c.icache_entry_bytes / c.bus_data_bytes).U))
      }

      

      tlb.s0.cmd                := tlb_cmd.invalidate
      tlb.s0.virt_address_top   := tlb_invalidate_counter
      val tlb_invalidate_counter_ovfl = (((tlb_invalidate_counter + 1.U) % c.itlb_entries.U) === 0.U)
      when(!tlb_invalidate_counter_ovfl) {
        tlb_invalidate_counter    := (tlb_invalidate_counter + 1.U) % c.itlb_entries.U
      }

      when(tlb_invalidate_counter_ovfl && cache_invalidate_counter_ovfl) {
        state := IDLE
        new_request_allowed := true.B
        tlb_invalidate_counter := 0.U
        cache_invalidate_counter := 0.U

        printf("[Fetch] Flush done\n")
      }
    } .elsewhen(state === TLB_REFILL) {
      /**************************************************************************/
      /* TLB Refill logic                                                       */
      /**************************************************************************/
      
      ibus <> ptw.bus

      tlb.s0.virt_address_top     := pc(c.avLen - 1, c.pgoff_len)
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
      // Constants:
      ibus.ar.len    := (burst_len - 1).U
      ibus.ar.size   := log2Ceil(c.bus_data_bytes).U
      ibus.ar.lock   := false.B

      ibus.ar.valid := !ar_done
      ibus.ar.addr  := Cat(cache.s0.write_paddr(c.apLen - 1, log2Ceil(c.icache_entry_bytes)), burst_counter.value, 0.U(log2Ceil(c.bus_data_bytes).W)).asSInt
      
      when(ibus.ar.ready) {
        ar_done := true.B
      }
      
      when(ibus.ar.ready || ar_done) {
        when(ibus.r.valid) {
          cache.s0.cmd              := cache_cmd.write

          burst_counter.inc()

          // TODO: This depends on the vaddr and counter of beats
          cache.s0.vaddr            := Cat(pc(c.avLen - 1, log2Ceil(c.icache_entry_bytes)), burst_counter.value, 0.U(log2Ceil(c.bus_data_bytes).W))
          // Q: Why there is two separate ports?
          // A: Because paddr is used in cptag writing
          //    Meanwhile vaddr is used to calculate the entry_bus_num and entry index

          when(ibus.r.last) {
            state := IDLE
            pc_restart := true.B
            
            burst_counter.reset()
            // Count from zero to icache_ways
            cache_victim_way := (cache_victim_way + 1.U) % c.icache_ways.U
          }
        }
        ibus.r.ready := true.B
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
        printf("[Fetch] pc=0x%x, Instruction holding, accepted\n", pc)
      }
      busy_reg := true.B
    } .elsewhen (state === ACTIVE) {
      /**************************************************************************/
      /* Outputing/Comparing/checking access permissions                        */
      /**************************************************************************/
      uop.pc := pc

      /**************************************************************************/
      /* Instruction output selection logic                                     */
      /**************************************************************************/
      if (c.bus_data_bytes == c.iLen / 8) {
        // If bus is as wide as the instruction then just output that
        uop.instr := cache.s1.response.bus_aligned_data.asUInt.asTypeOf(Vec(c.bus_data_bytes / (c.iLen / 8), UInt(c.iLen.W)))(0)
      } else {
        // Otherwise select the section of the bus that corresponds to the PC
        val vector_select = pc(log2Ceil(c.bus_data_bytes) - 1, log2Ceil(c.iLen / 8))
        uop.instr := cache.s1.response.bus_aligned_data.asUInt.asTypeOf(Vec(c.bus_data_bytes / (c.iLen / 8), UInt(c.iLen.W)))(vector_select)
      }
      

      // Unconditionally leave output stage. If pipeline accepts the response
      // then new request will set this register below
      state := IDLE

      // TODO: Add pc checks for missalignment
      // TODO: RV64 Add pc checks for sign bit to be properly extended to xlen, otherwise throw exception
      when(vm_enabled && tlb.s1.miss) {           // TLB Miss, go to refill
        /**************************************************************************/
        /* TLB Miss                                                               */
        /**************************************************************************/
        uop_valid             := false.B
        state                       := TLB_REFILL
        printf("[Fetch] pc=0x%x, tlb miss\n", pc)
      } .elsewhen(vm_enabled && pagefault.fault) { // Pagefault, output the error to the next stages
        /**************************************************************************/
        /* Pagefault                                                              */
        /**************************************************************************/
        uop_valid             := true.B
        uop.ifetch_page_fault := true.B

        printf("[Fetch] pc=0x%x, Pagefault\n", pc)
      } .elsewhen(cache.s1.response.miss) { // Cache Miss, go to refill
        /**************************************************************************/
        /* Cache Miss                                                             */
        /**************************************************************************/
        uop_valid                   := false.B
        state                       := CACHE_REFILL

        saved_tlb_ptag              := tlb.s1.read_data.ptag

        printf("[Fetch] pc=0x%x, Cache miss\n", pc)
      } .otherwise {
        /**************************************************************************/
        /* TLB Hit, Cache hit                                                     */
        /**************************************************************************/
        uop_valid             := true.B
        printf("[Fetch] pc=0x%x, TLB/Cache hit, instr=0x%x\n", uop.pc, uop.instr)
      }
      
      /**************************************************************************/
      /* HOLD write logic                                                       */
      /**************************************************************************/
      // Unconditionally remember the uop.
      // Only read if hold_uop_valid is set below
      hold_uop                := uop

      when(uop_valid && uop_accept) { // Accepted start new fetch
        new_request_allowed         := true.B
        printf("[Fetch] pc=0x%x, Instruction accepted\n", pc)
        state                       := IDLE
      } .elsewhen (uop_valid && !uop_accept) { // Not accepted, dont start new fetch. Hold the output value
        state                       := HOLD
        printf("[Fetch] pc=0x%x, Instruction holding\n", pc)
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
        printf("[Fetch] Killing pc=0x%x\n", pc)
      } .elsewhen (cmd === fetch_cmd.flush) {
        /**************************************************************************/
        /* Flush                                                                  */
        /**************************************************************************/
        state := FLUSH
        printf("[Fetch] Flushing pc=0x%x\n", pc)
      } .elsewhen(cmd === fetch_cmd.set_pc) {
        // Note how pc_restart is not used here
        // It is because then the PC instruction would have been fetched and provided to pipeline twice
        // pc_next := new_pc
        // start_new_request := true.B
        pc_restart := true.B
        pc := new_pc
        busy_reg := false.B
        cmd_ready := true.B
        printf("[Fetch] Accepted command (cmd === set_pc) from pc_next=0x%x\n", pc_next)
        // TODO: Benchmark the synced next_pc vs not syncex next_pc
      } .elsewhen(cmd === fetch_cmd.none) {
        
        start_new_request := true.B
        printf("[Fetch] Starting fetch (cmd === none) from pc_next=0x%x\n", pc_next)

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
      ar_done                   := false.B
      pc_restart                := false.B
      pc := pc_next
    }
    
    busy := busy_reg
}


import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

object FetchGenerator extends App {
  (new ChiselStage).execute(Array("--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new Fetch(new coreParams(bus_data_bytes = 4)))))
}


