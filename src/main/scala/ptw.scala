package armleocpu

import chisel3._
import chisel3.util._


import chisel3.util._
//import chisel3.experimental.dataview._

/*
class PTW(instName: String = "iptw ",
  c: CoreParams = new CoreParams,
  verbose: Boolean = false
) extends Module {
  // FIXME: 64 bit variant
  // TODO: Add PTW tests in isa tests
  // memory access bus
  val bus                   = IO(new ibus_t(c))
  val bus_data_bytes        = c.bp.data_bytes

  // request
  val vaddr                 = IO(Input(UInt(c.xLen.W)))
  val resolve_req           = IO(Input(Bool()))

  // response
  val cplt                  = IO(Output(Bool()))
  val pagefault            = IO(Output(Bool()))
  val accessfault          = IO(Output(Bool()))
  //FIXME: val pte_o                 = IO(Output(UInt(c.xLen.W)))
  //FIXME: val rvfi_pte              = IO(Output(Vec(4, UInt(c.xLen.W))))
  
  val physical_address_top  = IO(Output(UInt((44).W)))
  val meta                  = IO(Output())


  // CSR values
  val csr_regs_output              = IO(Input(new CsrRegsOutput(c)))


  val log = new Logger(c.lp.coreName, instName, verbose)

  
  // constant outputs
  bus.ar.valid  := false.B

  // TODO: needs to be different depending on xLen value and csr_regs_output.mode
  bus.ar.bits.size   := log2Ceil(c.xLen_bytes).U
  bus.ar.bits.lock   := false.B
  bus.ar.bits.len    := 0.U

  bus.r.ready   := false.B

  

  val current_table_base = Reg(UInt((c.apLen - c.pgoff_len).W)) // a from spec
  val current_level = Reg(UInt((log2Ceil(c.pagetableLevels) + 1).W)) // i from spec
  
  val STATE_IDLE            = 0.U(3.W)
  val STATE_PMA_PMP         = 1.U(3.W)
  val STATE_AR              = 2.U(3.W)
  val STATE_R               = 3.U(3.W)
  val STATE_TABLE_WALKING   = 4.U(3.W)
  val state = RegInit(STATE_IDLE)

  val pma_error = Reg(Bool())
  val bus_error = Reg(Bool())
  val saved_vaddr = Reg(UInt(c.avLen.W))

  val saved_vaddr_top = saved_vaddr(c.avLen - 1, c.pgoff_len)
  val saved_offset = saved_vaddr(c.pgoff_len - 1, 0)

  val vaddr_vpn = Wire(Vec(3, UInt(9.W)))
  vaddr_vpn(0) := saved_vaddr(20, 12)
  vaddr_vpn(1) := saved_vaddr(29, 21)
  vaddr_vpn(2) := saved_vaddr(38, 30)

  // TODO: RV64 VPN will be 9 bits each in 64 bit
  

  val pte_value   = Reg(UInt(c.xLen.W))

  val pte_valid   = pte_value(0)
  val pte_read    = pte_value(1)
  val pte_write   = pte_value(2)
  val pte_execute = pte_value(3)

  val pte_invalid = !pte_valid || (!pte_read && pte_write) || (pte_value(63, 54).orR)
  val pte_isLeaf  = pte_read || pte_execute
  val pte_leafMissaligned = (!pte_value(27, 10).orR && (current_level === 2.U)) || 
                            (!pte_value(18, 10).orR && (current_level === 1.U))
  val pte_pointer = pte_value(3, 0) === "b0001".U
  // outputs

  // We do no align according to bus_data_bytes, since we only request one specific PTE and not more
  bus.ar.bits.addr := Cat(current_table_base, vaddr_vpn(current_level), "b00".U(2.W)).asSInt;

  // FIXME: Test this below
  val physical_address_top_55_30 = pte_value(53, 28)
  val physical_address_top_29_21 = Mux(current_level >= 2.U, saved_vaddr(29, 21), pte_value(27, 19))
  val physical_address_top_20_12 = Mux(current_level >= 1.U, saved_vaddr(20, 12), pte_value(18, 10))
  physical_address_top := Cat(physical_address_top_55_30, physical_address_top_29_21, physical_address_top_20_12)


  cplt          := false.B
  pagefault    := false.B
  accessfault  := false.B
  meta          := pte_value(7, 0).asTypeOf(new tlbmeta_t)

  // TODO: Add PTE storage for RVFI
  
  
  val (defined, memory) = PMA(c, bus.ar.bits.addr.asUInt)
  
  
  switch(state) {
    is(STATE_IDLE) {
      current_level := 1.U
      saved_vaddr := vaddr
      current_table_base := csr_regs_output.ppn
      when(resolve_req) { // assumes csr_regs_output.mode -> 1 
                //because otherwise tlb would respond with hit and ptw request would not happen
        state := STATE_AR
        
        log("Resolve requested for virtual address 0x%x, csr_regs_output.mode is 0x%x", vaddr, csr_regs_output.mode)
      }
    }
    /*
    is(STATE_PMA_PMP)
    */
    // FIXME: Add PMP/PMA Check
    // FIXME: Save ptes instead of the tlb data
    // FIXME: Save PTEs to return to top, for RVFI
    is(STATE_AR) {
      pma_error := false.B // Reset the PMA Error
      // Bus error does not need to be reset, because it's unconitionally set
      when(defined && memory) {
        bus.ar.valid := true.B
        when(bus.ar.ready) {
          state := STATE_R
          log("AR request sent")
        }
      } .otherwise {
        log("PMA Check failed defined=%x, memory=%x", defined, memory)
        state := STATE_TABLE_WALKING
        pma_error := true.B
      }
    }
    is(STATE_R) {
      when(bus.r.valid) {
        bus_error := bus.r.bits.resp =/= bus_resp_t.OKAY
        state := STATE_TABLE_WALKING
        when(bus.r.bits.resp =/= bus_resp_t.OKAY) {
          
          log("Resolve failed because bus.r.bits.resp is 0x%x for address 0x%x", bus.r.bits.resp, bus.ar.bits.addr)
        } .otherwise {
            // We use saved_vaddr_top lsb bits to select the PTE from the bus
            // as the pte might be 32 bit, meanwhile the bus can be 128 bit
            // TODO: RV64 replace bus_data_bytes/4 with possibly /8 for xlen == 64
          pte_value := frombus(c, bus.ar.bits.addr.asUInt, bus.r.bits.data)
          
          log("Bus request complete resp=0x%x data=0x%x ar.addr=0x%x pte_value=0x%x", bus.r.bits.resp, bus.r.bits.data, bus.ar.bits.addr.asUInt, pte_value)
          
          
        }
      }
      bus.r.ready := true.B
    }
    is(STATE_TABLE_WALKING) {
      state := STATE_IDLE
      when(pma_error) {
        accessfault := true.B
        cplt := true.B
        log("Responding with pma error")
      } .elsewhen (bus_error) {
        accessfault := true.B
        cplt := true.B
        log("Responding with bus error")
      } .elsewhen(pte_invalid) {
        cplt := true.B
        pagefault := true.B
        
        log("Resolve failed because pte 0x%x is invalid is 0x%x for address 0x%x", pte_value(7, 0), pte_value, bus.ar.bits.addr)
      } .elsewhen(pte_isLeaf) {
        when(!pte_leafMissaligned) {
          cplt := true.B
          
          log("Resolve cplt 0x%x for address 0x%x", Cat(physical_address_top, saved_offset), saved_vaddr)
        } .elsewhen (pte_leafMissaligned){
          cplt := true.B
          pagefault := true.B
          
          log("Resolve missaligned 0x%x for address 0x%x, level = 0x%x", Cat(physical_address_top, saved_offset), saved_vaddr, current_level)
        }
      } .elsewhen (pte_pointer) { // pte is pointer to next level
        when(current_level === 0.U) {
          cplt := true.B
          pagefault := true.B
          
          log("Resolve pagefault for address 0x%x", saved_vaddr)
        } .elsewhen(current_level === 1.U) {
          current_level := current_level - 1.U
          current_table_base := pte_value(31, 10)
          state := STATE_AR
          
          log("Resolve going to next level for address 0x%x, pte = %x", saved_vaddr, pte_value)
        }
      }
    }
  }
}

import _root_.circt.stage.ChiselStage
import chisel3.stage.ChiselGeneratorAnnotation


object PTWGenerator extends App {
  (new ChiselStage).execute(Array("--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new PTW)))
}


*/