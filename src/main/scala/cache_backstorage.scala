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

  val req_type = Input(UInt(req_type_width.W)) // NOOP, Read, Write
  // Request type

  // Shared bus for address data
  val lane = Input(UInt(lane_width.W)) // Lane selection
  val offset = Input(UInt(offset_width.W)) // Offset selection inside Lane, one lane has 64 bytes -> 8 double words -> 3 bits
  //TODO: If required add back val unaligned_offset = Input(UInt(unaligned_offset_width.W)) // 64 bit -> 8 bytes -> 3 bits to fit unaligned address
  
  
  // Bus used only for writing
  val way_idx = Input(UInt(p.ways_width.W)) // select way for write
  val write_state_tag = Input(Bool())
  val state_tag_in = Input(new state_tag)
  val write_mask = Input(UInt(8.W)) // Write mask
  
  
}

// Second stage, valid on next cycle after valid first stage request
// Inputs are required to be provided externally because for first cycle TLB request is not done yet

class S1(p: CacheParams) extends Bundle {
  val ptag = Input(UInt(p.tag_width.W)) // Physical Tag of request, compared to tags stored in lanes

  val valid = Output(Bool())
  val state_tag_out = Output(new state_tag)
  val hit = Output(Bool())

  val way_idx = Output(UInt(p.ways_width.W)) // Contains way that had our requested data
  val data_out = Output(UInt(xLen.W)) // Contains output data
}



class CacheBackstorageIO(p: CacheParams) extends Bundle {
  val s0 = new S0(p)
  val s1 = new S1(p)
  val data_storage_sram_io = Vec(p.ways, new sram_1rw_io(lane_width + offset_width, xLen))
  val tag_storage_sram_io = Vec(p.ways, new sram_1rw_io(lane_width, state_tag_width))
  
}



class CacheBackstorage(p: CacheParams) extends Module {
  val io = IO(
    new CacheBackstorageIO(p)
  )
  for(ds <- io.data_storage_sram_io) {
    ds.address := Cat(io.s0.lane, io.s0.offset)
    ds.read := valid && (io.req_type == CB_READ)
    ds.write := valid && (io.req_type == CB_WRITE)
    ds.write_data := 
  }

  when(valid) {

  }
}