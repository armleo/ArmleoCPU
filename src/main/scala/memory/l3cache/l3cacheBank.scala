package armleocpu

import chisel3._
import chisel3.util._
import chisel3.util.random._
import busConst._
import L3CacheBankState._
import L3CacheUtils._



/*
L3 Cache

This file implements a L3 Cache. This cache is inclusive.

When any round robin selects an upstream cache then the request from that cache is accepted.
The request is moved in the pipeline:
S0 is the request start for the cache array read.
S1 is where address has been latched, no action
S2 is where the response from cache array has arrived.

This cache needs to handle upstream writebacks.
even in the middle of the read requests that are snooping cores.
During snoop request a writeback request may arrive because.
the upstream cache holds an unique, dirty line.

Cache is inclusive, so if upstream tries to do a writeback on evicted line then it is siletly absorbed.


L3 Cache is divided into banks.
Cache line is 64 bytes. Each 64 byte is stored in its respective bank.
The L3 Cache wrapper uses address to forward requests and responses between banks.
*/

class L3CacheBank(implicit ccx: CCXParams, implicit val cbp: CoherentBusParams) extends CCXModule {
  /**************************************************************************/
  /* Parameters                                                             */
  /**************************************************************************/
  // DIRECTORY: require(ccx.l3.directoryWaysLog2 >= 1)
  require(ccx.l3.cacheWaysLog2 >= 1)
  require(ccx.cacheLineBytes == cbp.busBytes) // We only support snoops the size of cache line

  /**************************************************************************/
  /* Inputs/Outputs                                                         */
  /**************************************************************************/
  val io = IO(new L3CacheBankIO)

  /**************************************************************************/
  /* Submodules                                                             */
  /**************************************************************************/

  val awArb = Module(new RRArbiter(io.up(0).aw.bits.cloneType, ccx.coreCount))
  val arArb = Module(new RRArbiter(io.up(0).ar.bits.cloneType, ccx.coreCount))

  // We dont need per-byte enable, as we will write entire 64 byte lines
  val cache = SRAM.masked(1 << ccx.l3.cacheEntriesLog2,
    Vec(1 << ccx.l3.cacheWaysLog2,
    new L3CacheEntry(cbp.addrWidth - ccx.l3.cacheEntriesLog2 - ccx.cacheLineLog2)),
    0, 0, 1)
  /**************************************************************************/
  /* SRAM access                                                            */
  /**************************************************************************/

  val s0_addr = WireDefault(0.U(cbp.addrWidth.W))
  val res  = WireDefault(false.B)
  val cacheWrite = WireDefault(false.B)
  val incrementVictim = WireDefault(false.B)

  val cacheWdata = Wire(cache.readwritePorts(0).writeData(0).cloneType)

  cache.readwritePorts(0).address     := getCacheEntryIdx(s0_addr)
  cache.readwritePorts(0).writeData   := VecInit.tabulate(1 << ccx.l3.cacheWaysLog2) {idx: Int => cacheWdata}
  cache.readwritePorts(0).mask.get.foreach(f => f := false.B)
  cache.readwritePorts(0).enable      := res || cacheWrite
  cache.readwritePorts(0).isWrite     := cacheWrite

  val s1_valid = RegNext(res, false.B)
  val s1_addr = RegEnable(s0_addr, res)

  /**************************************************************************/
  /* Read data decoding                                                     */
  /**************************************************************************/

  val cacheHits = cache.readwritePorts(0).readData.map {case (entry) => entry.valid && entry.tag === getCacheTag(s1_addr)}
  val cacheHit = VecInit(cacheHits).asUInt.orR
  val cacheHitIdx = PriorityEncoder(cacheHits)
  val cacheHitEntry = cache.readwritePorts(0).readData(cacheHitIdx)

  val unique = cacheHit && cacheHitEntry.unique
  val sharer = Mux(cacheHit, cacheHitEntry.sharer, 0.U(ccx.coreCount.W))
  val dirty = cacheHit && cacheHitEntry.dirty
  val respDirtyBit = (unique.asUInt << UNIQUEBITNUM)
  val respUniqueBit = (dirty.asUInt << DIRTYBITNUM)

  /**************************************************************************/
  /* Victim keeping                                                         */
  /**************************************************************************/

  val victimAvailability = Module(new L3CacheVictimAvailability)
  val victimKeeper = Module(new L3CacheVictimKeeper)
  val victimCommand = WireDefault(0.U.asTypeOf(new L3CacheVictimCommand))
  victimCommand.increment := incrementVictim

  victimAvailability.io.lookup.entries := cache.readwritePorts(0).readData
  victimKeeper.io.availability := victimAvailability.io.result
  victimKeeper.io.command := victimCommand

  val victimAvailable = victimKeeper.io.status.available
  val victimAvailableIdx = victimKeeper.io.status.availableIdx
  val victimWay = victimKeeper.io.status.victimWay

  val selectVictimWay = Reg(UInt(ccx.l3.cacheWaysLog2.W))

  /**************************************************************************/
  /* State                                                                  */
  /**************************************************************************/
  
  val state       = RegInit(init)

  val writeback     = RegInit(0.U.asTypeOf(new L3CacheRequest)) // Only used in writeback
  val reading     = RegInit(0.U.asTypeOf(new L3CacheRequest)) // Only used in read stages excluding snoop

  val snp_cores         = Reg(Vec(ccx.coreCount, Bool())) // Cores that need to be snooped
  val snp_sent          = Reg(Vec(ccx.coreCount, Bool())) // Cores that snoop request was sent
  val snp_resp          = Reg(Vec(ccx.coreCount, Bool())) // Cores that responded to snoop
  val snp_dataExpected  = Reg(Vec(ccx.coreCount, Bool())) // Cores that have data expected
  val snp_dataRecved    = Reg(Vec(ccx.coreCount, Bool())) // Cores that returned the data
  val snp_line          = RegInit(0.U((ccx.cacheLineBytes * 8).W)) // Data that was returned

  


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
  
  val readShared = WireDefault(false.B)
  val readUnique = WireDefault(false.B)
  readShared := (reading.op === ReadShared)
  readUnique := (reading.op === ReadUnique)


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


  /*

  def startWritebackRequest(interrupt: Bool): Unit = {
    res := true.B
    addr := io.up(awArb.io.chosen).aw.bits.addr

    writeback.chosen  := awArb.io.chosen
    writeback.addr    := addr
    writeback.op      := io.up(awArb.io.chosen).aw.bits.op
    
    state := wChooseVictim

    interruptedSnoop := interrupt
  }

  def SnoopStart(
    includedCores: UInt = Fill(ccx.coreCount, 1.U(1.W)),
    excludedCores: UInt = Fill(ccx.coreCount, 0.U(1.W)),
  ): Unit = {
    state                   := rSnoop
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
    state := rWaitR
  }*/

  /**************************************************************************/
  /* Reset                                                                  */
  /**************************************************************************/

  val resetCounterIncrement = WireDefault(false.B)
  val (resetCounter, resetCounterOverflow) = Counter(cond = resetCounterIncrement, Math.max(1 << ccx.l3.cacheEntriesLog2, 1 << ccx.l3.directoryEntriesLog2))
  
  when(state === init) {
    resetCounterIncrement := true.B
    cacheWrite            := true.B
    cacheWdata.valid      := false.B

    cache.readwritePorts(0).address     := resetCounter
    cache.readwritePorts(0).mask.get.foreach(f => f := true.B)

    when(resetCounterOverflow) {
      state := idle
      log("Reset completed")
    }
  } .elsewhen(state === idle) {
    /**************************************************************************/
    /* Write requests have priority                                           */
    /**************************************************************************/
    when(awArb.io.out.valid) {
      log(cf"Processing write from upstream ${arArb.io.chosen}")
      addr := io.up(awArb.io.chosen).aw.bits.addr

      reading.chosen := awArb.io.chosen
      reading.addr := addr
      reading.op := io.up(awArb.io.chosen).aw.bits.op
    } .elsewhen(arArb.io.out.valid) {
      addr := io.up(arArb.io.chosen).ar.bits.addr

      reading.chosen  := arArb.io.chosen
      reading.addr    := addr
      reading.op      := io.up(arArb.io.chosen).ar.bits.op

      log(cf"Processing read from upstream ${arArb.io.chosen}")
    }

    when(awArb.io.out.valid || arArb.io.out.valid) {
      res := true.B
      state := rResponseAnalysis
    }
  } .elsewhen(state == rResponseAnalysis) {
    // Cache array results are available

    when (!cacheHit) {
      // TODO: Check if the victim is valid and dirty then go to writeback.
      // TODO: Otherwise go to bus read request.
    } .otherwise {
      // TODO: If unique then go to snoop
      // TODO: If nobody ownes it, then return it.
    }



  } .elsewhen(state === wChooseVictim) {
    /**************************************************************************/
    /* choosing the victim to override.                                       */
    /**************************************************************************/
    when(victimAvailable) {
      /**************************************************************************/
      /* There is a way that is either non valid or non dirty. Choose it.       */
      /**************************************************************************/
      busRequest(addr = writeback.addr)
      

      state         := wRefillAfterEviction
      selectVictimWay := victimAvailableIdx
      log(cf"Victim selected 0x${victimAvailableIdx}%x")
    } .otherwise {
      /**************************************************************************/
      /* If we cannot find a free non dirty way,                                */
      /*  then start a write of the algorithmically selected victim             */
      /**************************************************************************/
      aw.bits.addr  := Cat(cache.readwritePorts(0).readData(victimWay).tag, getCacheEntryIdx(writeback.addr), 0.U(ccx.cacheLineLog2.W))
      aw.bits.op    := WriteOnce
      aw.valid      := true.B

      w.bits.data   := cache.readwritePorts(0).readData(victimWay).data
      w.valid       := true.B

      assert(aw.ready && w.ready)
      state         := wWaitB
      returnState   := wRefillAfterEviction
      selectVictimWay := victimWay
      log(cf"Writing dirty victim 0x${victimWay}%x")
    }
  } .elsewhen(state === wWaitB) {
    /**************************************************************************/
    /* Wait for B channel from downstream memory                              */
    /**************************************************************************/
    when(io.down.b.valid) {
      state           := returnState
      io.down.b.ready := true.B
      
      // All the memory behind cache HAS to return OKAY to writes
      
      assert(io.down.b.bits.resp(1, 0) === OKAY)

      when(returnState === wRefillAfterEviction) {
        // We need to start the request so it arrives at the same time as refillaftereviction is active
        ar.bits.addr  := writeback.addr
        ar.bits.op    := ReadOnce
        ar.valid      := true.B
        assert(ar.ready)

        // Request so we can make sure to invalidate directory
        res := true.B
        addr := writeback.addr
        directoryInvalidated := false.B
      }

      log(cf"B response recved")
    }
  } .elsewhen(state === wRefillAfterEviction) {
    /**************************************************************************/
    /* Refill processing                                                      */
    /**************************************************************************/
    


    io.down.r.ready := true.B
    
    res := true.B
    addr                              := writeback.addr
    cacheWdata                        := DontCare
    cacheWdata.data                   := io.down.r.bits.data
    cacheWdata.dirty                  := false.B
    cacheWdata.valid                  := true.B
    cacheWdata.forwarder              := 0.U // Nobody can forward
    cacheWdata.sharer                 := 0.U
    cacheWdata.tag                    := getCacheTag(addr)
    cache.readwritePorts(0).mask.get(selectVictimWay) := true.B

    /*
    DIRECTORY:
    directoryWdata := DontCare
    directoryWdata.valid := false.B
    directory.readwritePorts(0).mask.get(directoryHitIdx) := true.B

    directoryInvalidated := true.B
    dirWrite := !directoryInvalidated && directoryHit
    */

    when(io.down.r.valid) {
      // DIRECTORY: directoryInvalidated              := false.B

      cacheWrite                        := true.B
      when(interruptedSnoop) {
        state := rSnoop
      } .otherwise {
        state := idle
      }
      
      incrementVictim                   := true.B
      assert(io.down.r.bits.resp(1, 0) === OKAY)
      // TODO: L3 cache should properly handle requests that return error

      log(cf"Downstream R channel response recved")
    }
  } .elsewhen(state === rResponseAnalysis) {
    when (/*DIRECTORY: !directoryHit &&*/ !cacheHit) {
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
              r.bits.payload.resp := OKAY | respDirtyBit | respUniqueBit
              cacheWdata := cache.readwritePorts(0).readData(cacheHitIdx)
              cacheWdata.sharer := (1.U << reading.chosen) | cache.readwritePorts(0).readData(cacheHitIdx).sharer
              cacheWdata.forwarder := reading.chosen
              cacheWrite := true.B
              cache.readwritePorts(0).mask.get(cacheHitIdx) := true.B
              log(cf"readShared, cacheHit, shared")
            }
        } /*DIRECTORY: .elsewhen(directoryHit) {
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
        }*/
      } .elsewhen(readUnique) {
        when(cacheHit) {
          when(!sharer.orR) {
            // Nobody owns it but the cache. Safe to return and update the entry
            r.valid := true.B
            r.bits.idx := reading.chosen
            r.bits.payload.data := cache.readwritePorts(0).readData(cacheHitIdx).data
            r.bits.payload.resp := OKAY | respDirtyBit | respUniqueBit
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
        } /*DIRECTORY: .elsewhen(directoryHit) {
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
        }*/
      }
    }
  } .elsewhen(state === rSnoop) {
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
        
        // There is not branch to this state from writeback, only from reading
        res   := true.B
        addr  := reading.addr
        state := rStorageUpdate


        // TODO: Populate directory or cache
      } .otherwise {
      /**************************************************************************/
      /* No hits by snoop                                                       */
      /**************************************************************************/
        busRequest(reading.addr)
      }
    }
  } .elsewhen(state === rStorageUpdate) {
    /**************************************************************************/
    /* Update the backing storage according to snoop                          */
    /**************************************************************************/
    // FIXME: Is read shared/ read unique valid?
    when(readShared) {
      when(cacheHit) {
        // DIRECTORY: assert(!directoryHit)
        // Add the bit of the sharer
        cacheWdata := cache.readwritePorts(0).readData(cacheHitIdx)
        cacheWdata.sharer := cacheWdata.sharer | (1.U << reading.chosen)
        // Set the forwarder
        cacheWdata.forwarder := reading.chosen
        cacheWrite := true.B
        cache.readwritePorts(0).mask.get(cacheHitIdx) := true.B
      } /* DIRECTORY:  .elsewhen(directoryHit) {
        directoryWdata := directory.readwritePorts(0).readData(directoryHitIdx)
        // Add the bit of the sharer
        directoryWdata.sharer := directoryWdata.sharer | (1.U << reading.chosen)
        // Set the forwarder
        directoryWdata.forwarder := reading.chosen
        dirWrite := true.B
        directory.readwritePorts(0).mask.get(directoryHitIdx) := true.B
      } .otherwise {
        // Populate the directory with new entry for this line
        directoryWdata := 0.U.asTypeOf(directoryWdata)
        directoryWdata.valid := true.B
        directoryWdata.tag := getDirectoryTag(reading.addr)
        directoryWdata.sharer := (1.U << reading.chosen)
        directoryWdata.forwarder := reading.chosen
        directoryWdata.dirty := false.B
        directoryWdata.unique := false.B
        dirWrite := true.B
        // Choose a way to write (e.g., LRU or random)
        directory.readwritePorts(0).mask.get(0) := true.B // Replace 0 with victim way if needed
        // FIXME: Fix the mask to select the proper one
      }*/
    } .otherwise { // ReadUnique
      when(cacheHit) {
        // DIRECTORY: assert(!directoryHit)
        // FIXME: Remove all sharers
        // FIXME: Set unique
        // FIXME: Set the correct sharer
        // FIXME: Set the forwarder
      } /* DIRECTORY:  .elsewhen(directoryHit) {
        // FIXME: Remove all sharers
        // FIXME: Set unique
        // FIXME: Set the correct sharer
        // FIXME: Set the forwarder
        
      }*/ .otherwise {
        // FIXME: on randomly selected victim
        // FIXME: Remove all sharers on randomly selected victim
        // FIXME: Set unique
        // FIXME: Set the correct sharer
        // FIXME: Set the forwarder
        // FIXME: Populate the directory
      }
    }
    assert(readShared || readUnique)
  } .elsewhen(state === rWaitR) {
    /**************************************************************************/
    /* Response from bus                                                      */
    /**************************************************************************/
    // Wait for return from backing memory.
    // DIRECTORY: Populate the directory
  }
  

}

*/
