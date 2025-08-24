package armleocpu

import chisel3._
import chisel3.util._
import chisel3.util.random._
import busConst._

/*
L3 Cache
L2 Cache is separate because it may be able to use coherence to access peers storage. While L3 is unable to do that.
Has two storages:
  Directory
  Data storage
When a request is satisfied, then directory is updated and data is forwarded to the requester L2.
When L2 evicts it is recorded into data storage.

When any round robin selects an L2 cache then the request from that cache is accepted
Then the both the directory and data storage makes requests

L3 Cache is divided into banks.
Cache line is 64 bytes. Each 64 byte is stored in its respective bank
The L3 Cache wrapper uses address to forward requests and responses between banks


    
*/

class L3CacheParams {
  val directoryEntriesLog2: Int = log2Ceil(8 * 1024)
  val cacheEntriesLog2: Int = log2Ceil(2 * 1024)
  val directoryWaysLog2: Int = 2
  val cacheWaysLog2: Int = 2
}


/*
class L3CacheBank(implicit val ccx: CCXParams) extends Module {
  /**************************************************************************/
  /* Parameters                                                             */
  /**************************************************************************/
  require(ccx.l3.directoryWaysLog2 >= 1)
  require(ccx.l3.cacheWaysLog2 >= 1)

  /**************************************************************************/
  /* Inputs/Outputs                                                         */
  /**************************************************************************/
  val io = IO(new Bundle {
    val up = Vec(ccx.coreCount, new CoherentBus)
    val down = new Bus
  })

  /**************************************************************************/
  /* Structures                                                             */
  /**************************************************************************/
  class directoryEntry(tagWidth: Int) extends Bundle {
    val tag = UInt(tagWidth.W)
    val valid = Bool()
    val dirty = Bool()
    val shared = UInt((ccx.coreCount).W)
    val forwarder = UInt(log2Ceil(ccx.coreCount).W) // Last L1 that requested this data
  }

  class cacheEntry(tagWidth: Int) extends directoryEntry(tagWidth = tagWidth) {
    val data = UInt((ccx.busBytes * 8).W)
  }


  /**************************************************************************/
  /* Submodules                                                             */
  /**************************************************************************/

  val arb = Module(new Arbiter(io.up(0).req.bits.cloneType, ccx.coreCount))
  
  val cache             = SRAM(1 << ccx.l3.cacheEntriesLog2,      Vec(1 << ccx.l3.cacheWaysLog2,     new cacheEntry    (ccx.apLen - ccx.l3.cacheEntriesLog2 - ccx.cacheLineLog2)), 0, 0, 1)
  val directory         = SRAM(1 << ccx.l3.directoryEntriesLog2,  Vec(1 << ccx.l3.directoryWaysLog2, new directoryEntry(ccx.apLen - ccx.l3.directoryEntriesLog2 - ccx.cacheLineLog2)), 0, 0, 1)

  //val writeBackQueue = new Queue()
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
  directory.readwritePorts(0).mask.get := 0.U
  directory.readwritePorts(0).enable := false.B
  directory.readwritePorts(0).isWrite := false.B

  val cacheWdata = Wire(cache.readwritePorts(0).writeData(0).cloneType)

  cache.readwritePorts(0).address := getCacheEntryIdx(addr)
  cache.readwritePorts(0).writeData := VecInit.tabulate(1 << ccx.l3.cacheWaysLog2) {idx: Int => cacheWdata}
  cache.readwritePorts(0).mask.get := 0.U
  cache.readwritePorts(0).enable := false.B
  cache.readwritePorts(0).isWrite := false.B


  val cacheHits = cache.readwritePorts(0).readData.map {case (entry) => entry.valid && entry.tag === getCacheTag(pending_ax_bits.addr)}
  val cacheHit = VecInit(cacheHits).asUInt.orR
  val cacheHitIdx = PriorityEncoder(cacheHits)

  val directoryHits = directory.readwritePorts(0).readData.map {case (entry) => entry.valid && entry.tag === getDirectoryTag(pending_ax_bits.addr)}
  val directoryHit = VecInit(directoryHits).asUInt.orR
  val directoryHitIdx = PriorityEncoder(directoryHits)

  /**************************************************************************/
  /* State                                                                  */
  /**************************************************************************/
  

  // Steps:
  // Request acceptance
  // Response processing
  // Snooping sending
  // Snooping acceptance
  // If during any of the snoops there is writeback it is processed first
  

  val pending_chosen  = Reg(arb.io.chosen.cloneType)
  val pending_ax_bits = Reg(arb.io.out.bits.cloneType)


  // Separate the AW/AR
  // Prioritize writeback over the reads
  // 1. On request. Send read request to direcotry/cache
  // 2. If ReleaseData:
    // 2.1. Check if there is a free non dirty way then put it there in the cache array
    // 2.2. If there is all dirty ways, then start writeback of one of the ways
    // 2.3. If there is nondirty way then write to the cache array on that way
  // 3. If Release:
  //  3.1. Check the data array and update it if needed
  //  3.2. Check the direcotry and update it if needed
  //  3.3. Send the ReleaseAck
  // 4. If ReadUnique:
  //    4.1. Send snoop requests to the cores
  //    4.2. Obtain all snoop requests
  //    4.3. 


  // TODO: BURST: Add burst support
  
  /*

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
        io.up(pending_chosen).resp.bits.data := cache.readwritePorts(0).readData(cacheHitIdx).data
        io.up(pending_chosen).resp.bits.resp := SHARED_OKAY

      } .elsewhen(directoryHit) {
      } .otherwise {
        // All miss
      }
    }


    //addr := ax_bits.addr

    //directory.readwritePorts(0).readData

  }
  
  */

}

*/