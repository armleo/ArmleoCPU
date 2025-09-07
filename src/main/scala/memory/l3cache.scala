package armleocpu

import chisel3._
import chisel3.util._
import chisel3.util.random._
import busConst._

/*
L3 Cache

This file implements a L3 Cache. This cache is implemented as eviction buffer.
It stores all evicted lines.
On request starts it writes the directory so that future requests know which upstream cache owns this line.
This way we are not poluting snoop bus for other cores.


To implement this l3 cache has two storages:
  Directory
  Data storage

When a request is satisfied, then directory is updated and data is forwarded to the requester L2.
When upstream cache evicts it is recorded into data storage.

When any round robin selects an upstream cache then the request from that cache is accepted
Then both the directory and data storage start a read request.

This cache needs to handle upstream writebacks
even in the middle of the read requests that are snooping cores.
During snoop request a writeback request may arrive because
the upstream cache holds an unique, dirty line.
In order to return a shared cache line it MAY writeback.

There is another edge case that this rule resolves:
  When master1 starts read. Master2 can start writeback of the same line.
  l3 does not yet see master2's request and starts processing master1's request.
  l3 sends snoops to the master2 but it is processing a writeback and cannot interrupt the writeback
  l3 then sees master2's write. l3 needs to stop the snooping process and process the writeback first.



L3 Cache is divided into banks.
Cache line is 64 bytes. Each 64 byte is stored in its respective bank
The L3 Cache wrapper uses address to forward requests and responses between banks

This is the level that also needs to handle non-caching masters like DMA.
So that L2 cache can instead handle peer cache snooping
L2 Cache is separate because it may be able to use coherence to access peers storage. While L3 is unable to do that.
*/

class L3CacheParams(
  val directoryEntriesLog2: Int = log2Ceil(8 * 1024),
  val cacheEntriesLog2: Int = log2Ceil(2 * 1024),
  val directoryWaysLog2: Int = 2,
  val cacheWaysLog2: Int = 2
  ) {
  
}



class L3CacheBank(implicit ccx: CCXParams, implicit val cbp: CoherentBusParams) extends CCXModule {
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
    val up = Vec(ccx.coreCount, Flipped(new CoherentBus()))
    val down = new ReadWriteBus()(cbp)
  })


  /**************************************************************************/
  /* Structures                                                             */
  /**************************************************************************/
  class DirectoryEntry(tagWidth: Int) extends Bundle {
    val tag = UInt(tagWidth.W)
    val valid = Bool()
    val dirty = Bool()
    val unique = Bool() // Set only if one of the sharers is set to unique
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

  val awQ   = Module(new Queue(gen = io.down.aw.bits.cloneType, entries = 1, pipe = true, flow = true))
  val wQ    = Module(new Queue(gen = io.down.w.bits.cloneType, entries = 1, pipe = true, flow = true))
  val arQ   = Module(new Queue(gen = io.down.ar.bits.cloneType, entries = 1, pipe = true, flow = true))
  
  // Upstream
  val rQ    = Module(new Queue(gen = new Bundle {
    val payload = io.up(0).r.bits.cloneType
    val idx = UInt(log2Ceil(ccx.coreCount).W)
  }, entries = 1, pipe = true, flow = true))



  val cache = SRAM.masked(1 << ccx.l3.cacheEntriesLog2,
    Vec(1 << ccx.l3.cacheWaysLog2,
    new CacheEntry(cbp.addrWidth - ccx.l3.cacheEntriesLog2 - ccx.cacheLineLog2)),
    0, 0, 1)
  val directory = SRAM.masked(1 << ccx.l3.directoryEntriesLog2,
    Vec(1 << ccx.l3.directoryWaysLog2,
    new DirectoryEntry(cbp.addrWidth - ccx.l3.directoryEntriesLog2 - ccx.cacheLineLog2)),
    0, 0, 1)
  
  /**************************************************************************/
  /* SRAM access                                                            */
  /**************************************************************************/

  val addr = Wire(UInt(cbp.addrWidth.W))
  val res  = WireDefault(false.B)
  val cacheWrite = WireDefault(false.B)
  val dirWrite = WireDefault(false.B)
  val incrementVictim = WireDefault(false.B)

  def getDirectoryEntryIdx(addr: UInt): UInt = {
    addr(ccx.l3.directoryEntriesLog2 + ccx.cacheLineLog2 - 1, ccx.cacheLineLog2)
  }
  def getCacheEntryIdx(addr: UInt): UInt = {
    addr(ccx.l3.cacheEntriesLog2 + ccx.cacheLineLog2 - 1, ccx.cacheLineLog2)
  }
  def getDirectoryTag(addr: UInt): UInt = {
    addr(cbp.addrWidth - 1, ccx.l3.directoryEntriesLog2 + ccx.cacheLineLog2)
  }
  def getCacheTag(addr: UInt): UInt = {
    addr(cbp.addrWidth - 1, ccx.l3.cacheEntriesLog2 + ccx.cacheLineLog2)
  }
  
  val directoryWdata = Wire(directory.readwritePorts(0).writeData(0).cloneType)

  directory.readwritePorts(0).address     := getDirectoryEntryIdx(addr)
  directory.readwritePorts(0).writeData   := VecInit.tabulate(1 << ccx.l3.directoryWaysLog2) {idx: Int => directoryWdata}
  directory.readwritePorts(0).mask.get.foreach(f => f := false.B)
  directory.readwritePorts(0).enable      := res || dirWrite
  directory.readwritePorts(0).isWrite     := dirWrite

  val cacheWdata = Wire(cache.readwritePorts(0).writeData(0).cloneType)

  cache.readwritePorts(0).address     := getCacheEntryIdx(addr)
  cache.readwritePorts(0).writeData   := VecInit.tabulate(1 << ccx.l3.cacheWaysLog2) {idx: Int => cacheWdata}
  cache.readwritePorts(0).mask.get.foreach(f => f := false.B)
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

  val unique = Wire(Bool())
  val sharer = Wire(UInt(ccx.coreCount.W))
  val forwarder = Wire(UInt(log2Ceil(ccx.coreCount).W))
  val dirty = Wire(Bool())
  val dirtyBit = WireDefault(0.U(8.W))
  val uniqueBit = WireDefault(0.U(8.W))

  when(cacheHit) {
    unique := cache.readwritePorts(0).readData(cacheHitIdx).unique
    sharer := cache.readwritePorts(0).readData(cacheHitIdx).sharer
    forwarder := cache.readwritePorts(0).readData(cacheHitIdx).forwarder
    dirty := cache.readwritePorts(0).readData(cacheHitIdx).dirty
  } .otherwise {
    unique := directory.readwritePorts(0).readData(directoryHitIdx).unique
    sharer := directory.readwritePorts(0).readData(directoryHitIdx).sharer
    forwarder := directory.readwritePorts(0).readData(directoryHitIdx).forwarder
    dirty := directory.readwritePorts(0).readData(directoryHitIdx).dirty
  }

  uniqueBit := (unique.asUInt << UNIQUEBITNUM)
  dirtyBit := (dirty.asUInt << DIRTYBITNUM)
  
  

  /**************************************************************************/
  /* Victim keeping                                                         */
  /**************************************************************************/

  val victimAvailables = VecInit(cache.readwritePorts(0).readData.map(f => !f.valid || f.valid && !f.dirty))
  val victimAvailable = victimAvailables.asUInt.orR
  val victimAvailableIdx = PriorityEncoder(victimAvailables)
  
  val (victimWay, _) = Counter(cond = incrementVictim, n = (1 << ccx.l3.cacheWaysLog2))

  val selectVictimWay = Reg(UInt(ccx.l3.cacheWaysLog2.W))

  /**************************************************************************/
  /* State                                                                  */
  /**************************************************************************/
  
  object State extends ChiselEnum { val init, idle,
    wChooseVictim, wRefillAfterEviction, wWaitB, // The writeback branch
    rResponseAnalysis, rSnoop, rSnoopReturn, rPushReq, rStorageUpdate, rWaitR // The read branch (can be interrupted to service writeback)
    = Value
  }

  val state       = RegInit(State.init)
  val returnState = RegInit(State.idle)
  val interruptedSnoop  = RegInit(false.B)

  class Request extends Bundle {
    val addr   = UInt(cbp.addrWidth.W)
    val chosen = UInt(log2Ceil(ccx.coreCount).W)
    val op     = UInt(8.W)
  }

  val pending     = RegInit(0.U.asTypeOf(new Request)) // Only used in writeback
  val reading     = RegInit(0.U.asTypeOf(new Request)) // Only used in read stages excluding snoop

  val snp_cores         = Reg(Vec(ccx.coreCount, Bool()))
  val snp_sent          = Reg(Vec(ccx.coreCount, Bool()))
  val snp_resp          = Reg(Vec(ccx.coreCount, Bool()))
  val snp_dataExpected  = Reg(Vec(ccx.coreCount, Bool()))
  val snp_dataRecved    = Reg(Vec(ccx.coreCount, Bool()))
  val snp_line          = RegInit(0.U((ccx.cacheLineBytes * 8).W))


  /**************************************************************************/
  /* Decoding                                                               */
  /**************************************************************************/
  
  val allSnoopsSent = (snp_sent.asUInt & snp_cores.asUInt) === snp_cores.asUInt
  val allSnoopsRespRecved = (snp_resp.asUInt & snp_cores.asUInt) === snp_cores.asUInt
  val allSnoopsDataRecved = ((snp_dataRecved.asUInt & snp_dataExpected.asUInt & snp_cores.asUInt) === (snp_dataExpected.asUInt & snp_cores.asUInt))
  
  val allSnoopsDone = (
    // All snoop requests sent
    allSnoopsSent &&
    // All snoop responses received
    allSnoopsRespRecved &&
    // For snoops where data is expected, data is received
    allSnoopsDataRecved
  )
  

  val readShared = reading.op === ReadShared
  val readUnique = reading.op === ReadUnique


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

  for (idx <- 0 until ccx.coreCount) {
    io.up(idx).creq.bits := DontCare
    io.up(idx).creq.valid := false.B
    io.up(idx).cdata.ready := false.B
    io.up(idx).cresp.ready := false.B

    io.up(idx).ar.ready := false.B
    io.up(idx).aw.ready := false.B

    io.up(idx).b.valid := false.B
    io.up(idx).b.bits := DontCare
    io.up(idx).b.bits.resp := OKAY
  }


  val aw = awQ.io.enq
  val w = wQ.io.enq
  val ar = arQ.io.enq
  val r = rQ.io.enq

  awQ.io.deq <> io.down.aw
  wQ.io.deq <> io.down.w
  arQ.io.deq <> io.down.ar


  r.bits.payload.last := true.B
  r.bits.payload.resp := OKAY

  when(rQ.io.deq.valid) {
    io.up(rQ.io.deq.bits.idx).r.bits := rQ.io.deq.bits.payload
    io.up(rQ.io.deq.bits.idx).r.valid := rQ.io.deq.valid
    rQ.io.deq.ready := io.up(rQ.io.deq.bits.idx).r.ready
  }
  
  when(r.valid)  {assert(r.ready)};
  when(aw.valid) {  assert(aw.ready)}; aw.bits := DontCare
  when( w.valid) {  assert(w.ready)};  w.bits  := DontCare; w.bits.last := true.B
  when(ar.valid) {  assert(ar.ready)}; ar.bits := DontCare


  

  def startWritebackRequest(interrupt: Bool): Unit = {
    res := true.B
    addr := io.up(awArb.io.chosen).aw.bits.addr

    pending.chosen  := awArb.io.chosen
    pending.addr    := addr
    pending.op      := io.up(awArb.io.chosen).aw.bits.op
    
    state := State.wChooseVictim

    interruptedSnoop := interrupt
  }

  def SnoopStart(
    includedCores: UInt = Fill(ccx.coreCount, 1.U(1.W)),
    excludedCores: UInt = Fill(ccx.coreCount, 0.U(1.W)),
  ): Unit = {
    state                   := State.rSnoop
    for (idx <- 0 until ccx.coreCount) {
      snp_sent(idx)         := false.B
      snp_dataRecved(idx)   := false.B
      snp_dataExpected(idx) := false.B
      snp_resp(idx)         := false.B
    }
    snp_line                := Fill(ccx.cacheLineBytes / 4, "hDEADBEEF".U)
    snp_cores               := (includedCores & ~excludedCores).asBools
  }


  def busRequest(addr: UInt): Unit = {
    ar.bits.addr  := addr
    ar.bits.op    := ReadOnce
    ar.valid      := true.B
    assert(ar.ready)
    state := State.rWaitR
  }

  /**************************************************************************/
  /* Reset                                                                  */
  /**************************************************************************/

  val resetCounterIncrement = WireDefault(false.B)
  val (resetCounter, resetCounterOverflow) = Counter(cond = resetCounterIncrement, Math.max(1 << ccx.l3.cacheEntriesLog2, 1 << ccx.l3.directoryEntriesLog2))
  
  when(state === State.init) {
    resetCounterIncrement := true.B
    dirWrite              := true.B
    cacheWrite            := true.B
    cacheWdata.valid      := false.B
    directoryWdata.valid  := false.B

    cache.readwritePorts(0).address     := resetCounter
    cache.readwritePorts(0).mask.get.foreach(f => f := true.B)
    directory.readwritePorts(0).address := resetCounter
    directory.readwritePorts(0).mask.get.foreach(f => f := true.B)
    when(resetCounterOverflow) {
      state := State.idle
      log("Reset completed")
    }
  } .elsewhen(state === State.idle) {
    /**************************************************************************/
    /* Write requests have priority                                           */
    /**************************************************************************/
    when(awArb.io.out.valid) {
      startWritebackRequest(interrupt = false.B)
      log(cf"Processing write from upstream ${arArb.io.chosen}")
    } .elsewhen(arArb.io.out.valid) {
      res := true.B
      addr := io.up(arArb.io.chosen).ar.bits.addr

      reading.chosen  := arArb.io.chosen
      reading.addr    := addr
      reading.op      := io.up(arArb.io.chosen).ar.bits.op
      
      state := State.rResponseAnalysis
      log(cf"Processing read from upstream ${arArb.io.chosen}")
    }

    interruptedSnoop := false.B
  } .elsewhen(state === State.wChooseVictim) {
    /**************************************************************************/
    /* choosing the victim to override.                                       */
    /**************************************************************************/
    when(victimAvailable) {
      /**************************************************************************/
      /* There is a way that is either non valid or non dirty. Choose it.       */
      /**************************************************************************/
      busRequest(addr = pending.addr)
      

      state         := State.wRefillAfterEviction
      selectVictimWay := victimAvailableIdx
      log(cf"Victim selected 0x${victimAvailableIdx}%x")
    } .otherwise {
      /**************************************************************************/
      /* If we cannot find a free non dirty way,                                */
      /*  then start a write of the algorithmically selected victim             */
      /**************************************************************************/
      aw.bits.addr  := Cat(cache.readwritePorts(0).readData(victimWay).tag, getCacheEntryIdx(pending.addr), 0.U(ccx.cacheLineLog2.W))
      aw.bits.op    := WriteOnce
      aw.valid      := true.B

      w.bits.data   := cache.readwritePorts(0).readData(victimWay).data
      w.valid       := true.B

      assert(aw.ready && w.ready)
      state         := State.wWaitB
      returnState   := State.wRefillAfterEviction
      selectVictimWay := victimWay
      log(cf"Writing dirty victim 0x${victimWay}%x")
    }
  } .elsewhen(state === State.wWaitB) {
    /**************************************************************************/
    /* Wait for B channel from downstream memory                              */
    /**************************************************************************/
    when(io.down.b.valid) {
      state           := returnState
      io.down.b.ready := true.B
      
      // All the memory behind cache HAS to return OKAY to writes
      
      assert(io.down.b.bits.resp(1, 0) === OKAY)

      when(returnState === State.wRefillAfterEviction) {
        // We need to start the request so it arrives at the same time as refillaftereviction is active
        ar.bits.addr  := pending.addr
        ar.bits.op    := ReadOnce
        ar.valid      := true.B
        assert(ar.ready)
      }

      log(cf"B response recved")
    }
  } .elsewhen(state === State.wRefillAfterEviction) {
    /**************************************************************************/
    /* Refill processing                                                      */
    /**************************************************************************/
    io.down.r.ready := true.B
    
    addr                              := pending.addr
    cacheWdata.data                   := io.down.r.bits.data
    cacheWdata.dirty                  := false.B
    cacheWdata.valid                  := true.B
    cacheWdata.forwarder              := 0.U // Nobody can forward
    cacheWdata.sharer                 := 0.U
    cacheWdata.tag                    := getCacheTag(addr)
    cache.readwritePorts(0).mask.get(selectVictimWay) := true.B

    when(io.down.r.valid) {
      cacheWrite                        := true.B
      when(interruptedSnoop) {
        state := State.rSnoop
      } .otherwise {
        state := State.idle
      }
      
      incrementVictim                   := true.B
      assert(io.down.r.bits.resp(1, 0) === OKAY)
      // TODO: L3 cache should properly handle requests that return error

      log(cf"Downstream R channel response recved")
    }
  } .elsewhen(state === State.rResponseAnalysis) {
    when (!directoryHit && !cacheHit) {
    /**************************************************************************/
    /* Cache and directory miss                                               */
    /**************************************************************************/
      SnoopStart()
      snp_cores(reading.chosen)  := false.B
      // Either readUnique or readShared. Regardless just forward it.
      log(cf"Cache and directory miss. Start snoop to upstream caches")
    } .otherwise {
      when(readShared) {
        when(cacheHit) {
            when(unique) {
              // Snoop the owner and tell it its now shared
              SnoopStart(includedCores = sharer)
              log(cf"readShared, cacheHit, unique")
            } .otherwise {
              // It is not unique and is owned by cache.
              // Can be safely returned without invalidation
              r.valid := true.B
              r.bits.idx := reading.chosen
              r.bits.payload.data := cache.readwritePorts(0).readData(cacheHitIdx).data
              r.bits.payload.resp := OKAY | dirtyBit | uniqueBit
              cacheWdata := cache.readwritePorts(0).readData(cacheHitIdx)
              cacheWdata.sharer := (1.U << reading.chosen) | cache.readwritePorts(0).readData(cacheHitIdx).sharer
              cacheWdata.forwarder := reading.chosen
              cacheWrite := true.B
              cache.readwritePorts(0).mask.get(cacheHitIdx) := true.B
              log(cf"readShared, cacheHit, shared")
            }
        } .elsewhen(directoryHit) {
          when(unique) {
            // Go and snoop the owner for data as it might be dirty
            SnoopStart(includedCores = sharer)
            log(cf"readShared, dirHit, unique")
          } .elsewhen (sharer.orR) {
            // Go ask forwarder for the data
            SnoopStart(includedCores = (0.U(ccx.coreCount.W)) | (1.U << forwarder))
            log(cf"readShared, dirHit, shared")
          } .otherwise {
            busRequest(reading.addr)
            // Start a bus request as 
            // it is required that if entry does not have any bits set
            // but exists then it is not owned by anybody
            log(cf"readShared, dirHit, no sharers")
          }
        }
      } .elsewhen(readUnique) {
        when(cacheHit) {
          when(!sharer.orR) {
            // Nobody owns it but the cache. Safe to return and update the entry
            r.valid := true.B
            r.bits.idx := reading.chosen
            r.bits.payload.data := cache.readwritePorts(0).readData(cacheHitIdx).data
            r.bits.payload.resp := OKAY | dirtyBit | uniqueBit
            cacheWdata := cache.readwritePorts(0).readData(cacheHitIdx)
            cacheWdata.sharer := (1.U << reading.chosen) | cache.readwritePorts(0).readData(cacheHitIdx).sharer
            cacheWdata.forwarder := reading.chosen
            cacheWrite := true.B
            cache.readwritePorts(0).mask.get(cacheHitIdx) := true.B
            log(cf"readUnique, cacheHit, no sharers")
          } .otherwise {
            // Go and snoop everybody who shares it to evict their data. Then return to the owner.
            SnoopStart(includedCores = sharer)
            log(cf"readUnique, cacheHit, sharers")
          }
        } .elsewhen(directoryHit) {
          when(!sharer.orR) {
            busRequest(reading.addr)
            // Start bus request
            log(cf"readUnique, dirHit, no sharers")
          } .elsewhen(sharer.orR && !unique) {
            SnoopStart(includedCores = sharer)
            // Not unique and read unique request
            // Go and snoop everybody to evict their data.
            // Then return to the owner.
            log(cf"readUnique, dirHit, owned and shared")
          } .otherwise {
            // Sharer set AND unique,
            // need to start snoop to get the possibly dirty data from owner
            SnoopStart(includedCores = sharer)
            log(cf"readUnique, dirHit, unqiue")
          }
        }
      }
    }
  } .elsewhen(state === State.rSnoop) {
    /**************************************************************************/
    /* Snoop processing                                                       */
    /**************************************************************************/
    when(awArb.io.out.valid) {
    /**************************************************************************/
    /* Writeback with active snoop                                            */
    /**************************************************************************/
      startWritebackRequest(interrupt = true.B)
      log(cf"Interrupted writeback")
    } .elsewhen(!allSnoopsDone) {
      for (idx <- 0 until ccx.coreCount) {
        /**************************************************************************/
        /* Snoop request                                                          */
        /**************************************************************************/
        when(!snp_sent(idx)) {
          io.up(idx).creq.valid := true.B
          // FIXME: Creq addres
          // FIXME: Creq op
        }
        when(io.up(idx).creq.valid && io.up(idx).creq.ready) {
          snp_sent(idx) := true.B
          log(cf"creq completed ${idx}")
        }

        /**************************************************************************/
        /* Snoop response                                                         */
        /**************************************************************************/
        when(snp_sent(idx)) {
          when(io.up(idx).cresp.valid) {
            snp_resp(idx) := true.B
            io.up(idx).cresp.ready := true.B
            snp_dataExpected := io.up(idx).cresp.bits.resp(RETURNDATABITNUM)

            // FIXME: Handle the dirty line
            assert(!io.up(idx).cresp.bits.resp(idx)(DIRTYBITNUM))

            log(cf"cresp completed ${idx}")
          }
        }

        /**************************************************************************/
        /* Snoop data                                                             */
        /**************************************************************************/
        when(!snp_dataRecved(idx) && snp_dataExpected(idx) && snp_resp(idx) && snp_sent(idx)) {
          when(io.up(idx).cdata.valid) {
            snp_dataRecved(idx) := true.B
            io.up(idx).cdata.ready := true.B
            snp_line := io.up(idx).cdata.bits.data
            log(cf"cdata completed ${idx}")
          }
        }
      }
    } .otherwise {
      /**************************************************************************/
      /* All the snoops are done                                                */
      /**************************************************************************/
      when(snp_dataRecved.asUInt.orR) {
        
        // Somebody returned data. Use that instead
        // If any data is recieved
        r.valid := true.B
        r.bits.idx := reading.chosen
        r.bits.payload.data := snp_line
        
        when(readUnique) {
          r.bits.payload.resp := OKAY | (1.U << UNIQUEBITNUM)
          
        } .elsewhen(readShared) {
          r.bits.payload.resp := OKAY
        }
        // FIXME: Handle the dirty line
        
        // There is not branch to this state from pending, only from reading
        res   := true.B
        addr  := reading.addr
        state := State.rStorageUpdate


        // TODO: Populate directory or cache
      } .otherwise {
      /**************************************************************************/
      /* No hits by snoop                                                       */
      /**************************************************************************/
        busRequest(reading.addr)
      }
    }
  } .elsewhen(state === State.rStorageUpdate) {
    // FIXME: Is read shared/ read unique valid?
    when(readShared) {
      
    }
  } .elsewhen(state === State.rWaitR) {
    // Wait for return from backing memory.
    // TODO: Populate the directory
  }
  

}

