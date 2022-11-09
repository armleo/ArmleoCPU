package armleocpu

import chisel3._
import chisel3.util._


import chisel3.experimental.ChiselEnum
import chisel3.experimental.dataview._


class ptw(c: coreParams) extends Module {
  // memory access bus
  val bus = IO(new ibus_t(c))
  val bus_data_bytes = c.bus_data_bytes

  // request
  val vaddr = Input(UInt(c.xLen.W))
  val resolve_req = Input(Bool())

  // response
  val cplt = Output(Bool())
  val page_fault = Output(Bool())
  val access_fault = Output(Bool())
  val physical_address_top = Output(UInt(c.ptag_len.W))
  val meta = Output(new tlbmeta_t)


  // CSR values
  val mem_priv = Input(new MemoryPrivilegeState(c))


  // constant outputs
  bus.ar.valid := false.B
  // TODO: Add the rest of bus.ar.

  

  val current_table_base = RegInit(0.U(c.ptag_len.W)) // a from spec
  val current_level = RegInit(0.U((log2Ceil(c.pagetable_levels) + 1).W)) // i from spec
  
  val STATE_IDLE            = 0.U(2.W)
  val STATE_AR              = 1.U(2.W)
  val STATE_R               = 2.U(2.W)
  val STATE_TABLE_WALKING   = 3.U(2.W)
  val state = RegInit(STATE_IDLE)
  // TODO: Convert the verilog code to the chisel


  val pma_error = RegInit(false.B)
  val saved_vaddr = RegInit(0.U(20.W))
  val saved_offset = RegInit(0.U(12.W))
  val vaddr_vpn = Wire(Vec(2, UInt(10.W)))
  vaddr_vpn(0) := saved_vaddr(9, 0)
  vaddr_vpn(1) := saved_vaddr(19, 10)

  // TODO: RV64 VPN will be 9 bits each in 64 bit
  
  // internal
  // TODO: Proper PTE calculation
  // We use saved_vaddr lsb bits to select the PTE from the bus
  // TODO: RV64 replace bus_data_bytes/4 with possibly /8 for xlen == 64

  val pte_value = Reg(UInt(c.xLen.W))

  // TODO: PTE value calculation
  val pte_valid = pte_value(0)
  val pte_read = pte_value(1)
  val pte_write = pte_value(2)
  val pte_execute = pte_value(3)

  val pte_ppn1 = pte_value(31, 20)
  val pte_ppn0 = pte_value(19, 10)

  val pte_invalid = !pte_valid || (!pte_read && pte_write)
  val pte_isLeaf = pte_read || pte_execute
  val pte_leafMissaligned = Mux(current_level === 1.U,
                        pte_value(19, 10) =/= 0.U, // level == megapage
                        false.B)                // level == page => impossible missaligned
  val pte_pointer = pte_value(3, 0) === "b0001".U
  // outputs

  // We do no align according to bus_data_bytes, since we only request one specific PTE and not more
  bus.ar.addr := Cat(current_table_base, vaddr_vpn(current_level), "b00".U(2.W))
  physical_address_top := Cat(pte_value(31, 20), Mux(current_level === 1.U, saved_vaddr(9, 0), pte_value(19, 10)))


  cplt := false.B
  page_fault := false.B
  access_fault := false.B
  meta := pte_value(7, 0)
  switch(state) {
    is(STATE_IDLE) {
      current_level := 1.U;
      saved_vaddr := vaddr(31, 12)
      saved_offset := vaddr(11, 0) // used for c.ptw_verbose purposes only
      current_table_base := mem_priv.ppn;
      when(resolve_req) { // assumes mem_priv.mode -> 1 
                //because otherwise tlb would respond with hit and ptw request would not happen
        state := STATE_TABLE_WALKING
        if(c.ptw_verbose)
          printf("[PTW] Resolve requested for virtual address 0x%x, mem_priv.mode is 0x%x\n", vaddr, mem_priv.mode)
      }
    }
    is(STATE_AR) {
      bus.ar.valid := true.B
      when(bus.ar.ready) {
        state := STATE_R
        if(c.ptw_verbose)
          printf("[PTW] AR request sent")
      }
    }
    is(STATE_R) {
      when(bus.r.valid) {
        pma_error := bus.r.resp =/= bus_resp_t.OKAY
        when(bus.r.resp =/= bus_resp_t.OKAY) {
          if(c.ptw_verbose)
            printf("[PTW] Resolve failed because bus.r.resp is 0x%x for address 0x%x\n", bus.r.resp, bus.ar.addr)
        } .otherwise {
          pte_value := bus.r.data.asTypeOf(Vec(bus_data_bytes / 4, UInt(32.W)))(saved_vaddr % (bus_data_bytes / 4).U)
        }
      }
    }
    is(STATE_TABLE_WALKING) {
      when (pma_error) {
        access_fault := true.B
        page_fault := false.B
        cplt := true.B
      } .elsewhen(pte_invalid) {
        cplt := true.B
        page_fault := true.B
        access_fault := false.B
        state := STATE_IDLE
        if(c.ptw_verbose)
          printf("[PTW] Resolve failed because pte 0x%x is invalid is 0x%x for address 0x%x\n", pte_value(7, 0), pte_value, bus.ar.addr)
      } .elsewhen(pte_isLeaf) {
        when(!pte_leafMissaligned) {
          cplt := true.B
          page_fault := false.B
          access_fault := false.B
          state := STATE_IDLE
          if(c.ptw_verbose)
            printf("[PTW] Resolve cplt 0x%x for address 0x%x\n", Cat(physical_address_top, saved_offset), Cat(saved_vaddr, saved_offset))
        } .elsewhen (pte_leafMissaligned){
          cplt := true.B
          page_fault := true.B
          access_fault := false.B
          state := STATE_IDLE
          if(c.ptw_verbose)
            printf("[PTW] Resolve missaligned 0x%x for address 0x%x, level = 0x%x\n", Cat(physical_address_top, saved_offset), Cat(saved_vaddr, saved_offset), current_level)
        }
      } .elsewhen (pte_pointer) { // pte is pointer to next level
        when(current_level === 0.U) {
          cplt := true.B
          page_fault := true.B
          access_fault := false.B
          state := STATE_IDLE
          if(c.ptw_verbose)
            printf("[PTW] Resolve page_fault for address 0x%x\n", Cat(saved_vaddr, saved_offset))
        } .elsewhen(current_level === 1.U) {
          access_fault := false.B
          cplt := false.B
          page_fault := false.B
          current_level := current_level - 1.U
          current_table_base := pte_value(31, 10)
          state := STATE_AR
          if(c.ptw_verbose)
            printf("[PTW] Resolve going to next level for address 0x%x, pte = %x\n", Cat(saved_vaddr, saved_offset), pte_value)
        }
      }
    }
  }
}
