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
  val xLen: Int = 32,
) {
  val iLen: Int = 32
  val apLen: Int = 34
  val avLen: Int = xLen
  val pagetable_levels: Int = 2


  val pgoff_len: Int = 12


  val vtag_len = avLen - pgoff_len
  val ptag_len = apLen - pgoff_len

  val xLen_log2 = log2Ceil(xLen)

  require(xLen == 32) // TODO: RV64 replace
}

class CoreParams(
  /**************************************************************************/
  /*                Primary core parameters                                             */
  /**************************************************************************/
  val arch: ArchParams = new ArchParams(),

  /**************************************************************************/
  /*                Reset values and CSR ROs                                */
  /**************************************************************************/
  
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
  

  val icache:CacheParams = new CacheParams(),
  val dcache:CacheParams = new CacheParams(),

  val BusParams:BusParams = new BusParams(),

  val itlb:TlbParams = new TlbParams(),
  val dtlb:TlbParams = new TlbParams(),
  
  // PMA/PMP config
  val pma_config_default: Seq[pma_config_default_t] = Seq(
    new pma_config_default_t(
      BigInt(0) << 33,
      BigInt(1) << 33,
      true
    ), new pma_config_default_t(
      BigInt(1) << 33,
      (BigInt(1) << 34) - 1,
      false
    )
  ),
  /*
  val pmpcfg_default: Seq[Int],
  val pmpaddr_default: Seq[Int]*/

  // Debug options
  val lp: LoggerParams = new LoggerParams(coreName = f"core0", verboseCycleWidth = 16),


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
  for(m <- pma_config_default) {
    println(f"Region $regionnum start: 0x${m.addr_low.toString(16)}, end: 0x${m.addr_high.toString(16)}, cacheable: ${m.cacheable}")
    regionnum += 1
  }
  
  // TODO: RV64 In the future, replace with 64 version

  // Make sure it is power of two
  require( bus_data_bytes >= 1)
  require(isPowerOfTwo(bus_data_bytes))
  require( bus_data_bytes <= icache_entry_bytes * 2)
  require( bus_data_bytes <= dcache.entry_bytes * 2)

  require( bus_data_bytes >= xLen / 8)

  // bus_data_bytes used to be separate between Ibus and Dbus.
  // However, it would complicate PTW's bus connection and parametrization, so the idea was scrapped

  require((reset_vector & BigInt("11", 2)) == 0)

  def checkPositivePowerOfTwoParam(p: Int) = {
      require(p >= 1)
      require(isPowerOfTwo(p))
  }
  checkCacheTlbParam(icache_ways)
  checkCacheTlbParam(icache_entries)
  checkCacheTlbParam(icache_entry_bytes)

  checkCacheTlbParam(itlb_entries)
  checkCacheTlbParam(itlb_ways)

  checkCacheTlbParam(dcache.ways)
  checkCacheTlbParam(dcache.entries)
  checkCacheTlbParam(dcache.entry_bytes)
  
  checkCacheTlbParam(dtlb_entries)
  checkCacheTlbParam(dtlb_ways)

  
  // If it gets bigger than 4096 bytes, then it goes out of page boundry
  // This means that TLB has to be resolved before cache request is sent
  require(icache_entries * icache_entry_bytes <= 4096)
  require(dcache.entries * dcache.entry_bytes <= 4096)

  require(pagetable_levels == 2)
  // TODO: RV64 extend to have 3/4 layers

  
}
