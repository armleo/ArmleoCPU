package armleocpu

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

/* 
class PageFaultSpec extends AnyFlatSpec {

  it should "Basic PageFault functionality test" in {
    simulate(new PageFault(new CoreParams())) { dut =>
      /*def test_case(dut: PageFault, fault: Boolean = true,
          cmd: Int = 1, privilege:Int = 3,
          mode: Int = 0,
          mprv: Boolean = false, mxr: Boolean = false, sum: Boolean = false, mpp: Int = 0,
          valid: Boolean = true, read: Boolean = true, write: Boolean = true, execute: Boolean = true,
          user: Boolean = false, access: Boolean = true, dirty: Boolean = true): Unit = {
        dut.cmd.poke(cmd)
        dut.csrRegs.privilege.poke(privilege)
        
        dut.csrRegs.mprv.poke(mprv)
        dut.csrRegs.mxr.poke(mxr)
        dut.csrRegs.sum.poke(sum)
        dut.csrRegs.mpp.poke(mpp)

        dut.tlb_result_t.valid.poke(valid)
        dut.tlb_result_t.read.poke(read)
        dut.tlb_result_t.write.poke(write)
        dut.tlb_result_t.execute.poke(execute)
        dut.tlb_result_t.user.poke(user)
        dut.tlb_result_t.access.poke(access)
        dut.tlb_result_t.dirty.poke(dirty)

        dut.clock.step(1)
        dut.fault.expect(fault)
      }
      */
      dut.cmd.poke(0)

      dut.csrRegs.privilege.poke(3)

      dut.csrRegs.mprv.poke(0)
      dut.csrRegs.mxr.poke(0)
      dut.csrRegs.sum.poke(0)
      dut.csrRegs.mpp.poke(0)
      
      dut.tlbdata.meta.valid.poke(0)
      dut.tlbdata.meta.read.poke(0)
      dut.tlbdata.meta.write.poke(0)
      dut.tlbdata.meta.execute.poke(0)
      dut.tlbdata.meta.user.poke(0)
      dut.tlbdata.meta.access.poke(0)
      dut.tlbdata.meta.dirty.poke(0)

      

      println("Test case machine mode no mprv")
      dut.csrRegs.privilege.poke(3)
      dut.csrRegs.mode.poke(0)
      dut.clock.step(1)
      dut.fault.expect(0)

      println("Test case machine mode no mprv, mode = 1")
      dut.csrRegs.mode.poke(1)
      dut.clock.step(1)
      dut.fault.expect(0)


      println("Test case supervisor mode no mprv, user page access")
      dut.tlbdata.meta.valid.poke(1)
      dut.tlbdata.meta.read.poke(1)
      dut.tlbdata.meta.write.poke(1)
      dut.tlbdata.meta.execute.poke(1)
      dut.tlbdata.meta.user.poke(1)
      dut.tlbdata.meta.access.poke(1)
      dut.tlbdata.meta.dirty.poke(1)
      dut.csrRegs.privilege.poke(1)
      dut.clock.step(1)
      dut.fault.expect(1)

      println("Test case supervisor mode no mprv, user page access")
      dut.tlbdata.meta.user.poke(0)
      dut.clock.step(1)
      dut.fault.expect(0)

      println("Executable test cases");
      println("Test case execute on unexecutable");
      dut.cmd.poke(3)
      dut.tlbdata.meta.execute.poke(0)
      dut.clock.step(1)
      dut.fault.expect(1)

      dut.tlbdata.meta.execute.poke(1)
      dut.clock.step(1)
      dut.fault.expect(0)

      

    }
  }
}
 */