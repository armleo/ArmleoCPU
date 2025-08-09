package armleocpu

import chisel3._
import chisel3.util._
import chisel3.experimental.BundleLiterals._


import chisel3.simulator.VCDHackedEphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec


class PMPExerciserIO extends Bundle {
  val success = Output(Bool())
  val done = Output(Bool())
}

class PMPTestVec(ccx: CCXParams) extends Bundle {
  val addr = UInt(ccx.apLen.W)
  val priv = UInt(2.W)
  val op   = UInt(2.W)
  val mprv = Bool()
  val mpp  = UInt(2.W)
  val expected_fault = Bool()
}

class PMPExerciser(ccx: CCXParams) extends Module {
  val io = IO(new PMPExerciserIO)

  // Instantiate PMP
  val pmp = Module(new PMP(ccx))
  val csrRegs = Wire(new CsrRegsOutput(ccx))
  csrRegs := DontCare
  pmp.csrRegs := csrRegs

  // Test vector as VecInit of Bundles
  val tests = VecInit(Seq(
    // addr, priv, op, mprv, mpp, expected_fault
    (new PMPTestVec(ccx)).Lit(_.addr -> 0x1000.U, _.priv -> 3.U, _.op -> operation_type.load,    _.mprv -> false.B, _.mpp -> 3.U, _.expected_fault -> false.B),
    (new PMPTestVec(ccx)).Lit(_.addr -> 0x1000.U, _.priv -> 1.U, _.op -> operation_type.load,    _.mprv -> false.B, _.mpp -> 1.U, _.expected_fault -> true.B),
    (new PMPTestVec(ccx)).Lit(_.addr -> 0x2000.U, _.priv -> 1.U, _.op -> operation_type.load,    _.mprv -> false.B, _.mpp -> 1.U, _.expected_fault -> false.B),
    (new PMPTestVec(ccx)).Lit(_.addr -> 0x2000.U, _.priv -> 1.U, _.op -> operation_type.store,   _.mprv -> false.B, _.mpp -> 1.U, _.expected_fault -> true.B),
    (new PMPTestVec(ccx)).Lit(_.addr -> 0x2000.U, _.priv -> 1.U, _.op -> operation_type.execute, _.mprv -> false.B, _.mpp -> 1.U, _.expected_fault -> true.B),
    (new PMPTestVec(ccx)).Lit(_.addr -> 0x1000.U, _.priv -> 1.U, _.op -> operation_type.load,    _.mprv -> true.B,  _.mpp -> 3.U, _.expected_fault -> false.B),
    (new PMPTestVec(ccx)).Lit(_.addr -> 0x1000.U, _.priv -> 1.U, _.op -> operation_type.load,    _.mprv -> true.B,  _.mpp -> 1.U, _.expected_fault -> true.B)
  ))

  val numTests = tests.length
  val testIdx = RegInit(0.U(log2Ceil(numTests).W))
  val failed = RegInit(false.B)
  val finished = RegInit(false.B)

  // Default PMP config: all OFF
  for (i <- 0 until ccx.pmpCount) {
    csrRegs.pmp(i).pmpcfg.addressMatching := 0.U
    csrRegs.pmp(i).pmpcfg.read := false.B
    csrRegs.pmp(i).pmpcfg.write := false.B
    csrRegs.pmp(i).pmpcfg.execute := false.B
    csrRegs.pmp(i).pmpcfg.lock := false.B
    csrRegs.pmp(i).pmpaddr := 0.U
  }

  // Set up PMP entry for test 2-4 (region 0x2000, R permission, NAPOT)
  when(testIdx >= 2.U && testIdx <= 4.U) {
    csrRegs.pmp(0).pmpcfg.addressMatching := 3.U // NAPOT
    csrRegs.pmp(0).pmpcfg.read := true.B
    csrRegs.pmp(0).pmpcfg.write := false.B
    csrRegs.pmp(0).pmpcfg.execute := false.B
    csrRegs.pmp(0).pmpcfg.lock := false.B
    csrRegs.pmp(0).pmpaddr := "h800".U // NAPOT for 0x2000
  }

  // Drive PMP inputs and CSR fields from Vec
  val curTest = tests(testIdx)
  pmp.io.addr := curTest.addr
  pmp.csrRegs.privilege := curTest.priv
  pmp.io.operation_type := curTest.op
  csrRegs.mprv := curTest.mprv
  csrRegs.mpp := curTest.mpp
  csrRegs.privilege := curTest.priv // Current privilege

  // Check result
  val expected_fault = curTest.expected_fault
  when(!finished) {
    when(pmp.io.accessfault =/= expected_fault) {
      failed := true.B
      finished := true.B
    }.elsewhen(testIdx === (numTests-1).U) {
      finished := true.B
    }.otherwise {
      testIdx := testIdx + 1.U
    }
  }

  io.success := !failed && finished
  io.done := finished
}



class PmpTest extends AnyFlatSpec {
  it should "PmpTest" in {
    simulate("StressBusMux", new PMPExerciser(new CCXParams())) { harness =>
      for (i <- 0 to 200 * 5) {
        harness.clock.step(100)
        if (harness.io.done.peek().litValue == 1) {
          harness.io.success.expect(true.B)
        }
        println("100 cycles done")
      }
      
      harness.io.done.expect(true.B)
      harness.io.success.expect(true.B)
      

    }
  }
}
