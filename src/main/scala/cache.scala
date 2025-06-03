package armleocpu

import chisel3._
import chisel3.util._

import chisel3.experimental.dataview._


class CacheParams(
  val waysLog2: Int  = 1,
  val entriesLog2: Int = 6,
  val l1tlbParams:L1_TlbParams = new L1_TlbParams()
) {

}

class CacheMeta(ccx: CCXParams, cp: CacheParams) extends Bundle {
  import cp._
  val dirty       = Bool()
  val unique      = Bool()
  val ptag        = UInt((ccx.apLen - ccx.cacheLineLog2 - entriesLog2).W)
}




class Decomposition(ccx: CCXParams, cp: CacheParams, address: UInt) extends Bundle {
  import cp._

  val idx          =                              address(ccx.cacheLineLog2 + entriesLog2 - 1, ccx.cacheLineLog2)
  val ptag         =                              address(ccx.apLen - 1, ccx.cacheLineLog2 + entriesLog2)
  
  assert(Cat(ptag, idx, address(ccx.cacheLineLog2 - 1, 0)) === address)
}



class Cache(ccx: CCXParams, cp: CacheParams) extends CCXModule(ccx = ccx) {
  /**************************************************************************/
  /* Parameters and imports                                                 */
  /**************************************************************************/
  import cp._

  require(waysLog2 >= 1)
  require(ccx.cacheLineLog2 == 6) // 64 bytes per cache line
  require(ccx.cacheLineLog2 + entriesLog2 <= 12) // Make sure that 4K is maximum stored in early resolution as we dont have physical address yet
  
  /**************************************************************************/
  /* Inputs/Outputs                                                         */
  /**************************************************************************/
  
  val busy        = IO(Output(Bool())) // Is the cache busy with some operation

  val s0 = IO(Flipped(DecoupledIO(new Bundle {
    val read        = Bool() // Reads a data sample from the cache line
    val write       = Bool() // Writes a data sample to the cache line
    val flush       = Bool() // Flush the cache

    val vaddr       = UInt(ccx.apLen.W) // Virtual address or physical address for early resolves
    val csrRegs     = new CsrRegsOutput(ccx)
  })))

  // TODO: Make corebus isntead of dbus. For now we are using dbus

  val s1 = IO(new Bundle {
    val kill        = Input(Bool()) // Kill the current operation
    
    val read        = Input(Bool()) // Read command
    val write       = Input(Bool()) // Write command
    val flush       = Input(Bool()) // Flush the cache

    val valid               = Output(Bool()) // Previous operations result is valid
    val rdata               = Output(Vec(ccx.xLenBytes, UInt(8.W))) // Read data from the cache

    val accessfault         = Output(Bool()) // Access fault, e.g. invalid address
    val pagefault           = Output(Bool()) // Page fault, e.g. invalid page
    
    // FIXME: Return the TLB data so that core can make decision if access is allowed
    // FIXME: Return the TLB data so that it can be used to make requests on the PBUS

    // Write data command only
    val writeData         = Input(Vec(ccx.xLenBytes, UInt(8.W)))
    val writeMask         = Input(UInt(ccx.xLenBytes.W))
  })


  // TODO: Make corebus isntead of dbus. For now we are using dbus
  val corebus = IO(new dbus_t(ccx))




  /**************************************************************************/
  /*                                                                        */
  /* FSMs                                                                   */
  /*                                                                        */
  /**************************************************************************/
  val MAIN_IDLE = 0.U(3.W) // Idle state
  val MAIN_ACTIVE = 1.U(3.W) // Active state, executing the previous command
  val MAIN_FLUSH = 2.U(3.W) // Flush state
  val MAIN_REFILL = 3.U(3.W) // Refill state
  val MAIN_INVALIDATE = 4.U(3.W) // Invalidate state
  val MAIN_PTW = 5.U(3.W) // Page Table Walk state
  val mainState = RegInit(MAIN_FLUSH) // Main state of the cache
  val mainReturnState = RegInit(MAIN_IDLE) // Keeps the return state.
  // As we might transition to REFILL/ACTIVE to load data from bus for the PTW
  

  val PTW_IDLE = 0.U(3.W) // PTW idle state
  val PTW_ACTIVE = 1.U(3.W) // PTW active state, executing the previous command
  val ptwState = RegInit(PTW_IDLE) // PTW state of the cache

  when(mainState =/= MAIN_PTW) {
    assert(ptwState === PTW_IDLE, "PTW state should be idle when main state is not PTW")
  }
  when(ptwState =/= PTW_IDLE) {
    assert(mainState === MAIN_PTW, "Main state should be PTW when PTW state is not idle")
  }

  val REFILL_IDLE = 0.U(3.W) // Refill idle state
  val REFILL_ACTIVE = 1.U(3.W) // Refill active state, executing the previous command
  val refillState = RegInit(REFILL_IDLE) // Refill state of the cache

  when(mainState =/= MAIN_REFILL) {
    assert(refillState === REFILL_IDLE, "Refill state should be idle when main state is not REFILL")
  }
  when(refillState =/= REFILL_IDLE) {
    assert(mainState === MAIN_REFILL, "Main state should be REFILL when Refill state is not idle")
  }


  val WRITEBACK_IDLE = 0.U(3.W) // Writeback idle state
  val WRITEBACK_ACTIVE = 1.U(3.W) // Writeback active state, executing the previous command
  val writebackState = RegInit(WRITEBACK_IDLE) // Writeback state of the cache

  
  val BUS_IDLE = 0.U(3.W) // Bus idle state
  val BUS_USED_BY_REFILL = 1.U(3.W) // Bus used by refill state, e.g. waiting for the bus response
  val BUS_USED_BY_WRITEBACK = 2.U(3.W) // Bus used by writeback state, e.g. waiting for the bus response
  val busState = RegInit(BUS_IDLE) // Bus state of the cache
  // Bus state can be held as long as needed as it is independent of the coherency bus
  when(busState === BUS_USED_BY_REFILL) {
    assert(mainState === MAIN_REFILL, "Bus state should be REFILL when bus is used by refill")
    assert(refillState =/= REFILL_IDLE, "Refill state should not be idle when bus is used by refill")
  }


  val STORAGE_IDLE = 0.U(3.W) // Storage idle state
  val STORAGE_USED_BY_COHERENCY = 1.U(3.W) // Storage used by coherency state, e.g. waiting for the bus response
  val STORAGE_USED_BY_CORE = 2.U(3.W) // Storage used by core state, e.g. waiting for the core to write data
  val storageState = RegInit(STORAGE_IDLE) // Storage state of the cache
  // REQUIREMENT: Storage cannot be held for more than one cycle, to allow the coherency to access the storage
  // In order to achieve this, we will use the storageState to control the storage access
  // If request comes at the same cycle as cache coherency request then it is given a priority
  // Otherwise the core can access the storage.
  // If cache misses then it goes to refill. The refill will signal ready ONLY after the coherency releases the storage
  // This way we can ensure that the storage is not held for more than one cycle



  /**************************************************************************/
  /* Storage                                                                */
  /**************************************************************************/
  val valid     = RegInit(VecInit.tabulate( (1 << (entriesLog2)))      {idx: Int => 0.U((1 << (waysLog2)).W)})
  val validRdata = Reg(UInt((1 << (waysLog2)).W))

  val meta = SRAM.masked((1 << (entriesLog2)), Vec(1 << (waysLog2), new CacheMeta(ccx, cp)), 0, 0, 1)
  val data = SRAM.masked(1 << (entriesLog2), Vec(1 << (waysLog2 + ccx.cacheLineLog2), UInt(8.W)), 0, 0, 1)


  val l1tlb = Module(new AssociativeMemory(ccx = ccx,
    t = new tlb_entry_t(ccx, lvl = 2),
    sets = l1tlbParams.sets,
    ways = l1tlbParams.ways,
    flushLatency = l1tlbParams.flushLatency
  ))



  // FIXME: 
  /*
  // Track how many cycles storageState stays in STORAGE_USED_BY_CORE when coherency bus has requests
  val storageUsedByCoreCounter = RegInit(0.U(2.W))
  val coherencyBusHasRequest = corebus.ar.valid || corebus.aw.valid // Add other coherency signals if needed

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

  /**************************************************************************/
  /* Combinational declarations without assigments                          */
  /**************************************************************************/

  val victimWayIdxIncrement = WireDefault(false.B) // Increment victim way index

  /**************************************************************************/
  /* State declarations without assigments                                  */
  /**************************************************************************/

  val s1_vaddr = Reg(s0.bits.vaddr.cloneType)
  val s1_csrRegs = Reg(s0.bits.csrRegs.cloneType)

  /**************************************************************************/
  /* IO default values                                                      */
  /**************************************************************************/
  // FIXME: README cannot be always asserted
  s0.ready := false.B
  val s0_vdec = new Decomposition(ccx, cp, s0.bits.vaddr)
  

  s1.valid := false.B // Default to not valid
  s1.rdata := VecInit(Seq.fill(ccx.xLenBytes)(0.U(8.W))) // Default to zero read data
  s1.accessfault := false.B // Default to no access fault
  s1.pagefault := false.B // Default to no page fault

  corebus.ax.valid        := false.B
  // FIXME: corebus.ar.bits.addr    := Cat(s1_paddr).asSInt
  corebus.r.ready         := false.B
  
  corebus.ax.bits := 0.U.asTypeOf(corebus.ax.bits.cloneType)

  /**************************************************************************/
  /*                                                                        */
  /* State with assignments                                                 */
  /*                                                                        */
  /**************************************************************************/

  val (victimWayIdx, _) = Counter(0 until (1 << (waysLog2)), enable = victimWayIdxIncrement) // FIXME: Add the enable condition

  /**************************************************************************/
  /* Combinational declarations with assigments                             */
  /**************************************************************************/

  // FIXME: Busy output
  busy := mainState =/= MAIN_IDLE // Cache is busy if the main state is not idle
  
  val (s0_vm_enabled, s0_vm_privilege) = s0.bits.csrRegs.getVmSignals()
  val (s1_vm_enabled, s1_vm_privilege) = s1_csrRegs.getVmSignals()

  /**************************************************************************/
  /* PTW                                                                    */
  /**************************************************************************/
  
  val metaWdata = Wire(new CacheMeta(ccx, cp))
  metaWdata := DontCare

  meta.readwritePorts(0).address := s0_vdec.idx
  meta.readwritePorts(0).writeData := VecInit.tabulate(1 << (waysLog2)) {idx: Int => metaWdata} // TODO: Fix this
  meta.readwritePorts(0).enable := false.B
  meta.readwritePorts(0).isWrite := false.B
  meta.readwritePorts(0).mask.get := 0.U.asTypeOf(meta.readwritePorts(0).mask.get)
  

  data.readwritePorts(0).address := s0_vdec.idx // TODO: s2 for write or refill
  data.readwritePorts(0).writeData := VecInit.tabulate(1 << (waysLog2 + ccx.cacheLineLog2)) {idx: Int => 0.U(8.W)} // TODO: Fix this, should be write data from s0
  //data.readwritePorts(0).writeData := VecInit.tabulate(1 << (waysLog2 + ccx.cacheLineLog2)) {idx: Int => s1_writeData(idx % ccx.xLenBytes)}
  data.readwritePorts(0).mask.get := 0.U.asTypeOf(data.readwritePorts(0).mask.get)
  data.readwritePorts(0).enable := false.B
  data.readwritePorts(0).isWrite := false.B
  
  /**************************************************************************/
  /* TLB                                                                    */
  /**************************************************************************/
  

  // The L1 tlb will only keep the 4K aligned pages.
  // If it crosses 4K page and forces an lookup on L2,
  // then it is perfectly fine as it gives two cycle latency max

  l1tlb.io.resolve := false.B
  l1tlb.io.flush := false.B
  l1tlb.io.write := false.B
  l1tlb.io.s0.idx := s0.bits.vaddr(s0.bits.vaddr.getWidth - 1, 12)
  l1tlb.io.s0.valid := false.B
  l1tlb.io.s0.wentry := 0.U.asTypeOf(l1tlb.io.s0.wentry.cloneType)

  
  val tlbHits = l1tlb.io.s1.rentry.zip(l1tlb.io.s1.valid).map {case (entry, valid) => valid && entry.vpn === s1_vaddr(ccx.apLen-1, 12)}
  val tlbHit = VecInit(tlbHits).asUInt.orR
  val tlbHitIdx = PriorityEncoder(tlbHits)
  val l1tlbValid = l1tlb.io.s1.valid(tlbHitIdx)
  val l1tlbRentry = l1tlb.io.s1.rentry(tlbHitIdx)
  when(tlbHit) {
    assert((1.U << tlbHitIdx) === VecInit(tlbHits).asUInt, "TLB can only have one entry that matches")
  }

  val s1_vdec = new Decomposition(ccx, cp, s1_vaddr)
  val s1_paddr = Cat(Mux(s1_vm_enabled, l1tlbRentry.ppn, s1_vdec.ptag), s1_vaddr(11, 0))
  val s1_pdec = new Decomposition(ccx, cp, s1_paddr)

  val cacheHits = meta.readwritePorts(0).readData.zip(validRdata.asBools).map {case (entry, valid) => valid && entry.ptag === s1_pdec.ptag}
  val cacheHit = VecInit(cacheHits).asUInt.orR
  val cacheHitIdx = PriorityEncoder(cacheHits)

  
  when(cacheHit) {
    assert((1.U << cacheHitIdx) === VecInit(cacheHits).asUInt, "Cache can only have one entry that matches")
  }
  

  // is Core request is used to decide if we need to wait for the storage lock or not
  def storageReadRequest(vaddr: UInt, idx: UInt, isCoreRequest: Boolean = true): Bool = {
    meta.readwritePorts(0).address := idx
    meta.readwritePorts(0).enable := true.B

    validRdata := valid(idx)

    data.readwritePorts(0).address := idx
    data.readwritePorts(0).enable := true.B

    l1tlb.io.resolve := true.B
    l1tlb.io.s0.idx := vaddr(vaddr.getWidth - 1, 12)

    true.B
  }


  /*
  when(s0.write) {
    valid(s0_dec.idx)(Cat(s0.writepayload.wayIdxIn, s0_dec.lateIdx)) := s0.writepayload.valid
    log(s"Writing to way ${s0.writepayload.wayIdxIn} at ${s0_dec.idx} with mask ${s0.writepayload.mask} and data ${s0.writepayload.wdata} and meta ${s0.writepayload.meta}")
  }
  
  s1.response.rdata := VecInit.tabulate((1 << waysLog2)) {wayIdx:Int => data_rdata(Cat(wayIdx.U(waysLog2.W), s0_dec.lateIdx))}
  s1.response.meta := VecInit.tabulate((1 << waysLog2)) {wayIdx:Int => meta_rdata(Cat(wayIdx.U(waysLog2.W), s0_dec.lateIdx))}
  s1.response.valid := RegNext(VecInit.tabulate((1 << waysLog2)) {wayIdx:Int => valid(s0_dec.idx)(Cat(wayIdx.U(waysLog2.W), s0_dec.lateIdx))}, init = 0.U.asTypeOf(valid(0)))
  

  // val s2_killed             = RegInit(false.B)
  // val s2_arCplt             = RegInit(false.B)
  // val s2_refillErrors       = RegInit(false.B)

  when(s1_refillInProgress) {
    ibus.ar.valid := !ar_done
    
    when(ibus.ar.ready) {
      ar_done := true.B
    }
  }
  */

  val newRequestAllowed = WireDefault(false.B)
  when(mainState === MAIN_IDLE) {
    // In idle state, we can accept new requests
    newRequestAllowed := true.B
  } .elsewhen(mainState === MAIN_ACTIVE) {
    // If we writing then we cannot accept new requests
    // If it is a miss then we cannot accept new requests
    when(s1.kill) {
      log(cf"MAIN: Kill")
      // The operation was killed. No need to proceed.
    }.elsewhen(!tlbHit && s1_vm_enabled) {
      mainState := MAIN_PTW
      log(cf"MAIN: TLBMiss")
    } .elsewhen(false.B /*pagefault*/) { // Pagefault
      log(cf"MAIN: Pagefault")
    } .elsewhen(false.B /*pmp accessfault*/) {
      log(cf"MAIN: PMP accessfault")
    } .elsewhen(false.B /*pma not allowed access*/) {
      log(cf"MAIN: PMA accessfault")
    } .elsewhen(false.B /*!pma.memory*/) { // Not a cacheable location
      log(cf"MAIN: PMA marks this as non memory, therefore not cacheable")
    } .elsewhen(!cacheHit) {
      log(cf"MAIN: CacheMiss")
      mainState := MAIN_REFILL
    } .otherwise {
      log(cf"MAIN: Hit")
      // Data array Hit, TLB hit, access allowed by PMA/PMP
    }
    /* .otherwise {
      
      when(mainReturnState === MAIN_IDLE && !s1.write) {
        newRequestAllowed := true.B
      }
    }*/
  } .elsewhen(mainState === MAIN_FLUSH) {
    // Flush state. After flush we can accept new requests
    newRequestAllowed := false.B
  } .elsewhen(mainState === MAIN_REFILL) {
    // Refill the cache. After refilling, we can accept new requests
    newRequestAllowed := false.B
  } .elsewhen(mainState === MAIN_PTW) {
    // Page Table Walk state. We wont accept new requests as we may need to return the current one.
    newRequestAllowed := false.B
  }


  when(s0.valid) {
    when(newRequestAllowed) {
      when(s0.bits.read || s0.bits.write) {
        // Read or write command
        
        s0.ready := storageReadRequest(s0.bits.vaddr, s0_vdec.idx)
        when(s0.ready) {
          s1_vaddr := s0.bits.vaddr
          mainState := MAIN_ACTIVE
          log(cf"START: vaddr=${s0.bits.vaddr}%x\n")
        } .otherwise {
          mainState := MAIN_IDLE
          log(cf"CONGESTION: vaddr=${s0.bits.vaddr}%x\n")
        }
      } .elsewhen (s0.bits.flush) {
        // Flush the cache
        log(cf"FLUSH\n")
        mainState := MAIN_FLUSH
      }
    }
  }

  


}

import _root_.circt.stage.ChiselStage
import chisel3.stage.ChiselGeneratorAnnotation



object CacheGenerator extends App {
  val chiselArgs =
    Array(
      "--target",
      "systemverilog",
      "--target-dir",
      "generated_vlog",
    )
    val ccx = new CCXParams
  ChiselStage.emitSystemVerilogFile(new Cache(ccx = ccx, cp = ccx.core.icache), args=chiselArgs)
}
