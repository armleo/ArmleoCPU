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



class L3CacheBank(implicit val ccx: CCXParams, implicit val cbp: CoherentBusParams) extends Module {
  /**************************************************************************/
  /* Parameters                                                             */
  /**************************************************************************/
  require(ccx.l3.directoryWaysLog2 >= 1)
  require(ccx.l3.cacheWaysLog2 >= 1)
  require(ccx.cacheLineBytes == cbp.busBytes) // We only support snoops the size of cache line

  /**************************************************************************/
  /* Inputs/Outputs                                                         */
  /**************************************************************************/
  val io = IO(new Bundle {
    val up = Vec(ccx.coreCount, new CoherentBus())
    val down = new ReadWriteBus()(cbp)
  })

  /**************************************************************************/
  /* Structures                                                             */
  /**************************************************************************/
  class DirectoryEntry(tagWidth: Int) extends Bundle {
    val tag = UInt(tagWidth.W)
    val valid = Bool()
    val dirty = Bool()
    // We do not need to keep a separate bit for unique as we just check that sharer's only one bit is set
    val shared = UInt((ccx.coreCount).W)
    val forwarder = UInt(log2Ceil(ccx.coreCount).W) // Last L1 that requested this data
  }

  class CacheEntry(tagWidth: Int) extends DirectoryEntry(tagWidth = tagWidth) {
    val data = UInt(ccx.cacheLineBytes.W)
    require(ccx.cacheLineBytes == cbp.busBytes) // We only support snoops the size of cache line
  }


  /**************************************************************************/
  /* Submodules                                                             */
  /**************************************************************************/

  val awArb = Module(new RRArbiter(UInt(log2Ceil(ccx.coreCount).W), ccx.coreCount))

  val cache = SRAM(1 << ccx.l3.cacheEntriesLog2,
    Vec(1 << ccx.l3.cacheWaysLog2,
    new CacheEntry(ccx.apLen - ccx.l3.cacheEntriesLog2 - ccx.cacheLineLog2)),
    0, 0, 1)
  val directory = SRAM(1 << ccx.l3.directoryEntriesLog2,
    Vec(1 << ccx.l3.directoryWaysLog2,
    new DirectoryEntry(ccx.apLen - ccx.l3.directoryEntriesLog2 - ccx.cacheLineLog2)),
    0, 0, 1)
  
  /**************************************************************************/
  /* State                                                                  */
  /**************************************************************************/

  val addr = Wire(UInt(ccx.apLen.W))
  val req  = WireDefault(false.B)

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

  directory.readwritePorts(0).address     := getDirectoryEntryIdx(addr)
  directory.readwritePorts(0).writeData   := VecInit.tabulate(1 << ccx.l3.directoryWaysLog2) {idx: Int => directoryWdata}
  directory.readwritePorts(0).mask.get    := 0.U
  directory.readwritePorts(0).enable      := req
  directory.readwritePorts(0).isWrite     := false.B

  val cacheWdata = Wire(cache.readwritePorts(0).writeData(0).cloneType)

  cache.readwritePorts(0).address     := getCacheEntryIdx(addr)
  cache.readwritePorts(0).writeData   := VecInit.tabulate(1 << ccx.l3.cacheWaysLog2) {idx: Int => cacheWdata}
  cache.readwritePorts(0).mask.get    := 0.U
  cache.readwritePorts(0).enable      := req
  cache.readwritePorts(0).isWrite     := false.B

  val cacheHits = cacheMeta.readwritePorts(0).readData.map {case (entry) => entry.valid && entry.tag === getCacheTag(addr)}
  val cacheHit = VecInit(cacheHits).asUInt.orR
  val cacheHitIdx = PriorityEncoder(cacheHits)

  val directoryHits = directory.readwritePorts(0).readData.map {case (entry) => entry.valid && entry.tag === getDirectoryTag(addr)}
  val directoryHit = VecInit(directoryHits).asUInt.orR
  val directoryHitIdx = PriorityEncoder(directoryHits)

  /**************************************************************************/
  /* State                                                                  */
  /**************************************************************************/
  

  // ------------------------------
  // Dedicated Writeback FSM
  // ------------------------------
  object State extends ChiselEnum { val idle, wChooseVictim, wVictimEvict, wPushReq, wWaitB, rResponseAnalysis, rSnoop, rReadReturn = Value }

  val state       = RegInit(State.idle)

  // Active WB context (when L3 has an eviction or receives WriteBack)
  val wb_addr       = RegInit(0.U(ccx.apLen.W))
  val wb_buf        = RegInit(0.U((ccx.cacheLineBytes * 8).W)) // holds full line/beat to be written back
  val wb_buf_valid  = RegInit(false.B)

  // Snoops can be satisfied from active WB buffer if the address matches
  def sameLine(a: UInt, b: UInt): Bool = {
    // You can refine this with line masking by cacheLineLog2
    a(ccx.apLen-1, ccx.cacheLineLog2) === b(ccx.apLen-1, ccx.cacheLineLog2)
  }
  


  // ------------------------------
  // Default I/O
  // ------------------------------
  io.down.ar.valid := false.B
  io.down.ar.bits  := 0.U.asTypeOf(io.down.ar.bits)
  io.down.r.ready  := true.B

  io.down.aw.valid := false.B
  io.down.aw.bits  := 0.U.asTypeOf(io.down.aw.bits)
  io.down.w.valid  := false.B
  io.down.w.bits   := 0.U.asTypeOf(io.down.w.bits)
  io.down.b.ready  := true.B



  when(state === State.idle) {
    when(awArb.io.out.valid) {
      // New writeback/flush request
      // Compete to access the directory/cache array
      state := State.wChooseVictim
      wb_addr := io.up(awArb.io.chosen).aw.bits.addr
      wb_buf_valid := false.B
      // TODO: Record pending addr
      addr := io.up(awArb.io.chosen).aw.bits.addr
      req := true.B
    } .elsewhen(readRequest) {
      // TODO: Read the request address from backing memory/directory
      // TODO: Record pending addr
      state := State.rResponseAnalysis
    }
  } .elsewhen(state === State.wChooseVictim) {
    // We are choosing the victim to override.
    when(victimAvailable) {
      state := State.wVictimEvict
      // TODO: Populate the AW. Iterate over W until all beats are done
      // IMPORTANT: AW and W should be independedly processed. One cannot depend on another
    } .otherwise {
      // If we cannot find a free non dirty way, then go to wVictimEvict so that we can evict that line
    }
  } .elsewhen(state === State.wVictimEvict) {
    // We wanted to keep the data in data array but there was no empty way
    // Write the victim to the backing storage
    // increment the victim
    returnState := State.wChooseVictim
  } .elsewhen(state === State.wWaitB) {
    // Wait for the backing memory to respond with B
    // Then go to returnState. Restart the request using the addr stored in pending_addr
  } .elsewhen(state === State.rResponseAnalysis) {
    // Two edge cases: ReadShared and ReadUnique.
    // Analyze the cache/directory to see if we can return response without asking any of the caches
    // If yes, then return the data.

    when(readShared) {
      when(cacheHit) {
        when(unique) {
          // TODO: Go and snoop the owner for data as it might be dirty
        } .otherwise {
          // TODO: Return the data
        }
      } .elsewhen(directoryHit) {
        when(unique) {
          // TODO: Go and snoop the owner for data as it might be dirty
        } .elsewhen (sharer.orR) {
          // TODO: Go ask forwarder for the data
        } .otherwise {
          // TODO: Error: If no sharer then why is it in directory?
        }
      } .otherwise {
        // TODO: Snooop and check if any caches have it
        //    TODO: If yes then return it, otherwise go to backing memory
      }
    } .elsewhen(readUnique) {
      when(cacheHit) {
        when(unique) {
          // TODO: Go and snoop the owner for data as it might be dirty
        } .otherwise {
          // TODO: Go and snoop everybody who shares it to evict their data. Then return to the owner.
        }
      } .elsewhen(directoryHit) {
        when(unique) {
          // TODO: Go and snoop the owner for data as it might be dirty
        } .otherwise {
          // TODO: Go and snoop everybody to evict their data. Then return to the owner.
        }
      } .otherwise {
        // Cannot see in the directory or the cache
        // TODO: Go to snoop and request everybody for the data and tell them to evict their data. Make sure directory is populated
      }
    }
  } .elsewhen(state === State.rSnoop) {
    when(awArb.io.out.valid) {
      // TODO: Start the backArray request. Proceed to wChooseVictim, because we want to process the writeback first
      // TODO: Its okay to just stall the snoops
    } .elsewhen(allSnoopsDone) {
      // If we got all the snoops then decide:
      // Return if anybody has it
      // If nobody has it then go to bus request
    }
  
  } .elsewhen(state === State.rSnoopReturn) {
    // TODO: We successfully snooped all the masters and got a response. Return it on the R bus
  } .elsewhen(state === State.rPushReq) {
    // Read the backing memory for the data
    state := State.rWaitR
  } .elsewhen(state === State.rWaitR) {
    // Wait for return from backing memory.
  }
  

}

