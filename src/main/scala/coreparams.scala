package armleocpu


import chisel3._
import chisel3.util._

import armleocpu.utils._


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

class ArchParams(
  
) {
  val xLen: Int = 64
  val iLen: Int = 32
  val apLen: Int = 56
  val avLen: Int = 39
  val pagetableLevels: Int = 3


  val pgoff_len: Int = 12


  val vtag_len = avLen - pgoff_len
  val ptag_len = apLen - pgoff_len

  val xLen_log2 = log2Ceil(xLen)
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
  /*                Primary core parameters                                             */
  /**************************************************************************/
  val archParams: ArchParams = new ArchParams(),


  /**************************************************************************/
  /*                Memory subystem configuration                           */
  /**************************************************************************/
  

  val icache: CacheParams = new CacheParams(),
  val dcache: CacheParams = new CacheParams(),

  val bp: BusParams = new BusParams(),

  val itlb: TlbParams = new TlbParams(),
  val dtlb: TlbParams = new TlbParams(),
  
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
  /*
  val pmpCount: Int = 4,
  val pmpcfg_default: Seq[BigInt] = Seq(BigInt("11111111", 2)), // Allow all access, locked
  val pmpaddr_default: Seq[BigInt] = Seq(BigInt("FFFFFFFF", 16)), // For full memory range
  */
  // Debug options
  val lp: LoggerParams = new LoggerParams(),


  val ptw_verbose: Boolean = true,
  val core_verbose: Boolean = true,
  val fetch_verbose: Boolean = true,
  val itlb_verbose: Boolean = true,
  val dtlb_verbose: Boolean = true,
  val icache_verbose: Boolean = true,
  val dcache_verbose: Boolean = true,
  // TODO: Add verbose options for the rest of modules

  val rvfi_enabled: Boolean = false,
  val rvfi_dont_touch: Boolean = true
) {
  println("Generating using PMA Configuration default:")
  var regionnum = 0
  for(m <- pma_config) {
    println(f"Region $regionnum start: 0x${m.addrLow.toString(16)}, end: 0x${m.addrHigh.toString(16)}, memory: ${m.memory}")
    regionnum += 1
  }
  
  require( bp.data_bytes >= archParams.xLen / 8)


  require((reset_vector & BigInt("11", 2)) == 0)
  
}
