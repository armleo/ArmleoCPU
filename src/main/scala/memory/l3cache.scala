package armleocpu

import chisel3._
import chisel3.util._
import chisel3.util.random._

/*
class L3CacheParams {
  val directoryEntriesLog2: Int = log2Ceil(8 * 1024)
  val cacheEntriesLog2: Int = log2Ceil(2 * 1024)
  val directoryWaysLog2: Int = 2
  val cacheWaysLog2: Int = 2
}


import bus_const_t._
class L3Cache(implicit val ccx: CCXParams) extends Module {
  /**************************************************************************/
  /* Parameters                                                             */
  /**************************************************************************/
  require(ccx.l3.directoryWaysLog2 >= 1)
  require(ccx.l3.cacheWaysLog2 >= 1)

  /**************************************************************************/
  /* Inputs/Outputs                                                         */
  /**************************************************************************/
  val io = IO(new Bundle {
    val up = Vec(ccx.coreCount, new corebus_t(ccx))
    //TODO: val upNonCoherent = Vec(ccx.l3.nonCoherent, new dbus_t(ccx))
    val down = new dbus_t(ccx)
  })

  /**************************************************************************/
  /* Structures                                                             */
  /**************************************************************************/
  class directoryEntry(tagWidth: Int) extends Bundle {
    val dirty = Bool() // Is this entry dirty
    val tag = UInt(tagWidth.W) // Address tag, we the use it to compare with the requested address
    val shared = UInt((ccx.coreCount).W) // Which cores have this line
    // If it is owned then only one bit is set
    val forwarder = UInt(log2Ceil(ccx.coreCount).W) // Last L1 that requested this data
  }

  class cacheEntry(tagWidth: Int) extends directoryEntry(tagWidth = tagWidth) {
    val data = UInt((ccx.busBytes * 8).W)
  }


  /**************************************************************************/
  /* Submodules                                                             */
  /**************************************************************************/

  val arb = Module(new Arbiter(io.up(0).ax.bits.cloneType, ccx.coreCount))

  val cache             = SRAM(1 << ccx.l3.cacheEntriesLog2,      Vec(1 << ccx.l3.cacheWaysLog2,     new cacheEntry    (ccx.apLen - ccx.l3.cacheEntriesLog2 - ccx.cacheLineLog2)), 0, 0, 1)
  val directory         = SRAM(1 << ccx.l3.directoryEntriesLog2,  Vec(1 << ccx.l3.directoryWaysLog2, new directoryEntry(ccx.apLen - ccx.l3.directoryEntriesLog2 - ccx.cacheLineLog2)), 0, 0, 1)

  val cacheValid          = RegInit(VecInit.tabulate((1 << ccx.l3.cacheEntriesLog2))          {idx: Int => 0.U(ccx.l3.cacheWaysLog2.W)})
  val directoryValid      = RegInit(VecInit.tabulate((1 << ccx.l3.directoryEntriesLog2))      {idx: Int => 0.U(ccx.l3.directoryWaysLog2.W)})
  
  val cacheValidReadData      = Reg(UInt((1 << ccx.l3.cacheEntriesLog2).W))
  val directoryValidReadData  = Reg(UInt((1 << ccx.l3.directoryEntriesLog2).W))

  /**************************************************************************/
  /* State                                                                  */
  /**************************************************************************/

  val addr = Wire(UInt(ccx.apLen.W))
  def getDirectoryEntryIdx(addr: UInt): UInt = {
    addr(ccx.l3.directoryEntriesLog2 + ccx.cacheLineLog2 - 1, ccx.cacheLineLog2)
  }
  def getCacheEntryIdx(addr: UInt): UInt = {
    addr(ccx.l3.cacheEntriesLog2 + ccx.cacheLineLog2 - 1, ccx.cacheLineLog2)
  }
  def getDirectoryTag(addr: UInt): UInt = {
    addr(ccx.apLen, ccx.l3.directoryEntriesLog2 + ccx.cacheLineLog2)
  }
  def getCacheTag(addr: UInt): UInt = {
    addr(ccx.apLen, ccx.l3.cacheEntriesLog2 + ccx.cacheLineLog2)
  }
  
  val directoryWdata = Wire(directory.readwritePorts(0).writeData(0).cloneType)

  directory.readwritePorts(0).address := getDirectoryEntryIdx(addr)
  directory.readwritePorts(0).writeData := VecInit.tabulate(1 << ccx.l3.directoryWaysLog2) {idx: Int => directoryWdata}
  directory.readwritePorts(0).mask.get := 0.U.asTypeOf(directory.readwritePorts(0).mask.get)
  directory.readwritePorts(0).enable := false.B
  directory.readwritePorts(0).isWrite := false.B

  when(directory.readwritePorts(0).enable) {
    directoryValidReadData := directoryValid(getDirectoryEntryIdx(addr))
  }

  val cacheWdata = Wire(cache.readwritePorts(0).writeData(0).cloneType)

  cache.readwritePorts(0).address := getCacheEntryIdx(addr)
  cache.readwritePorts(0).writeData := VecInit.tabulate(1 << ccx.l3.cacheWaysLog2) {idx: Int => cacheWdata}
  cache.readwritePorts(0).mask.get := 0.U.asTypeOf(cache.readwritePorts(0).mask.get)
  cache.readwritePorts(0).enable := false.B
  cache.readwritePorts(0).isWrite := false.B

  when(cache.readwritePorts(0).enable) {
    cacheValidReadData := cacheValid(getCacheEntryIdx(addr))
  }

  val cacheHits = cache.readwritePorts(0).readData.zip(cacheValidReadData.asBools).map {case (entry, valid) => valid && entry.tag === getCacheTag(pending_ax_bits.addr)}
  val cacheHit = VecInit(cacheHits).asUInt.orR
  val cacheHitIdx = PriorityEncoder(cacheHits)

  val directoryHits = directory.readwritePorts(0).readData.zip(directoryValidReadData.asBools).map {case (entry, valid) => valid && entry.tag === getDirectoryTag(pending_ax_bits.addr)}
  val directoryHit = VecInit(directoryHits).asUInt.orR
  val directoryHitIdx = PriorityEncoder(directoryHits)

  /**************************************************************************/
  /* State                                                                  */
  /**************************************************************************/

  val IDLE        = 0.U(4.W)
  // No active requests
  val DECISION    = 1.U(4.W)
  // Use the results from local buffer to decide what to do next
  // If read shared and somebody has it, then request it from there
  // If read unique and somebody owns it, then we ask to migrate
  // If read unique and somebody shares it, then we ask to invalidate
  // If invalidate then we mark the invalidation
  // If writeback then we mark it clean
  // If nobody has it, forward to memory
  val SNOOP       = 2.U(4.W)
  val RETURN      = 3.U(4.W)
  val BACKMEMORY  = 4.U(4.W)
  
  

  val state = RegInit(IDLE)
  val pending_chosen  = Reg(arb.io.chosen.cloneType)
  val pending_ax_bits = Reg(arb.io.out.bits.cloneType)


  /**************************************************************************/
  /* State                                                                  */
  /**************************************************************************/
  addr := arb.io.out.bits.addr


  // FIXME: Connect the arbiter: arb.io.in() <> 

  when(state === IDLE) {
    when(arb.io.out.valid) {
      pending_chosen                              := arb.io.chosen
      // FIXME: Save the chosen so that we dont DECISION the requesting host
      arb.io.out.ready                    := true.B

      pending_ax_bits                             := arb.io.out.bits

      addr                                := arb.io.out.bits.addr
      directory .readwritePorts(0).enable := true.B
      cache     .readwritePorts(0).enable := true.B

      state := DECISION
    }
  } .elsewhen(state === DECISION) {
    when(pending_ax_bits.op === CACHE_READ_SHARED) {
      when(cacheHit) {
        // We have cache hit, return the request
        io.up(pending_chosen).r.bits.data := cache.readwritePorts(0).readData(cacheHitIdx).data
        io.up(pending_chosen).r.bits.resp := SHARED_OKAY

      } .elsewhen(directoryHit) {
      } .otherwise {
        // All miss
      }
    }


    // Cases:
      // 1. L1 request is readShared.
      //      1.1. Directory has it.                            -> It select the responder and request from it
      //      1.2. Directory is a miss:
      //        1.2.1. Data array miss. Request from memory     -> insert to directory
      //        1.2.2. Data array is hit. Return data.          -> Remove from data array and insert into directory
      // 2. L1 request is readUnique.
      //      2.1. Directory has it.                            ->Request the forwarder if exists. Rest get snoop invalidate
      //      2.2. Directory is a miss:
      //            2.2.1. Data array miss. Request from memory -> insert to directory
      //            2.2.2. Data array is hit. Return data.      -> Remove from data array and insert into directory
      // 4. L1 request MAKE_UNIQUE
      //      2.1. Directory has it.
      // 3. L1 request is evict.
      //      3.1. Evicted line is dirty                        -> Store it into data array. Remove from directory
      //      3.2. Evicted line is clean                        -> Store it into data array. Remove from directory

    
    //addr := ax_bits.addr

    //directory.readwritePorts(0).readData

  }
  



  // On request arrival we accept the request
  // Then check the directory
  //      If requested unique and somebody has it, then send DECISION request to the owned core


  // Implement the actual storage
  // Implement the directory
  // Implement the DECISIONing
}

*/