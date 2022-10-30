package armleocpu

import chisel3._
import chisel3.util._

import chisel3.experimental.ChiselEnum


object TlbConsts {
  aLen = 34
  vtag_len = aLen - 12
}

import TlbConsts._

class tlbmeta_t extends Bundle {
  val dirty   = Bool()
  val access  = Bool()
  val global  = Bool()
  val user    = Bool()
  val execute = Bool()
  val write   = Bool()
  val read    = Bool()
  val valid   = Bool()
}

object tlb_cmd extends ChiselEnum {
  val none, resolve, invalidate, write = Value
}

class TLB_S0 extends Bundle {
  // Command for TLB
  val cmd = Input(UInt(tlb_cmd.getWidth.W))

  val virt_address = Input(UInt(.W))
  
  val write_data = Input(new Bundle{
    val meta = new tlbmeta_t
    val ptag = UInt(PHYS_ADDRESS_W.W)
  })
}

class TLB_S1 extends Bundle {
  val miss = Output(Bool())

  val access_permissions_tag_output = Output(UInt(8.W))
  val ptag_output = Output(UInt(PHYS_ADDRESS_W.W))
}

class TLB(ways: Int, entries: Int) extends Module {
  val io = IO(new Bundle{
    val s0 = new TLB_S0()
    val s1 = new TLB_S1()
  })

  // Parameter based calculations
  val VIRT_TAG_W = VIRT_ADDRESS_W - ENTRIES_W
  val ENTRIES = 1 << ENTRIES_W;
  val tlb_ways_clog2 = log2Ceil(tlb_ways)
  require(VIRT_TAG_W > 0)

  // Virtual address decomposition
  val s0_index = io.s0.virt_address(ENTRIES_W-1, 0)
  val s0_vtag = io.s0.virt_address(VIRT_ADDRESS_W-1, ENTRIES_W)

  // Memory units
  val accesstag_permissions_storage       = Seq.tabulate(tlb_ways) (i => Module(new sram_1rw(depth_arg = ENTRIES, data_width = 8, mask_width = 1)))
  val vtag_storage                        = Seq.tabulate(tlb_ways) (i => Module(new sram_1rw(depth_arg = ENTRIES, data_width = VIRT_TAG_W, mask_width = 1)))
  val ptag_storage                        = Seq.tabulate(tlb_ways) (i => Module(new sram_1rw(depth_arg = ENTRIES, data_width = PHYS_ADDRESS_W, mask_width = 1)))
  
  // Command decoding. CMD is used to guarantee that only one cmd is executed at the same time
  val s0_resolve_req = io.s0.cmd === TLB_CMD_RESOLVE
  val s0_invalidate_all_ways = io.s0.cmd === TLB_CMD_INVALIDATE
  val s0_write_victim = io.s0.cmd === TLB_CMD_NEW_ENTRY

  // Registers inputs for use in second cycle
  val s1_vtag = RegEnable(s0_vtag, s0_resolve_req)

  // Keeps track of victim
  val victim_bits = if (tlb_ways_clog2 > 0) tlb_ways_clog2 else 1
  val victim_way = RegInit(0.U(victim_bits.W))
  if(tlb_ways > 0) {
    when(s0_write_victim) {
      // tlb_ways may be not power of two, so cap it at that value
      when(victim_way === (tlb_ways.U - 1.U)) {
        victim_way := 0.U
      } .otherwise {
        victim_way := victim_way + 1.U
      }
    }
  } else {
    victim_way := 0.U;
  }

  for(i <- 0 until tlb_ways) {
    accesstag_permissions_storage(i).io.address := s0_index
    vtag_storage(i).io.address := s0_index
    ptag_storage(i).io.address := s0_index

    // Data is read only when requested
    accesstag_permissions_storage(i).io.read := s0_resolve_req
    vtag_storage(i).io.read := s0_resolve_req
    ptag_storage(i).io.read := s0_resolve_req
    
    // Write bus
    // Accesstag is modified for writes and invalidations
    // PTAG/VTAG is not modified in invalidate, because valid bit is set to zero.
    // This reduces power consumption because less bit transition
    accesstag_permissions_storage(i).io.write := s0_invalidate_all_ways || (s0_write_victim && victim_way === i.U)
    vtag_storage(i).io.write := (s0_write_victim && victim_way === i.U)
    ptag_storage(i).io.write := (s0_write_victim && victim_way === i.U)

    accesstag_permissions_storage(i).io.write_data(0) := Mux(s0_invalidate_all_ways, 0.U(8.W), io.s0.access_permissions_tag_input)
    vtag_storage(i).io.write_data(0) := s0_vtag // Vtag is written value from input
    ptag_storage(i).io.write_data(0) := io.s0.ptag_input // We write ptag input to memory for write requests

    accesstag_permissions_storage(i).io.write_mask := 1.U
    vtag_storage(i).io.write_mask := 1.U
    ptag_storage(i).io.write_mask := 1.U
  }

  io.s1.access_permissions_tag_output := accesstag_permissions_storage(0).io.read_data(0)
  io.s1.ptag_output := ptag_storage(0).io.read_data(0)
  io.s1.miss := true.B
  for(i <- 0 until tlb_ways) {
    when((accesstag_permissions_storage(i).io.read_data(0)(0) === 1.U) && (s1_vtag === vtag_storage(i).io.read_data(0))) {
      // hit
      io.s1.miss := false.B
      io.s1.access_permissions_tag_output := accesstag_permissions_storage(i).io.read_data(0)
      io.s1.ptag_output := ptag_storage(i).io.read_data(0)
    }.otherwise {
      // miss
      io.s1.miss := true.B
    }
  }
  
}
