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
  val lane = Input(UInt(p.lane_width.W)) // Lane selection
  val offset = Input(UInt(offset_width.W)) // Offset selection inside Lane, one lane has 64 bytes -> 8 double words -> 3 bits
  //TODO: If required add back val unaligned_offset = Input(UInt(unaligned_offset_width.W)) // 64 bit -> 8 bytes -> 3 bits to fit unaligned address
  
  
  // Bus used only for writing
  val way_idx_in = Input(UInt(p.ways_width.W)) // select way for write
  val write_full_tag = Input(Bool())
  val state_tag_in = Input(UInt(state_tag_width.W))
  val address_tag_in = Input(UInt(p.address_ptag_width.W))

  val write_data = Input(Vec(8, UInt(8.W)))
  val write_mask = Input(Vec(8, Bool()))
  
  
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
  val data_out = Output(Vec(8, UInt(8.W))) // Contains output data
}



class CacheBackstorageIO(p: CacheParams) extends Bundle {
  // Cache I/O
  val s0 = new S0(p)
  val s1 = new S1(p)
}



class CacheBackstorage(p: CacheParams) extends Module {
  val io = IO(
    new CacheBackstorageIO(p)
  )

  val valid_out_reg = RegNext(io.s0.valid)
  io.s1.valid_out := valid_out_reg


  


  val read = io.s0.valid && (io.s0.req_type === CB_READ)
  val read_reg = RegNext(read)

  val write = Wire(Vec(p.ways, Bool()))
  
  for(i <- 0 until p.ways) {
    write(i) := io.s0.valid && // Request is valid
    (((io.s0.req_type === CB_WRITE) && (io.s0.way_idx_in === i.U)) || // Request is write to specific way
       (io.s0.req_type === CB_WRITE_ALL_WAYS)) // Request is write to all ways
  }

  // Data storage

  val data_storage = Seq.tabulate(p.ways) (i => SyncReadMem(1 << (p.lane_width + offset_width), Vec(8, UInt((8).W))))
  val data_storage_read_data_raw = Wire(Vec(p.ways, (Vec(8, UInt(8.W))))) // Raw read output from memory
  val data_storage_read_data_saved = Reg(Vec(p.ways, (Vec(8, UInt(8.W))))) // Registered output from memory
  val data_storage_read_data = Wire(Vec(p.ways, (Vec(8, UInt(8.W))))) // Final output from memory. It is valid after read request till next request (inclusive)
  data_storage_read_data_saved := data_storage_read_data
  
  for(i <- 0 until p.ways) {
    when(write(i)) {
      data_storage(i).write(Cat(io.s0.lane, io.s0.offset), io.s0.write_data, io.s0.write_mask)
    }
    data_storage_read_data_raw(i) := data_storage(i).read(Cat(io.s0.lane, io.s0.offset), read)
  }

  data_storage_read_data := Mux(read_reg, data_storage_read_data_raw, data_storage_read_data_saved)


  // address tag storage
  val address_tag_storage = Seq.tabulate(p.ways) (i => SyncReadMem(1 << (p.lane_width), UInt(p.address_ptag_width.W)))
  val address_tag_read_data_raw = Wire(Vec(p.ways, UInt(p.address_ptag_width.W))) // Raw read output from memory
  val address_tag_read_data_saved = Reg(Vec(p.ways, UInt(p.address_ptag_width.W))) // Registered output from memory
  val address_tag_read_data = Wire(Vec(p.ways, UInt(p.address_ptag_width.W))) // Final output from memory. It is valid after read request till next request (inclusive)
  address_tag_read_data_saved := address_tag_read_data
  
  for(i <- 0 until p.ways) {
    when(write(i)) {
      address_tag_storage(i).write(io.s0.lane, io.s0.address_tag_in)
    }
    address_tag_read_data_raw(i) := address_tag_storage(i).read(io.s0.lane, read)
  }

  address_tag_read_data := Mux(read_reg, address_tag_read_data_raw, address_tag_read_data_saved)


  // state tag storage
  val state_tag_storage = Seq.tabulate(p.ways) (i => SyncReadMem(1 << (p.lane_width), UInt(state_tag_width.W)))
  val state_tag_read_data_raw = Wire(Vec(p.ways, UInt(state_tag_width.W))) // Raw read output from memory
  val state_tag_read_data_saved = Reg(Vec(p.ways, UInt(state_tag_width.W))) // Registered output from memory
  val state_tag_read_data = Wire(Vec(p.ways, UInt(state_tag_width.W))) // Final output from memory. It is valid after read request till next request (inclusive)
  state_tag_read_data_saved := state_tag_read_data
  
  for(i <- 0 until p.ways) {
    when(write(i)) {
      state_tag_storage(i).write(io.s0.lane, io.s0.state_tag_in)
    }
    state_tag_read_data_raw(i) := state_tag_storage(i).read(io.s0.lane, read)
  }

  state_tag_read_data := Mux(read_reg, state_tag_read_data_raw, state_tag_read_data_saved)
  





  // Final output calculation

  io.s1.hit := false.B
  io.s1.way_idx_out := 0.U
  io.s1.address_tag_out := address_tag_read_data(0)
  io.s1.state_tag_out := state_tag_read_data(0)
  io.s1.data_out := data_storage_read_data(0)

  for(i <- 0 until p.ways) {
    when(state_tag_read_data(i)(state_tag_valid_idx) && (address_tag_read_data(i) === io.s1.ptag)) {
      io.s1.hit := true.B
      io.s1.way_idx_out := i.U
      io.s1.address_tag_out := address_tag_read_data(i)
      io.s1.state_tag_out := state_tag_read_data(i)
      io.s1.data_out := data_storage_read_data(i)
    }
  }

  
}
