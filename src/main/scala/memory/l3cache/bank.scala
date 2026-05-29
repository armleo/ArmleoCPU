package armleocpu.l3cache

import chisel3._
import chisel3.util._
import chisel3.util.random._
import busConst._
import L3CacheBankState._
import addressUtils._
import armleocpu.l3cache._
import _root_.memory.l3cache.L3CacheVictimAvailability

class Bank(implicit ccx: CCXParams, implicit val cbp: CoherentBusParams) extends CCXModule {
  /**************************************************************************/
  /* Parameters                                                             */
  /**************************************************************************/
  // DIRECTORY: require(ccx.l3.directoryWaysLog2 >= 1)
  require(ccx.l3.cacheWaysLog2 >= 1)
  require(ccx.cacheLineBytes == cbp.busBytes) // We only support snoops the size of cache line

  /**************************************************************************/
  /* Inputs/Outputs                                                         */
  /**************************************************************************/
  val io = IO(new BankIO)

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


  /**************************************************************************/
  /* Submodules                                                             */
  /**************************************************************************/

  val awArb = Module(new RRArbiter(io.up(0).aw.bits.cloneType, ccx.coreCount))
  val arArb = Module(new RRArbiter(io.up(0).ar.bits.cloneType, ccx.coreCount))
  val dataArray = Module(new DataArray)
  val reseter = Module(new Reseter)
  val snoopRequest = Module(new SnoopRequest)
  val snoopResponse = Module(new SnoopResponse)
  val victimAvailability = Module(new VictimAvailability)
  val victimSelection = Module(new VictimSelection)

  /**************************************************************************/
  /* Victim keeping                                                         */
  /**************************************************************************/

  // TODO: DO connections
  victimAvailability.io.lookup.entries := dataArray.io.resp.entries

  /**************************************************************************/
  /* State                                                                  */
  /**************************************************************************/
  
  val state       = RegInit(init)



  /**************************************************************************/
  /* Default io states                                                      */
  /**************************************************************************/
  cacheReset.io.start := false.B

  /**************************************************************************/
  /* Reset                                                                  */
  /**************************************************************************/

  when(state === init) {
    cacheReset.io.start := !cacheReset.io.active
    victimCommand := cacheReset.io.victim

    when(cacheReset.io.start) {
      log("Reset started")
    }

    when(cacheReset.io.done) {
      state := idle
      log("Reset completed")
    }
  } .elsewhen(state === idle) {
    /**************************************************************************/
    /* Write requests have priority                                           */
    /**************************************************************************/
    when(awArb.io.out.valid) {
      addr := io.up(awArb.io.chosen).aw.bits.addr

      reading.chosen := awArb.io.chosen
      reading.addr := addr
      reading.op := io.up(awArb.io.chosen).aw.bits.op

      resolve := true.B
      state := rResponseAnalysis

      log(cf"Processing write from upstream ${arArb.io.chosen}")
    } .elsewhen(arArb.io.out.valid) {
      addr := io.up(arArb.io.chosen).ar.bits.addr

      reading.chosen  := arArb.io.chosen
      reading.addr    := addr
      reading.op      := io.up(arArb.io.chosen).ar.bits.op

      resolve := true.B
      state := rResponseAnalysis

      log(cf"Processing read from upstream ${arArb.io.chosen}")
    } .otherwise { // Voluntary eviction of dirty sections
      // TODO: Implement
      // returnState := idle
    }
  } .elsewhen(state == rResponseAnalysis) {
    // Cache array results are available

    when (!cacheHit) {
      // TODO: Check if the victim is valid and dirty then go to writeback.
      // TODO: Otherwise go to bus read request.
    } .otherwise {
      // TODO: If owned by current level, return it.
      // TODO: If owned by level above (e.g. L1 or L2) then send a 
    }
  } .elsewhen(state === wChooseVictim) {
    /**************************************************************************/
    /* choosing the victim to override.                                       */
    /**************************************************************************/
    when(victimAvailable) {
      /**************************************************************************/
      /* There is a way that is either non valid or non dirty. Choose it.       */
      /**************************************************************************/
      // TODO: Send the request to bus.
      

      state         := wRefillAfterEviction
      selectVictimWay := victimAvailableIdx
      log(cf"Victim selected 0x${victimAvailableIdx}%x")
    } .otherwise {
      /**************************************************************************/
      /* If we cannot find a free non dirty way,                                */
      /*  then start a write of the algorithmically selected victim             */
      /**************************************************************************/
      // TODO: Send the request to downsteam bus to writeback the victim.
      aw.bits.addr  := Cat(cacheRdata(victimWay).tag, getCacheEntryIdx(writeback.addr), 0.U(ccx.cacheLineLog2.W))
      aw.bits.op    := WriteOnce
      aw.valid      := true.B

      w.bits.data   := cacheRdata(victimWay).data
      w.valid       := true.B

      assert(aw.ready && w.ready)
      state         := wWaitB
      // TODO: Add return state either refill after eviction
      // Because it can be that we volunarily evicted some entires, so return state is idle
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
        resolve := true.B
        addr := writeback.addr
        directoryInvalidated := false.B
      }

      log(cf"B response recved")
    }
}
