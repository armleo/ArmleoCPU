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
}



class CacheBackstorage(p: CacheParams) extends Module {
  val io = IO(
    new CacheBackstorageIO(p)
  )

  val valid_out_reg = RegNext(io.s0.valid)
  io.s1.valid_out := valid_out_reg


  val data_storage = SyncReadMem(1 << (lane_width + offset_width), Vec(p.ways * 8, UInt((8).W)))
  val address_tag_storage = SyncReadMem(1 << (lane_width), Vec(p.ways, UInt((p.address_ptag_width).W)))
  val state_tag_storage = SyncReadMem(1 << (lane_width), Vec(p.ways, UInt((p.address_ptag_width).W)))

  io.s1.hit := false.B
  io.s1.way_idx_out := 0.U
  io.s1.address_tag_out := 0.U
  io.s1.state_tag_out := 0.U
  //io.s1.data_out := 0.U


  val read = io.s0.valid && (io.s0.req_type === CB_READ)
  val read_reg = RegNext(read)

  val write = Wire(Vec(p.ways, Bool()))
  
  for(i <- 0 until p.ways) {
    write(i) := io.s0.valid && // Request is valid
    (((io.s0.req_type === CB_WRITE) && (io.s0.way_idx_in === i.U)) || // Request is write to specific way
       (io.s0.req_type === CB_WRITE_ALL_WAYS)) // Request is write to all ways
  }
  
  val byte_writes = Wire(Vec(p.ways * 8, UInt(8.W)))
  val byte_enables = Wire(Vec(p.ways * 8, Bool()))

  for(i <- 0 until p.ways * 8) {
    val m = i % 8
    
    byte_writes(i) := io.s0.write_data(((m+1) * 8) - 1, ((m) * 8))
    byte_enables(i) := write(i / 8) && io.s0.write_mask(i % 8)
  }
  
  data_storage.write(Cat(io.s0.lane, io.s0.offset), byte_writes, byte_enables)
  // Declarations:
  //val data_storage_read_data_bytes = Wire(Vec(p.ways * 8, UInt(8.W))) // Contains data read from memory
  val data_storage_read_data = Wire(Vec(p.ways * 8, UInt(8.W))) // Output data bytes, valid until next read
  val data_storage_read_data_saved_bytes = Reg(Vec(p.ways * 8, UInt(8.W)))

  val data_storage_read_data_bytes = data_storage.read(Cat(io.s0.lane, io.s0.offset), read)
  /*when(read) {
    data_storage_read_data_bytes :=  // When read request happens request data from memory
  }*/
  
  data_storage_read_data_saved_bytes := Mux(read_reg, data_storage_read_data_bytes, data_storage_read_data_saved_bytes)
  /*
  for(i <- 0 until 8 * p.ways) {
    
  }*/

  data_storage_read_data := Mux(read_reg, data_storage_read_data_bytes, data_storage_read_data_saved_bytes) // If last cycle was read request, save it to output it on next cycles


  //io.s1.hit := false.B
  //io.s1.way_idx_out := 0.U
  io.s1.data_out := 0.U
  //println(data_storage_read_data(0))
  /*val data_out = Wire(UInt(xLen.W))
   data_out
  for(i <- 0 until 8) {
    
    println(data_out(((i+1) * 8) - 1, ((i) * 8)))
    println(data_storage_read_data(i))

    data_out(((i+1) * 8) - 1, ((i) * 8)) := data_storage_read_data(i)
  }*/
  /*io.s1.address_tag_out := io.address_tag_storage_sram_io(0).read_data
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
    
  }*/

  /*
  for(way <- 0 until p.ways) {
    for(byteNum <- 0 until 8) {
      when(write(way) && io.s0.write_mask(byteNum)) {

        data_storage.write(((way << 3) | byteNum).U, Vec of bytes, Vec of byte enables)//
      }
    }
  }*/
  

/*
  
  // Each write for it's way
  // Create write masks vector of bools

  val write_masks_vector_of_vectors = Seq.tabulate(p.ways) (i => Fill(8, write(i)) & io.s0.write_mask)
  val write_masks = collection.mutable.ArrayBuffer(write_masks_vector_of_vectors(0))
  
  for(i <- 1 until p.ways) {
    write_masks ++= Seq(write_masks_vector_of_vectors(i))
  }
  
  val data_storage_write_data = VecInit(p.ways) (i => io.s0.write_data)


  data_storage.write(, data_storage_write_data, write_masks)

  
  
  
  
  data_storage.read(Cat(io.s0.lane, io.s0.offset), s0.io.valid)



  address_tag_storage.write(io.s0.lane, Fill(p.ways, io.s0.address_tag_in), write & Fill(p.ways, ))


  
  for(i <- 0 until p.ways) {
    //val ats = io.address_tag_storage_sram_io(i)
    //val sts = io.state_tag_storage_sram_io(i)
    io.address_tag_storage_sram_io(i).address := 
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

  
  

  // todo: Output formation
  */
}
