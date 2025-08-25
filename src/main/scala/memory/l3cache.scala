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
    val sharer = UInt((ccx.coreCount).W)
    val forwarder = UInt(log2Ceil(ccx.coreCount).W) // Last L1 that requested this data
  }

  class CacheEntry(tagWidth: Int) extends DirectoryEntry(tagWidth = tagWidth) {
    val data = UInt(ccx.cacheLineBytes.W)
    require(ccx.cacheLineBytes == cbp.busBytes) // We only support snoops the size of cache line
  }


  /**************************************************************************/
  /* Submodules                                                             */
  /**************************************************************************/

  val awArb = Module(new RRArbiter(io.up(0).aw.bits.cloneType, ccx.coreCount))
  val arArb = Module(new RRArbiter(io.up(0).ar.bits.cloneType, ccx.coreCount))
  val awQ   = Module(new Queue(gen = io.down.aw.bits, entries = 1, pipe = true, flow = true))
  val wQ    = Module(new Queue(gen = io.down.w.bits, entries = 1, pipe = true, flow = true))
  val arQ   = Module(new Queue(gen = io.down.ar.bits, entries = 1, pipe = true, flow = true))


  val cache = SRAM(1 << ccx.l3.cacheEntriesLog2,
    Vec(1 << ccx.l3.cacheWaysLog2,
    new CacheEntry(cbp.addrWidth - ccx.l3.cacheEntriesLog2 - ccx.cacheLineLog2)),
    0, 0, 1)
  val directory = SRAM(1 << ccx.l3.directoryEntriesLog2,
    Vec(1 << ccx.l3.directoryWaysLog2,
    new DirectoryEntry(cbp.addrWidth - ccx.l3.directoryEntriesLog2 - ccx.cacheLineLog2)),
    0, 0, 1)
  

  // TODO: Do the connection for the AW/AR in
  /**************************************************************************/
  /* State                                                                  */
  /**************************************************************************/

  val addr = Wire(UInt(cbp.addrWidth.W))
  val res  = WireDefault(false.B)
  val cacheWrite = WireDefault(false.B)
  val dirWrite = WireDefault(false.B)

  def getDirectoryEntryIdx(addr: UInt): UInt = {
    addr(ccx.l3.directoryEntriesLog2 + ccx.cacheLineLog2 - 1, ccx.cacheLineLog2)
  }
  def getCacheEntryIdx(addr: UInt): UInt = {
    addr(ccx.l3.cacheEntriesLog2 + ccx.cacheLineLog2 - 1, ccx.cacheLineLog2)
  }
  def getDirectoryTag(addr: UInt): UInt = {
    addr(cbp.addrWidth, ccx.l3.directoryEntriesLog2 + ccx.cacheLineLog2)
  }
  def getCacheTag(addr: UInt): UInt = {
    addr(cbp.addrWidth, ccx.l3.cacheEntriesLog2 + ccx.cacheLineLog2)
  }
  
  val directoryWdata = Wire(directory.readwritePorts(0).writeData(0).cloneType)

  directory.readwritePorts(0).address     := getDirectoryEntryIdx(addr)
  directory.readwritePorts(0).writeData   := VecInit.tabulate(1 << ccx.l3.directoryWaysLog2) {idx: Int => directoryWdata}
  directory.readwritePorts(0).mask.get    := 0.U
  directory.readwritePorts(0).enable      := res || dirWrite
  directory.readwritePorts(0).isWrite     := dirWrite

  val cacheWdata = Wire(cache.readwritePorts(0).writeData(0).cloneType)

  cache.readwritePorts(0).address     := getCacheEntryIdx(addr)
  cache.readwritePorts(0).writeData   := VecInit.tabulate(1 << ccx.l3.cacheWaysLog2) {idx: Int => cacheWdata}
  cache.readwritePorts(0).mask.get    := 0.U
  cache.readwritePorts(0).enable      := res || cacheWrite
  cache.readwritePorts(0).isWrite     := cacheWrite


  /**************************************************************************/
  /* Read data decoding                                                     */
  /**************************************************************************/

  val cacheHits = cache.readwritePorts(0).readData.map {case (entry) => entry.valid && entry.tag === getCacheTag(addr)}
  val cacheHit = VecInit(cacheHits).asUInt.orR
  val cacheHitIdx = PriorityEncoder(cacheHits)

  val directoryHits = directory.readwritePorts(0).readData.map {case (entry) => entry.valid && entry.tag === getDirectoryTag(addr)}
  val directoryHit = VecInit(directoryHits).asUInt.orR
  val directoryHitIdx = PriorityEncoder(directoryHits)

  val sharer = Wire(UInt(ccx.coreCount.W))

  when(cacheHit) {
    sharer := cache.readwritePorts(0).readData(cacheHitIdx).sharer
  } .otherwise {
    sharer := directory.readwritePorts(0).readData(cacheHitIdx).sharer
  }

  val unique = (sharer & (sharer - 1.U)) === 0.U

  val victimAvailables = VecInit(cache.readwritePorts(0).readData.map(f => !f.valid || f.valid && !f.dirty))
  val victimAvailable = victimAvailables.asUInt.orR
  val victimAvailableIdx = PriorityEncoder(victimAvailables)

  /**************************************************************************/
  /* State                                                                  */
  /**************************************************************************/
  
  object State extends ChiselEnum { val init, idle, wChooseVictim, wVictimEvict, wPushReq, wWaitB, rResponseAnalysis, rSnoop, rReadReturn = Value }

  val state       = RegInit(State.idle)

  val pending_addr    = RegInit(0.U(cbp.addrWidth.W))
  val pending_chosen  = RegInit(UInt(log2Ceil(ccx.coreCount).W))
  val pending_op      = RegInit(UInt(8.W))


  val snp_sent          = RegInit(UInt(ccx.coreCount.W))
  val snp_resp          = RegInit(UInt(ccx.coreCount.W))
  val snp_dataExpected  = RegInit(UInt(ccx.coreCount.W))
  val snp_data          = RegInit(UInt(ccx.coreCount.W))
  val snp_line          = RegInit(UInt((ccx.cacheLineBytes * 8).W))


  val aw = awQ.io.enq
  val w = wQ.io.enq
  val ar = arQ.io.enq
  when(aw.valid) {  assert(aw.ready)}; aw.bits := DontCare
  when( w.valid) {  assert(w.ready)};  w.bits  := DontCare; w.bits.last := true.B
  when(ar.valid) {  assert(ar.ready)}; ar.bits := DontCare

  /**************************************************************************/
  /* Default IO                                                             */
  /**************************************************************************/
  
  io.down.ar.valid := false.B
  io.down.ar.bits  := 0.U.asTypeOf(io.down.ar.bits)
  io.down.r.ready  := true.B

  io.down.aw.valid := false.B
  io.down.aw.bits  := 0.U.asTypeOf(io.down.aw.bits)
  io.down.w.valid  := false.B
  io.down.w.bits   := 0.U.asTypeOf(io.down.w.bits)
  io.down.b.ready  := true.B




  when(state === State.init) {
    // TODO: Add the reseting
  } .elsewhen(state === State.idle) {
    when(awArb.io.out.valid) {
      res := true.B
      addr := io.up(awArb.io.chosen).aw.bits.addr

      pending_chosen  := awArb.io.chosen
      pending_addr    := io.up(awArb.io.chosen).aw.bits.addr
      pending_op      := io.up(awArb.io.chosen).aw.bits.op
      
      state := State.wChooseVictim
    } .elsewhen(arArb.io.out.valid) {
      res := true.B
      addr := io.up(arArb.io.chosen).ar.bits.addr

      pending_chosen  := arArb.io.chosen
      pending_addr    := addr
      pending_op      := io.up(awArb.io.chosen).ar.bits.op
      
      state := State.rResponseAnalysis
    }
  } .elsewhen(state === State.wChooseVictim) {
    // We are choosing the victim to override.
    when(victimAvailable) {
      ar.bits.addr  := pending_addr
      ar.bits.op    := ReadOnce
      ar.valid      := true.B
      state         := State.wRefillAfterEviction
    } .otherwise {
      state := State.wVictimEvict
      // If we cannot find a free non dirty way, then go to wVictimEvict so that we can evict that line
    }
  } .elsewhen(state === State.wRefillAfterEviction) {
    // TODO: Use the pending addr to write to backing cache array
  } .elsewhen(state === State.wVictimEvict) {
    // TODO: Wait for B response fo successful victim eviction
    // increment the victim
    returnState := State.wChooseVictim
  } .elsewhen(state === State.wWaitB) {
    // TODO: 
    // Wait for the backing memory to respond with B
    // Then go to returnState. Restart the request using the addr stored in pending_addr
  } .elsewhen(state === State.rResponseAnalysis) {
    // Two edge cases: ReadShared and ReadUnique.
    // Analyze the cache/directory to see if we can return response without asking any of the caches
    // If yes, then return the data.

    when (!directoryHit && !cacheHit) {
      // TODO: There is no point in checking the cache and directory if there are not hits. Proceed to snooping
    } .otherwise {
      when(readShared) {
        when(cacheHit) {
            when(unique) {
              // TODO: Snoop the owner and tell it its now shared
            } .otherwise {
              // TODO: Forward data as its not unique and is hit
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
            // TODO: Go and snoop everybody who shares it to evict their data. Then return to the owner.
        } .elsewhen(directoryHit) {
          when(!unique) {
            // TODO: Go and snoop everybody to evict their data. Then return to the owner.
          }
        } .otherwise {
          // Cannot see in the directory or the cache
          // TODO: Go to snoop and request everybody for the data and tell them to evict their data. Make sure directory is populated
        }
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
    // TODO: Populate the directory
  }
  

}

