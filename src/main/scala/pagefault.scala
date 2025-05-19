package armleocpu

import chisel3._
import chisel3.util._


import chisel3.util._

object pagefault_cmd extends ChiselEnum {
  val none, load, store, execute = Value
}

class Pagefault(
  // TODO: Add pagefault logging;
  // verbose: Boolean = true, instName: String = "iptw ",
  val c: CoreParams,
) extends Module {
  /**************************************************************************/
  /*Input/Output                                                            */
  /**************************************************************************/

  val cmd             = IO(Input(pagefault_cmd()))
  val csr_regs_output = IO(Input(new CsrRegsOutput(c)))
  val tlbdata         = IO(Input(new tlb_result_t(c, lvl = 2)))

  val fault = IO(Output(Bool()))

  /**************************************************************************/
  /* MPRV/MPP based privilege calculation                                   */
  /**************************************************************************/
  val (vm_enabled, vm_privilege) = csr_regs_output.getVmSignals()

  fault := false.B

  /**************************************************************************/
  /* VM disabled                                                            */
  /**************************************************************************/
  when(!vm_enabled) {
    // Machine mode or Bare mode (User/supervisor), no pagefault possible
  } .otherwise {
    /************************************************************************/
    // Invalid TLB data
    /************************************************************************/
    when(!tlbdata.valid) {
      fault := true.B
    }

    /************************************************************************/
    /* Supervisor/User checks                                               */
    /************************************************************************/
    when(vm_privilege === privilege_t.S) {
      when(tlbdata.user && !csr_regs_output.sum) {
        fault := true.B
      }
    } .elsewhen(vm_privilege === privilege_t.USER) {
      when(!tlbdata.user) {
        fault := true.B
      }
    }

    /************************************************************************/
    /* Access/Dirty checks                                                  */
    /************************************************************************/
    when(!tlbdata.access) { 
      fault := true.B
    } .elsewhen(cmd === pagefault_cmd.store) {
      /************************************************************************/
      /* Store checks                                                         */
      /************************************************************************/
      when ((!tlbdata.dirty || !tlbdata.write)) {
        fault := true.B
      }
    } .elsewhen(cmd === pagefault_cmd.load) {
      /************************************************************************/
      /* Load checks                                                          */
      /************************************************************************/
      when(!tlbdata.read) {
        when(csr_regs_output.mxr && tlbdata.execute) {

        } .otherwise {
          fault := true.B
        }
      }
    } .elsewhen(cmd === pagefault_cmd.execute) {
      /************************************************************************/
      /* Execute checks                                                       */
      /************************************************************************/
      when(!tlbdata.execute) {
        fault := true.B
      }
    }
  }
}