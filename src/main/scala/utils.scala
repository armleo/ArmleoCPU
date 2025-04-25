package armleocpu


import scala.math._
import chisel3._
import chisel3.util._
import chisel3.util

object utils {
  def isPowerOfTwo(d: BigInt):Boolean = {
    return util.log2Up(d) == util.log2Down(d)
  }

  def isPositivePowerOfTwo(d: BigInt):Boolean = {
    return isPowerOfTwo(d) && (d >= 1)
  }
}


// TODO: Need to ignore the change if last cycle was valid && ready
object checkStableRecord {
  def apply[T <: Record](x: T): Unit = {
    require(x.elements.contains("valid"), s"${x} must have a 'valid' field")
    require(x.elements.contains("ready"), s"${x} must have a 'ready' field")

    val valid = x.elements("valid").asInstanceOf[Bool]
    val ready = x.elements("ready").asInstanceOf[Bool]

    // Define "payload" as any fields other than valid/ready
    val payloadFields = x.elements.view.filterKeys(k => k != "valid" && k != "ready").toMap

    // Registers to store previous payload and previous transaction status
    val prevPayload = payloadFields.map { case (name, data) =>
      name -> RegEnable(WireDefault(data), valid && !ready)
    }

    val prevValid = RegNext(valid, init = false.B)
    val prevReady = RegNext(ready, init = false.B)

    val transferLastCycle = prevValid && prevReady
    val holdPayload = valid && !ready && !transferLastCycle

    when(holdPayload) {
      for ((name, data) <- payloadFields) {
        val prev = prevPayload(name)
        assert(data === prev, s"$name changed while valid was high and ready was low")
      }
    }
  }
}