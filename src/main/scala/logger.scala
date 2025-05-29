package armleocpu


import chisel3._
import chisel3.util._

import chisel3.util._
import chisel3.experimental.dataview._

import scala.io.AnsiColor._

// Logger: A class used to create logger objects inside of modules
// Purpose: Log to stdout the internal processes for debugging purposes
// TODO: Add a util for writing pipeline stage states to the file. See: https://www.gem5.org/documentation/general_docs/cpu_models/visualization/

class Logger(coreName: String, moduleName: String, enabled: Boolean) {
  val cycle = RegInit(0.U(16.W))
  cycle := cycle + 1.U

  def apply(str: Printable): Unit = {
    if(enabled) {
      printf(cf"[c:$cycle $coreName $moduleName] ${str}\n")
    }
  }
  def apply(fmt: String, data: Bits*):Unit = {
    if(enabled) {
      val newdata = cycle +: data
      
      printf(f"[c:%%d $coreName $moduleName] ${fmt}\n", newdata:_*)
    }
  }
}
