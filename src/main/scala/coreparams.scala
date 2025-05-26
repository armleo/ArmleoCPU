package armleocpu


import chisel3._
import chisel3.util._




/**************************************************************************/
/*                                                                        */
/*                CORE PARAMS                                             */
/*                                                                        */
/**************************************************************************/

class LoggerParams(
  val coreName: String = "core0", // Determines the name of Core
  val verboseCycleWidth: Int = 16 // Determines width of cycle counter for logging
) {

}

class CoreParams(

  /**************************************************************************/
  /*                Reset values and CSR ROs                                */
  /**************************************************************************/
  // Note: All addresses are SIGNED numbers. That is -4 is a valid value for reset_vector
  // They are not automatically sign extended. User need to do that manually

  val reset_vector:   BigInt = BigInt("40000000", 16),
  val mtvec_default:  BigInt = BigInt("40002000", 16),
  val stvec_default:  BigInt = BigInt("40004000", 16),

  val mvendorid:  BigInt = BigInt("0A1AA1E0", 16),
  val marchid:    BigInt = BigInt(1),
  val mimpid:     BigInt = BigInt(1),
  val mhartid:    BigInt = BigInt(0),
  val mconfigptr: BigInt = BigInt("100", 16),
  
  /**************************************************************************/
  /*                Memory subystem configuration                           */
  /**************************************************************************/
  
  val busBytes:Int = 32,

  val icache: CacheParams = new CacheParams(),
  val dcache: CacheParams = new CacheParams(),


  val itlb: L1_TlbParams = new L1_TlbParams(),
  val dtlb: L1_TlbParams = new L1_TlbParams(),

  val l2tlb: L2_TlbParams = new L2_TlbParams(),
  
  // PMA/PMP config
  val pma_config: Seq[pma_config_t] = Seq(
    new pma_config_t(
      BigInt(0) << 33,
      BigInt(1) << 33,
      true
    ), new pma_config_t(
      BigInt(1) << 33,
      (BigInt(1) << 34),
      false
    )
  ),
  
  val pmpCount: Int = 1,
  val pmpcfg_default: Seq[BigInt] = Seq(BigInt("00011111", 2)), // Allow all access, unlocked, NAPOT addressing
  val pmpaddr_default: Seq[BigInt] = Seq(BigInt("4FFFFFFFFFFFFF", 16)), // For full memory range
  
  // Debug options
  val lp: LoggerParams = new LoggerParams(),

  val ptw_verbose: Boolean = true,
  val core_verbose: Boolean = true,
  val fetch_verbose: Boolean = true,
  val itlb_verbose: Boolean = true,
  val dtlb_verbose: Boolean = true,
  val l2tlb_verbose: Boolean = true,
  val icache_verbose: Boolean = true,
  val dcache_verbose: Boolean = true,
  val dptw_verbose: Boolean = true,
  val iptw_verbose: Boolean = true,
  // TODO: Add verbose options for the rest of modules

  val rvfi_enabled: Boolean = false,
  val rvfi_dont_touch: Boolean = true
) {
  val xLen: Int = 64
  val iLen: Int = 32
  val apLen: Int = 56
  val avLen: Int = 39
  val pagetableLevels: Int = 3

  val xLen_log2 = log2Ceil(xLen)
  val xLen_bytes = xLen / 8


  println("Generating using PMA Configuration default:")
  var regionnum = 0
  for(m <- pma_config) {
    println(f"Region $regionnum start: 0x${m.addrLow.toString(16)}, end: 0x${m.addrHigh.toString(16)}, memory: ${m.memory}")
    regionnum += 1
  }
  
  require(busBytes >= xLen_bytes)


  require((reset_vector & BigInt("11", 2)) == 0)
  
}
