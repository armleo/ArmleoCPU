package armleocpu


import chisel3._
import chisel3.util._


class DynamicROCsrRegisters(ccx: CCXParams) extends Bundle {
  val resetVector = UInt(ccx.apLen.W)
  val mtVector = UInt(ccx.apLen.W)
  val stVector = UInt(ccx.apLen.W)
  
  val mvendorid = UInt(ccx.xLen.W)
  val marchid = UInt(ccx.xLen.W)
  val mimpid = UInt(ccx.xLen.W)
  val mhartid = UInt(ccx.xLen.W)
  val mconfigptr = UInt(ccx.xLen.W)

  // FIXME:
  // assume(resetVector(1, 0) === 0.U)

  /*val reset_vector:   BigInt = BigInt("40000000", 16),
  val mtvec_default:  BigInt = BigInt("40002000", 16),
  val stvec_default:  BigInt = BigInt("40004000", 16),


  
  val mvendorid:  BigInt = BigInt("0A1AA1E0", 16),
  val marchid:    BigInt = BigInt(1),
  val mimpid:     BigInt = BigInt(1),
  val mhartid:    BigInt = BigInt(0),
  val mconfigptr: BigInt = BigInt("100", 16),*/
}


// CSR registers that are registered on the first cycle after reset
class StaticCsrRegisters(ccx: CCXParams) extends Bundle {
  // FIXME: Add PMP registers
  val pmpcfg_default = Vec(ccx.pmpCount, UInt(ccx.xLen.W))
  val pmpaddr_default = Vec(ccx.pmpCount, UInt(ccx.xLen.W))
  //val pmpcfg_default: Seq[BigInt] = Vec(BigInt("00011111", 2)), // Allow all access, unlocked, NAPOT addressing
  //val pmpaddr_default: Seq[BigInt] = Seq(BigInt("4FFFFFFFFFFFFF", 16)), // For full memory range
}



class CoreParams(
  // PMA/PMP config
  val pma_config: Seq[pma_config_t] = Seq(
    new pma_config_t(
      BigInt(0) << 34, // 0 -- 2^34
      BigInt(1) << 34,
      true
    ),
    new pma_config_t(
      BigInt(1) << 34, // 2^34 -- 2^35 peripheral bus
      (BigInt(1) << 35),
      false
    )
  ),
  
  val maxWriteBacks: Int = 8,
  /**************************************************************************/
  /*                Memory subystem configuration                           */
  /**************************************************************************/

  val icache: CacheParams = new CacheParams(),
  val dcache: CacheParams = new CacheParams(),

  val l2tlb: L2_TlbParams = new L2_TlbParams(),
) {
  println("Generating using PMA Configuration default:")
  var regionnum = 0
  for(m <- pma_config) {
    println(f"Region $regionnum start: 0x${m.addrLow.toString(16)}, end: 0x${m.addrHigh.toString(16)}, memory: ${m.memory}")
    regionnum += 1
  }
}


class CCXParams(
  val coreCount: Int = 4,
  val busBytes:Int = 32,
  val core: CoreParams = new CoreParams(),

  val pmpCount: Int = 1,
  
  val rvfi_enabled: Boolean = false,
  val rvfi_dont_touch: Boolean = true,
  val l3:L3CacheParams = new L3CacheParams,
) {
  

  val cacheLineLog2: Int = 6 // Fixed 64 bytes
  val xLen: Int = 64
  val iLen: Int = 32
  val apLen: Int = 56
  val avLen: Int = 39
  val pagetableLevels: Int = 3

  val xLenLog2 = log2Ceil(xLen)
  val xLenBytes = xLen / 8
  val xLenBytesLog2 = log2Ceil(xLenBytes)

  require(busBytes >= xLenBytes)
}


class CCXModule(ccx: CCXParams) extends Module {
  val cycle = RegInit(0.U(64.W)) // The cycle width does not matter as it is simulattion only
  cycle := cycle + 1.U
  

  def log(str: Printable): Unit = {
    printf(cf"[$cycle%x $name] ${str}\n")
  }
}

class CCX(ccx: CCXParams) extends CCXModule(ccx = ccx) {
  
}