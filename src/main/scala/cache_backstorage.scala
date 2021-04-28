package armleocpu

import chisel3._
import chisel3.util._


import Consts._
import CacheConsts._

// TODO: Decide if we want to have write-back or write-through cache?
// Write back benefits: Writes to near sections are cached
// TODO: Decide how big is cache backstorage width? Let's start with 64 bit and will extend if required



// First stage, request
class S0(p: CacheParams) extends Bundle {
  val valid = Input(Bool()) // Use valid signal to signal valid request
  // Valid() constructor is intentioanlly not used, it nests into request into bits and does not give any benefits

  val req_type = Input(UInt(req_type_width.W)) // NOOP, Read, Write, Write all ways
  // Request type

  // Shared bus for address data
  val lane = Input(UInt(lane_width.W)) // Lane selection
  val offset = Input(UInt(offset_width.W)) // Offset selection inside Lane, one lane has 64 bytes -> 8 double words -> 3 bits
  //TODO: If required add back val unaligned_offset = Input(UInt(unaligned_offset_width.W)) // 64 bit -> 8 bytes -> 3 bits to fit unaligned address
  
  
  // Bus used only for writing
  val way_idx_in = Input(UInt(p.ways_width.W)) // select way for write
  val write_full_tag = Input(Bool())
  val state_tag_in = Input(UInt(state_tag_width.W))
  val address_tag_in = Input(UInt(p.address_ptag_width.W))

  val write_data = Input(UInt(xLen.W))
  val write_mask = Input(UInt(8.W)) // Write mask
  
  
}

// Second stage, valid on next cycle after valid first stage request
// Inputs are required to be provided externally because for first cycle TLB request is not done yet

class S1(p: CacheParams) extends Bundle {
  val ptag = Input(UInt(p.address_ptag_width.W)) // Physical Tag of request, compared to tags stored in lanes

  val valid_out = Output(Bool())
  val state_tag_out = Output(UInt(state_tag_width.W))
  val address_tag_out = Output(UInt(p.address_ptag_width.W))

  val hit = Output(Bool())

  val way_idx_out = Output(UInt(p.ways_width.W)) // Contains way that had our requested data
  val data_out = Output(UInt(xLen.W)) // Contains output data
}



class CacheBackstorageIO(p: CacheParams) extends Bundle {
  // Cache I/O
  val s0 = new S0(p)
  val s1 = new S1(p)

  // SRAM I/O
  val data_storage_sram_io = Vec(p.ways, new sram_1rw_io(lane_width + offset_width, xLen, xLen/8))
  val state_tag_storage_sram_io = Vec(p.ways, new sram_1rw_io(lane_width, state_tag_width, 1))
  val address_tag_storage_sram_io = Vec(p.ways, new sram_1rw_io(lane_width, p.address_ptag_width, 1))
}



class CacheBackstorage(p: CacheParams) extends Module {
  val io = IO(
    new CacheBackstorageIO(p)
  )

  val valid_out_reg = RegNext(io.s0.valid)
  io.s1.valid_out := valid_out_reg
  val read = io.s0.valid && (io.s0.req_type === CB_READ)
  val write = VecInit.tabulate(p.ways) (i =>
    io.s0.valid // Request is valid
    && (((io.s0.req_type === CB_WRITE) && (io.s0.way_idx_in === i.U)) // Request is write to specific way
    || (io.s0.req_type === CB_WRITE_ALL_WAYS))) // Request is write to all ways

  for(i <- 0 until p.ways) {
    //val ds = io.data_storage_sram_io(i)
    io.data_storage_sram_io(i).address := Cat(io.s0.lane, io.s0.offset)
    io.data_storage_sram_io(i).read := read
    io.data_storage_sram_io(i).write := write(i)
    io.data_storage_sram_io(i).write_data := io.s0.write_data
    io.data_storage_sram_io(i).write_mask := io.s0.write_mask
  }

  
  for(i <- 0 until p.ways) {
    //val ats = io.address_tag_storage_sram_io(i)
    //val sts = io.state_tag_storage_sram_io(i)
    io.address_tag_storage_sram_io(i).address := io.s0.lane
    io.state_tag_storage_sram_io(i).address := io.s0.lane

    io.address_tag_storage_sram_io(i).read := read
    io.state_tag_storage_sram_io(i).read := read

    io.address_tag_storage_sram_io(i).write := write(i) && io.s0.write_full_tag
    io.state_tag_storage_sram_io(i).write := write(i) && io.s0.write_full_tag

    io.address_tag_storage_sram_io(i).write_data := io.s0.address_tag_in
    io.state_tag_storage_sram_io(i).write_data := io.s0.state_tag_in

    io.address_tag_storage_sram_io(i).write_mask := 1.U
    io.state_tag_storage_sram_io(i).write_mask := 1.U
  }

  io.s1.hit := false.B
  io.s1.way_idx_out := 0.U

  io.s1.data_out := io.data_storage_sram_io(0).read_data
  io.s1.address_tag_out := io.address_tag_storage_sram_io(0).read_data
  io.s1.state_tag_out := io.state_tag_storage_sram_io(0).read_data
  

  for(i <- 0 until p.ways) {
    when(io.state_tag_storage_sram_io(i).read_data(state_tag_valid_idx)
    && (io.address_tag_storage_sram_io(i).read_data === io.s1.ptag)) {
      
      io.s1.data_out := io.data_storage_sram_io(i).read_data
      io.s1.address_tag_out := io.address_tag_storage_sram_io(i).read_data
      io.s1.state_tag_out := io.state_tag_storage_sram_io(i).read_data

      io.s1.hit := true.B
      io.s1.way_idx_out := i.U
    }
    
  }
  

  // todo: Output formation
}
