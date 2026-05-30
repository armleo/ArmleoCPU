package armleocpu.memory.l3cache

import chisel3._
import chisel3.util._
import chisel3.util.random._
import armleocpu.busConst._
import BankState._
import addressUtils._
import armleocpu._

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

    io.up(idx).b.valid := false.B
    io.up(idx).b.bits := DontCare
    io.up(idx).b.bits.resp := OKAY


    awArb.io.in(idx) <> io.up(idx).aw
    arArb.io.in(idx) <> io.up(idx).ar

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
  /* Default submodule IO                                                   */
  /**************************************************************************/

  victimAvailability.io.lookup.entries := dataArray.io.resp.bits.rdata

  for (idx <- 0 until ccx.coreCount) {
    awArb.io.out.ready := false.B
    arArb.io.out.ready := false.B
  }



  /**************************************************************************/
  /* Victim keeping                                                         */
  /**************************************************************************/

  // TODO: Do connections
  


  

  /**************************************************************************/
  /* State                                                                  */
  /**************************************************************************/
  
  val state       = RegInit(init)
  val activeReq   = RegInit(0.U.asTypeOf(new Req))



  /**************************************************************************/
  /* Default io states                                                      */
  /**************************************************************************/
  reseter.io.start := false.B

  /**************************************************************************/
  /* Reset                                                                  */
  /**************************************************************************/

  when(state === init) {
    reseter.io.start := !reseter.io.active

    dataArray.io.req <> reseter.io.dataArrayReq
    victimSelection.io.command <> reseter.io.victimSelectionCommand
    activeReq := 0.U.asTypeOf(new Req)

    when(reseter.io.start) {
      log("Reset started")
    }

    when(reseter.io.done) {
      state := idle
      log("Reset completed")
    }
  } .elsewhen(state === idle) {
    /**************************************************************************/
    /* Write requests have priority                                           */
    /**************************************************************************/
    when(awArb.io.out.valid) {
      activeReq.core := awArb.io.chosen
      activeReq.addr := io.up(awArb.io.chosen).aw.bits.addr
      activeReq.op := io.up(awArb.io.chosen).aw.bits.op

      dataArray.io.req.valid := true.B
      dataArray.io.req.bits.addr := io.up(awArb.io.chosen).aw.bits.addr

      state := rResponseAnalysis

      log(cf"Processing write from upstream ${arArb.io.chosen}")
    } .elsewhen(arArb.io.out.valid) {
      activeReq.core    := arArb.io.chosen
      activeReq.addr    := io.up(arArb.io.chosen).ar.bits.addr
      activeReq.op      := io.up(arArb.io.chosen).ar.bits.op

      dataArray.io.req.valid := true.B
      dataArray.io.req.bits.addr := io.up(arArb.io.chosen).ar.bits.addr

      state := rResponseAnalysis

      log(cf"Processing read from upstream ${arArb.io.chosen}")
    } .otherwise { // Voluntary eviction of dirty sections
      state := evict
      // TODO: Implement
      // returnState := idle
    }
  } .elsewhen(state === rResponseAnalysis) {
    // Cache array results are available


    assert(dataArray.io.resp.valid)

    when (!dataArray.io.resp.bits.hit) {
      when(victimAvailability.io.result.available) {
        // TODO: If non dirty victim available, then select it.
      } .otherwise {
        // TODO: Otherwise go to bus read request.
        io.down.ar.bits.addr := activeReq.addr
        io.down.ar.bits.op   := 
      }
    } .otherwise {
      // TODO: If owned by current level, return it.
      // TODO: If owned by level above (e.g. L1 or L2) then send a 
    }
  } .elsewhen(state === wChooseVictim) {
    /**************************************************************************/
    /* choosing the victim to override.                                       */
    /**************************************************************************/
    
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
      // Because it can be that we volunarily evicted some entries, so return state is idle
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
}

