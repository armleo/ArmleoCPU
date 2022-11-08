package armleocpu

import chisel3._
import chisel3.util._


import chisel3.experimental.ChiselEnum
import chisel3.experimental.dataview._

class PTW(c: coreParams) extends Module {
  val io = IO(new Bundle {
    // memory access bus
    val bus = new ibus_t(c)


    // request
    val vaddr = Input(UInt(32.W))
    val resolve_req = Input(Bool())
    
    // response
    val cplt = Output(Bool())
    val page_fault = Output(Bool())
    val access_fault = Output(Bool())
    val physical_address_top = Output(UInt(22.W))
    val meta = Output(UInt(8.W))

    
    // CSR values
    val mem_priv = Input(new MemoryPrivilegeState(c))
  })
  // constant outputs
  io.memory.write := false.B
  io.memory.writedata := 0.U
  

  val current_table_base = RegInit(0.U(22.W)) // a from spec
  val current_level = RegInit(0.U(2.W)) // i from spec
  
  val STATE_IDLE = 0.U(2.W);
  val STATE_TABLE_WALKING = 1.U(2.W);
  val state = RegInit(STATE_IDLE)
  val read_issued = RegInit(true.B)
  val saved_vaddr = RegInit(0.U(20.W))
  val saved_offset = RegInit(0.U(12.W))
  val vaddr_vpn = Wire(Vec(2, UInt(10.W)))
  vaddr_vpn(0) := saved_vaddr(9, 0)
  vaddr_vpn(1) := saved_vaddr(19, 10)
  
  // internal
  val pte_valid = io.memory.readdata(0)
  val pte_read = io.memory.readdata(1)
  val pte_write = io.memory.readdata(2)
  val pte_execute = io.memory.readdata(3)

  val pte_ppn1 = io.memory.readdata(31, 20)
  val pte_ppn0 = io.memory.readdata(19, 10)

  val pte_invalid = !pte_valid || (!pte_read && pte_write)
  val pte_isLeaf = pte_read || pte_execute
  val pte_leafMissaligned = Mux(current_level === 1.U,
                        io.memory.readdata(19, 10) =/= 0.U, // level == megapage
                        false.B)                // level == page => impossible missaligned
  val pte_pointer = io.memory.readdata(3, 0) === "b0001".U
  // outputs
  io.memory.burstcount := 1.U;
  io.memory.address := Cat(current_table_base, vaddr_vpn(current_level), "b00".U(2.W));
  io.memory.read := !read_issued && state === STATE_TABLE_WALKING
  io.physical_address_top := Cat(io.memory.readdata(31, 20), Mux(current_level === 1.U, saved_vaddr(9, 0), io.memory.readdata(19, 10)))


  io.cplt := false.B
  io.page_fault := false.B
  io.access_fault := false.B
  io.meta := io.memory.readdata(7, 0)
  switch(state) {
    is(STATE_IDLE) {
      io.cplt := false.B
      io.page_fault := false.B
      io.access_fault := false.B
      read_issued := false.B
      current_level := 1.U;
      saved_vaddr := io.vaddr(31, 12)
      saved_offset := io.vaddr(11, 0) // used for c.ptw_verbose purposes only
      current_table_base := io.matp_ppn;
      when(io.resolve_req) { // assumes io.matp_mode -> 1 
                //because otherwise tlb would respond with hit and ptw request would not happen
        state := STATE_TABLE_WALKING
        if(c.ptw_verbose)
          printf("[PTW] Resolve requested for virtual address 0x%x, io.matp_mode is 0x%x\n", io.vaddr, io.matp_mode)
      }
    }
    is(STATE_TABLE_WALKING) {
      io.cplt := false.B
      io.page_fault := false.B
      io.access_fault := false.B

      when(!io.memory.waitrequest) {
        read_issued := true.B;
      }
      when(!io.memory.waitrequest && io.memory.readdatavalid) {
        when(io.memory.response =/= MemHostIfResponse.OKAY) {
          io.access_fault := true.B
          io.page_fault := false.B
          io.cplt := true.B
          state := STATE_IDLE
          if(c.ptw_verbose)
            printf("[PTW] Resolve failed because io.memory.response is 0x%x for address 0x%x\n", io.memory.response, io.memory.address)
        } .elsewhen(pte_invalid) {
          io.cplt := true.B
          io.page_fault := true.B
          io.access_fault := false.B
          state := STATE_IDLE
          if(c.ptw_verbose)
            printf("[PTW] Resolve failed because pte 0x%x is invalid is 0x%x for address 0x%x\n", io.memory.readdata(7, 0), io.memory.readdata, io.memory.address)
        } .elsewhen(pte_isLeaf) {
          when(!pte_leafMissaligned) {
            io.cplt := true.B
            io.page_fault := false.B
            io.access_fault := false.B
            state := STATE_IDLE
            if(c.ptw_verbose)
              printf("[PTW] Resolve cplt 0x%x for address 0x%x\n", Cat(io.physical_address_top, saved_offset), Cat(saved_vaddr, saved_offset))
          } .elsewhen (pte_leafMissaligned){
            io.cplt := true.B
            io.page_fault := true.B
            io.access_fault := false.B
            state := STATE_IDLE
            if(c.ptw_verbose)
              printf("[PTW] Resolve missaligned 0x%x for address 0x%x, level = 0x%x\n", Cat(io.physical_address_top, saved_offset), Cat(saved_vaddr, saved_offset), current_level)
          }
        } .elsewhen (pte_pointer) { // pte is pointer to next level
          when(current_level === 0.U) {
            io.cplt := true.B
            io.page_fault := true.B
            io.access_fault := false.B
            state := STATE_IDLE
            if(c.ptw_verbose)
              printf("[PTW] Resolve page_fault for address 0x%x\n", Cat(saved_vaddr, saved_offset))
          } .elsewhen(current_level === 1.U) {
            io.access_fault := false.B
            io.cplt := false.B
            io.page_fault := false.B
            current_level := current_level - 1.U
            current_table_base := io.memory.readdata(31, 10)
            read_issued := false.B
            if(c.ptw_verbose)
              printf("[PTW] Resolve going to next level for address 0x%x, pte = %x\n", Cat(saved_vaddr, saved_offset), io.memory.readdata)
          }
        }
      }
    }
  }
}
