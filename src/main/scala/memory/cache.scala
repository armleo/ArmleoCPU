package armleocpu

import chisel3._
import chisel3.util._

import chisel3.experimental.dataview._
import armleocpu.busConst._


class CacheParams(
  val waysLog2: Int  = 1,
  val entriesLog2: Int = 6,
  val l1tlbParams:AssociativeMemoryParameters = new AssociativeMemoryParameters(2, 2)
) {
  val ways = 1 << waysLog2
  val entries = 1 << entriesLog2
}

class CacheMeta(implicit val ccx: CCXParams, implicit val cp: CacheParams) extends Bundle {
  val valid       = Bool()
  // TODO: Writeback: Add the dirty, unique bits
  val ptag        = UInt((ccx.apLen - ccx.cacheLineLog2 - cp.entriesLog2).W)
}


class CacheReq(implicit val ccx: CCXParams) extends DecoupledIO(new Bundle {
    val read        = Bool() // Reads a data sample from the cache line
    val write       = Bool() // Writes a data sample to the cache line

    val atomicRead  = Bool()
    val atomicWrite = Bool()

    val vaddr       = UInt(ccx.apLen.W) // Virtual address or physical address for early resolves

  }) {

}

class CacheResp(implicit val ccx: CCXParams) extends Bundle {
  val read        = Input(Bool()) // Read command
  val write       = Input(Bool()) // Write command

  val atomicRead  = Input(Bool())
  val atomicWrite = Input(Bool())
  
  val valid               = Output(Bool()) // Previous operations result is valid
  val readData               = Output(Vec(ccx.xLenBytes, UInt(8.W))) // Read data from the cache

  val accessFault         = Output(Bool()) // Access fault, e.g. invalid address
  val pageFault           = Output(Bool()) // Page fault, e.g. invalid page

  val rvfiPtes           = Output(Vec(3, UInt(ccx.PTESIZE.W)))
  
  // FIXME: Return the TLB data so that core can make decision if access is allowed
  // FIXME: Return the TLB data so that it can be used to make requests on the PBUS

  // Write data command only
  val writeData         = Input(Vec(ccx.xLenBytes, UInt(8.W)))
  val writeMask         = Input(UInt(ccx.xLenBytes.W))
}


class Cache()(implicit ccx: CCXParams, implicit val cp: CacheParams) extends CCXModule {
  /**************************************************************************/
  /* Parameters and imports                                                 */
  /**************************************************************************/
  import cp._
  import CacheUtils._

  require(waysLog2 >= 1)
  require(ccx.cacheLineLog2 == 6) // 64 bytes per cache line
  require(ccx.cacheLineLog2 + entriesLog2 <= 12) // Make sure that 4K is maximum stored in early resolution as we dont have physical address yet

  val ways = 1 << waysLog2
  val entries = 1 << entriesLog2

  /**************************************************************************/
  /* Inputs/Outputs                                                         */
  /**************************************************************************/
  
  val ctrl        = IO(new PipelineControlIO)
  val csrRegs     = new CsrRegsOutput


  val req = IO(Flipped(new CacheReq))
  val resp = IO(new CacheResp)

  val bus = IO(new Bus)

  // TODO: PBUS: Add the peripheral bus for access that is not cached

  /*

  /*

  val l1tlb = Module(new AssociativeMemory(ccx = ccx,
    t = new tlb_entry_t(ccx, lvl = 2),
    sets = l1tlbParams.sets,
    ways = l1tlbParams.ways
  ))
  // FIXME: Add the PTE storage for RVFI

  // Keeps track of the all dirty lines so they can be written back asynchronously:
  //val writeBackQueue = Module(new Queue(UInt(entriesLog2.W), ccx.core.maxWriteBacks, useSyncReadMem = true))
  */


  /**************************************************************************/
  /*                                                                        */
  /* FSMs                                                                   */
  /*                                                                        */
  /**************************************************************************/
  val MAIN_IDLE = 0.U(4.W) // Idle state
  val MAIN_ACTIVE = 1.U(4.W) // Active state, executing the previous command
  val MAIN_REFILL = 2.U(4.W) // Refill state
  val MAIN_PTW = 3.U(4.W) // Page Table Walk state
  val MAIN_WRITE = 4.U(4.W)

  //val MAIN_WRITEBACK = 5.U(4.W) // Flush state
  //val MAIN_MAKE_UNIQUE = 6.U(4.W)
  //val MAIN_INVALIDATE = 7.U(4.W) // Invalidate state

  val mainState = RegInit(MAIN_IDLE) // Main state of the cache
  val mainReturnState = RegInit(MAIN_IDLE) // Keeps the return state.
  // As we might transition to REFILL/ACTIVE to load data from bus for the PTW
  

  /**************************************************************************/
  /* Combinational declarations without assigments                          */
  /**************************************************************************/

  val victimWayIdxIncrement = WireDefault(false.B) // Increment victim way index

  /**************************************************************************/
  /* State declarations without assigments                                  */
  /**************************************************************************/

  val resp_vaddr = Reg(req.bits.vaddr.cloneType)
  val resp_csrRegs = Reg(csrRegs.cloneType)

  val ax_cplt = RegInit(false.B)

  /**************************************************************************/
  /* IO default values                                                      */
  /**************************************************************************/

  // FIXME: README cannot be always asserted
  req.ready            := false.B

  resp.valid            := false.B // Default to not valid
  resp.accessFault      := false.B // Default to no access fault
  resp.pageFault        := false.B // Default to no page fault

  bus.ax.valid        := false.B
  bus.ax.bits         := 0.U.asTypeOf(bus.ax.bits.cloneType)
  bus.r.ready         := false.B


  // FIXME: bus.ar.bits.addr    := Cat(resp_paddr).asSInt

  /**************************************************************************/
  /*                                                                        */
  /* State with assignments                                                 */
  /*                                                                        */
  /**************************************************************************/

  val (victimWayIdx, _) = Counter(0 until (ways), enable = victimWayIdxIncrement) // FIXME: Add the enable condition

  /**************************************************************************/
  /* Combinational declarations with assigments                             */
  /**************************************************************************/

  // FIXME: Busy output
  ctrl.busy := mainState =/= MAIN_IDLE // Cache is busy if the main state is not idle
  
  //val (req_vm_enabled, req_vm_privilege) = csrRegs.getVmSignals()
  //val (resp_vm_enabled, resp_vm_privilege) = resp_csrRegs.getVmSignals()
  val req_vm_enabled = WireDefault(false.B)
  val resp_vm_enabled = WireDefault(false.B)

  // TODO: Above calculation as module

  /**************************************************************************/
  /* PTW                                                                    */
  /**************************************************************************/
  
  /*
  val metaWdata = Wire(new CacheMeta)
  metaWdata := DontCare

  meta.readwritePorts(0).address := getIdx(req.bits.vaddr)
  meta.readwritePorts(0).writeData := VecInit.tabulate(ways) {idx: Int => metaWdata} // TODO: Fix this
  meta.readwritePorts(0).enable := false.B
  meta.readwritePorts(0).isWrite := false.B
  meta.readwritePorts(0).mask.get := 0.U.asTypeOf(meta.readwritePorts(0).mask.get)
  


  data.readwritePorts(0).address := getIdx(req.bits.vaddr) // TODO: s2 for write or refill
  data.readwritePorts(0).writeData := VecInit.tabulate(1 << (waysLog2 + ccx.cacheLineLog2)) {idx: Int => 0.U(8.W)} // TODO: Fix this, should be write data from s0
  //data.readwritePorts(0).writeData := VecInit.tabulate(1 << (waysLog2 + ccx.cacheLineLog2)) {idx: Int => resp_writeData(idx % ccx.xLenBytes)}
  data.readwritePorts(0).mask.get := 0.U.asTypeOf(data.readwritePorts(0).mask.get)
  data.readwritePorts(0).enable := false.B
  data.readwritePorts(0).isWrite := false.B
  */

  /**************************************************************************/
  /* TLB                                                                    */
  /**************************************************************************/
  

  // The L1 tlb will only keep the 4K aligned pages.
  // If it crosses 4K page and forces an lookup on L2,
  // then it is perfectly fine as it gives two cycle latency max

  /*
  l1tlb.io.req.valid            := false.B
  l1tlb.io.req.op               := AssociativeMemoryOp.resolve
  l1tlb.io.req.idx              := req.bits.vaddr(req.bits.vaddr.getWidth - 1, 12)
  l1tlb.io.req.writeEntry       := 0.U.asTypeOf(l1tlb.io.req.writeEntry.cloneType)
  l1tlb.io.req.writeEntryValid  := false.B

  
  val tlbHits = l1tlb.io.resp.rentry.zip(l1tlb.io.resp.valid).map {case (entry, valid) => valid && entry.vpn === resp_vaddr(ccx.apLen-1, 12)}
  val tlbHit = VecInit(tlbHits).asUInt.orR
  val tlbHitIdx = PriorityEncoder(tlbHits)
  val l1tlbValid = l1tlb.io.resp.valid(tlbHitIdx)
  val l1tlbRentry = l1tlb.io.resp.rentry(tlbHitIdx)
  when(tlbHit) {
    assert((1.U << tlbHitIdx) === VecInit(tlbHits).asUInt, "TLB can only have one entry that matches")
  }
  */


  // FIXME:
  val resp_paddr = Cat(Mux(resp_vm_enabled, 0.U(16.W)/*l1tlbRentry.ppn*/, getPtag(resp_vaddr)), resp_vaddr(11, 0))
  val s2_paddr = Reg(resp_paddr.cloneType)

  val cacheHits = meta.readwritePorts(0).readData.zip(validreadData.asBools).map {case (entry, valid) => valid && entry.ptag === getPtag(resp_paddr)}
  val cacheHit = VecInit(cacheHits).asUInt.orR
  val cacheHitIdx = PriorityEncoder(cacheHits)

  
  when(cacheHit) {
    assert((1.U << cacheHitIdx) === VecInit(cacheHits).asUInt, "Cache can only have one entry that matches")
  }
  
  // TODO: The resp readData muxing
  resp.readData := VecInit(Seq.fill(ccx.xLenBytes)(0.U(8.W))) // Default to zero read data

  // is Core request is used to decide if we need to wait for the storage lock or not
  def storageReadRequest(vaddr: UInt, isCoreRequest: Boolean = true): Bool = {
    meta.readwritePorts(0).address := getIdx(vaddr)
    meta.readwritePorts(0).enable := true.B

    validreadData := valid(getIdx(vaddr))
    //dirtyreadData := dirty(getIdx(vaddr))

    data.readwritePorts(0).address := getIdx(vaddr)
    data.readwritePorts(0).enable := true.B

    //l1tlb.io.resolve := true.B
    //l1tlb.io.req.idx := getPtag(vaddr)

    true.B
  }


  val newRequestAllowed = WireDefault(false.B)


  
  when(mainState === MAIN_IDLE) {
    // In idle state, we can accept new requests
    newRequestAllowed := true.B
  } .elsewhen(mainState === MAIN_ACTIVE) {
    // If we writing then we cannot accept new requests
    // If it is a miss then we cannot accept new requests
    when(ctrl.kill) {
      log(cf"MAIN: Kill")
      // The operation was killed. No need to proceed.
    }.elsewhen(!false.B && resp_vm_enabled) { // FIXME: TLB HIT
      mainState := MAIN_PTW
      log(cf"MAIN: TLBMiss")
      assert(false.B, "TLB not implemented")
    } .elsewhen(false.B /*pageFault*/) { // PageFault
      log(cf"MAIN: PageFault")
    } .elsewhen(false.B /*pmp accessFault*/) {
      log(cf"MAIN: PMP accessFault")
    } .elsewhen(false.B /*pma not allowed access*/) {
      log(cf"MAIN: PMA accessFault")
    } .elsewhen(false.B /*!pma.memory*/) { // Not a cacheable location
      log(cf"MAIN: PMA marks this as non memory, therefore not cacheable")
    } .elsewhen(resp.read && !cacheHit) {
      log(cf"MAIN: CacheMiss")
      mainState := MAIN_REFILL
      s2_paddr := resp_paddr
      
    } .elsewhen(resp.write) {
      log(cf"MAIN: Write")
      mainState := MAIN_WRITE
      s2_paddr := resp_paddr
      // FIXME: Modify the cache line after write
    } .elsewhen(resp.read && cacheHit) {
      log(cf"MAIN: Hit")
      // Data array Hit, TLB hit, access allowed by PMA/PMP
    }
  } .elsewhen(mainState === MAIN_REFILL) {

    when(refill.io.cplt) {
      victimWayIdxIncrement := true.B
    }
    rdata := refill.readData
    
    // FIXME: Use the refill unit

    // Refill the cache. After refilling, we can accept new requests
    newRequestAllowed := false.B
  } .elsewhen(mainState === MAIN_WRITE) {

  } .elsewhen(mainState === MAIN_PTW) {
    // Page Table Walk state. We wont accept new requests as we may need to return the current one.
    newRequestAllowed := false.B
  }
  

  when(req.valid) {
    when(newRequestAllowed) {
      when(req.bits.read || req.bits.write) {
        // Read or write command
        
        req.ready := storageReadRequest(req.bits.vaddr)
        when(req.ready) {
          resp_vaddr := req.bits.vaddr
          mainState := MAIN_ACTIVE
          log(cf"START: vaddr=${req.bits.vaddr}%x")
        } .otherwise {
          mainState := MAIN_IDLE
          log(cf"CONGESTION: vaddr=${req.bits.vaddr}%x")
        }
      } .elsewhen (ctrl.flush) {
        valid := 0.U.asTypeOf(valid)
        // Flush the cache
        log(cf"FLUSH")
        //mainState := MAIN_WRITEBACK
      }
    }
  }

  resp.rvfiPtes := DontCare
  */

}


  // FIXME: 
  /*
  // Track how many cycles storageState stays in STORAGE_USED_BY_CORE when coherency bus has requests
  val storageUsedByCoreCounter = RegInit(0.U(2.W))
  val coherencyBusHasRequest = bus.ar.valid || bus.aw.valid // Add other coherency signals if needed

  when(storageState === STORAGE_USED_BY_CORE && coherencyBusHasRequest) {
    storageUsedByCoreCounter := storageUsedByCoreCounter + 1.U
  }.otherwise {
    storageUsedByCoreCounter := 0.U
  }

  // Assert: storageState cannot stay in USED_BY_CORE for more than 2 cycles if coherency bus has requests
  assert(!(storageUsedByCoreCounter === 2.U && storageState === STORAGE_USED_BY_CORE && coherencyBusHasRequest),
    "Cache storageState stayed in USED_BY_CORE for more than 2 cycles while coherency bus has requests!"
  )
  */




// TODO: Move to synthesis stage
import _root_.circt.stage.ChiselStage
import chisel3.stage.ChiselGeneratorAnnotation

object CacheGenerator extends App {
  implicit val ccx: CCXParams = new CCXParams
  implicit val cp: CacheParams = ccx.core.icache
  val chiselArgs =
    Array(
      "--target",
      "systemverilog",
      "--target-dir",
      "generated_vlog",
    )
  ChiselStage.emitSystemVerilogFile(new Cache, args=chiselArgs)
}
