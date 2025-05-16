package armleocpu

import chisel3._
import chisel3.util._

import chisel3.util._


import armleocpu.utils._


// L0 is gigapages
// L1 is megapages
// L2 is 4K pages

class TlbParams(
  val l0_sets:Int = 64,
  val l1_sets:Int = 16,
  val l2_sets:Int = 4,

  val l0_ways:Int = 4,
  val l1_ways:Int = 8,
  val l2_ways:Int = 16
) {
  require(isPositivePowerOfTwo(l0_sets))
  require(isPositivePowerOfTwo(l1_sets))
  require(isPositivePowerOfTwo(l2_sets))
  require(l2_sets <= l1_sets)
  require(l1_sets <= l0_sets)

  require(log2Ceil(l2_sets) <= 9)
  require(log2Ceil(l1_sets) <= 18)
  require(log2Ceil(l0_sets) <= 27)

  require(isPositivePowerOfTwo(l0_ways))
  require(isPositivePowerOfTwo(l1_ways))
  require(isPositivePowerOfTwo(l2_ways))
  require(l2_ways <= l1_ways)
  require(l1_ways <= l0_ways)
  require(2 <= l0_ways)
  require(2 <= l1_ways)
  require(2 <= l2_ways)
}

/**************************************************************************/
/* Input/Output Bundles                                                   */
/**************************************************************************/

object tlb_cmd extends ChiselEnum {
  val none, resolve, flush, write = Value
}


class tlb_accessbits_t extends Bundle {
  val dirty   = Bool()
  val access  = Bool()
  val global  = Bool()
  val user    = Bool()
  val execute = Bool()
  val write   = Bool()
  val read    = Bool()

  // Trying to read this entry resulted in accessfault
  val accessfault = Bool()

  // Trying to resolve this entry resulted in pagefault
  val pagefault = Bool()
}


// This bundle is kept in the memory,
//    while valid bit is kept in registers due to flush invalidating every entry

class tlb_entry_t(c: CoreParams, lvl: Int) extends tlb_accessbits_t {
  require(lvl <= 2)
  require(lvl >= 0)
  // The accessbits are defined in tlb_accessbits_t we extends
  val vpn =
    if(lvl == 2)        UInt(9.W)
    else if (lvl == 1)  UInt(18.W)
    else                UInt(24.W)
  
  val ppn = UInt(44.W) // We keep the 44 bits as it can be a pointer to a subtree
  
  def is_leaf(): Bool = read || execute
}

class tlb_wentry_t(c: CoreParams, lvl: Int) extends tlb_entry_t(c, lvl = lvl)  {
  val valid = Bool()
}

class tlb_result_t(c: CoreParams, lvl: Int) extends tlb_wentry_t(c, lvl = lvl)  {
  val hit = Bool()
}

class TLBIO(c: CoreParams) extends Bundle {
  // Command for TLB
  val s0 = new Bundle {
    val cmd       = Input(chiselTypeOf(tlb_cmd.write))
    val lvl       = Input(UInt(3.W))
    val vpn       = Input(UInt(39.W))
    val wentry = Input(new Bundle {
      val l0 = Input(new tlb_wentry_t(c, lvl = 0))
      val l1 = Input(new tlb_wentry_t(c, lvl = 1))
      val l2 = Input(new tlb_wentry_t(c, lvl = 2))
    })
  }

  // Output stage of TLB
  // Only valid for one cycle because it uses memory units,
  // which keep the output valid only one cycle
  val s1 = new Bundle {
    val l0    = Output(new tlb_result_t(c, lvl = 0))
    val l1    = Output(new tlb_result_t(c, lvl = 1))
    val l2    = Output(new tlb_result_t(c, lvl = 2))
  }
}


/**************************************************************************/
/* TLB Module                                                             */
/* ways/sets are not extracted from CoreParams,                           */
/* because it depends if it is a dtlb or itlb                             */
/**************************************************************************/

class TLB(verbose: Boolean = true, instName: String = "itlb ", c: CoreParams, tp: TlbParams) extends Module {
  
  val log = new Logger(c.lp.coreName, instName, verbose)

  /**************************************************************************/
  /* Input/Output                                                           */
  /**************************************************************************/
  val io = IO(new TLBIO(c))

  val s0 = io.s0
  val s1 = io.s1
  
  import tp._
  import tlb_cmd._

  // Registers inputs for use in second cycle, to compute hit logic
  val s1_vpn     = RegNext(s0.vpn)

  // Keeps track of victim
  val victim_reset   = (s0.cmd === flush)
  val (l0_victim, _) = Counter(0 until tp.l0_ways, enable = (s0_write && (s0.lvl === 0.U)), reset = victim_reset)
  val (l1_victim, _) = Counter(0 until tp.l1_ways, enable = (s0_write && (s0.lvl === 1.U)), reset = victim_reset)
  val (l2_victim, _) = Counter(0 until tp.l2_ways, enable = (s0_write && (s0.lvl === 2.U)), reset = victim_reset)

  /**************************************************************************/
  /* TLB Storage and state                                                  */
  /**************************************************************************/
  val l0_mem        = SyncReadMem (tp.l0_sets, Vec(tp.l0_ways, new tlb_entry_t(c, lvl = 0)))
  val l1_mem        = SyncReadMem (tp.l1_sets, Vec(tp.l1_ways, new tlb_entry_t(c, lvl = 1)))
  val l2_mem        = SyncReadMem (tp.l2_sets, Vec(tp.l2_ways, new tlb_entry_t(c, lvl = 2)))

  val l0_valid      = RegInit(VecInit.tabulate(tp.l0_sets) {setnum:Int => 0.U(tp.l0_ways.W)})
  val l1_valid      = RegInit(VecInit.tabulate(tp.l1_sets) {setnum:Int => 0.U(tp.l1_ways.W)})
  val l2_valid      = RegInit(VecInit.tabulate(tp.l2_sets) {setnum:Int => 0.U(tp.l2_ways.W)})
  
  /**************************************************************************/
  /* Command decoding                                                       */
  /**************************************************************************/
  // Command decoding. CMD is used to guarantee that only one cmd is executed at the same time
  val s0_resolve          = s0.cmd === resolve
  val s0_write            = s0.cmd === write

  /**************************************************************************/
  /* Decomposition the virtual address                                      */
  /**************************************************************************/
  val s0_vpn0   = s0.vpn(8, 0)
  val s0_vpn1   = s0.vpn(17, 9)
  val s0_vpn2   = s0.vpn(23, 18)


  val s0_l0_idx = s0.vpn(23, 0) % tp.l0_sets.U
  val s0_l1_idx = s0.vpn(23, 9) % tp.l1_sets.U
  val s0_l2_idx = s0.vpn(23, 18) % tp.l2_sets.U


  val l0_rdata = l0_mem.readWrite(
    /*idx = */s0_l0_idx,
    /*writeData = */VecInit.tabulate(tp.l2_ways) {way: Int => s0.wentry.l0.asTypeOf(new tlb_entry_t(c, lvl = 0))},
    /*mask = */(1.U << l0_victim).asBools,
    /*en = */s0_resolve || (s0_write && (s0.lvl === 0.U)),
    /*isWrite = */s0_write && (s0.lvl === 0.U)
  )

  val l1_rdata = l1_mem.readWrite(
    /*idx = */s0_l1_idx,
    /*writeData = */VecInit.tabulate(tp.l2_ways) {way: Int => s0.wentry.l1.asTypeOf(new tlb_entry_t(c, lvl = 1))},
    /*mask = */(1.U << l1_victim).asBools,
    /*en = */s0_resolve || (s0_write && (s0.lvl === 1.U)),
    /*isWrite = */s0_write && (s0.lvl === 1.U)
  )

  val l2_rdata = l2_mem.readWrite(
    /*idx = */s0_l2_idx,
    /*writeData = */VecInit.tabulate(tp.l2_ways) {way: Int => s0.wentry.l2.asTypeOf(new tlb_entry_t(c, lvl = 2))},
    /*mask = */(1.U << l2_victim).asBools,
    /*en = */s0_resolve || (s0_write && (s0.lvl === 2.U)),
    /*isWrite = */s0_write && (s0.lvl === 2.U)
  )

  /**************************************************************************/
  /* Write logic                                                            */
  /**************************************************************************/

  when(s0_write) {
    // TODO: Fix the write logic log
    //log("Write s0_index=0x%x, meta=0x%x, vtag=0x%x, ptag=0x%x", s0_index, s0.write_data.meta.asUInt, s0_vtag, s0.write_data.ptag)
    
  }

  /**************************************************************************/
  /* Invalidate logic                                                       */
  /**************************************************************************/

  when(s0.cmd === flush) {
    log("Flush\n")
    l0_valid := VecInit.tabulate(tp.l0_sets) {setnum:Int => 0.U(tp.l0_ways.W)}
    l1_valid := VecInit.tabulate(tp.l1_sets) {setnum:Int => 0.U(tp.l1_ways.W)}
    l2_valid := VecInit.tabulate(tp.l2_sets) {setnum:Int => 0.U(tp.l2_ways.W)}
  }

  /**************************************************************************/
  /* Output logic                                                           */
  /**************************************************************************/
  
  s1.l0 := l0_rdata(0)
  s1.l1 := l1_rdata(0)
  s1.l2 := l2_rdata(0)

  val s1_l0_idx = RegNext(s0_l0_idx)
  val s1_l1_idx = RegNext(s0_l1_idx)
  val s1_l2_idx = RegNext(s0_l2_idx)

  s1.l0 := l0_valid(s1_l0_idx)(0)
  s1.l1 := l1_valid(s1_l1_idx)(0)
  s1.l2 := l2_valid(s1_l2_idx)(0)
  
  s1.l0.hit := false.B
  s1.l1.hit := false.B
  s1.l2.hit := false.B
  
  for(i <- 0 until l0_ways) {
    when(l0_valid(s1_l0_idx)(i) && s1_vpn === l0_rdata(i).vpn) {
      s1.l0 := l0_rdata(i)
      s1.l0.hit := true.B
    }
  }

  for(i <- 0 until l1_ways) {
    when(l1_valid(s1_l1_idx)(i) && s1_vpn === l1_rdata(i).vpn) {
      s1.l1 := l1_rdata(i)
      s1.l1.hit := true.B
    }
  }
  
  
  for(i <- 0 until l2_ways) {
    when(l2_valid(s1_l2_idx)(i) && s1_vpn === l2_rdata(i).vpn) {
      s1.l2 := l2_rdata(i)
      s1.l2.hit := true.B
    }
  }
}
