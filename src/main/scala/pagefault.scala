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
  val tlbentry         = IO(Input(new tlb_entry_t(c, lvl = 2)))
  val tlbentry_valid = IO(Input(Bool())) // Valid bit of the TLB entry, used to check if the entry is valid

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
    when(!tlbentry_valid) {
      fault := true.B
    }

    /************************************************************************/
    /* Supervisor/User checks                                               */
    /************************************************************************/
    when(vm_privilege === privilege_t.S) {
      when(tlbentry.user && !csr_regs_output.sum) {
        fault := true.B
      }
    } .elsewhen(vm_privilege === privilege_t.USER) {
      when(!tlbentry.user) {
        fault := true.B
      }
    }

    /************************************************************************/
    /* Access/Dirty checks                                                  */
    /************************************************************************/
    when(!tlbentry.access) { 
      fault := true.B
    } .elsewhen(cmd === pagefault_cmd.store) {
      /************************************************************************/
      /* Store checks                                                         */
      /************************************************************************/
      when ((!tlbentry.dirty || !tlbentry.write)) {
        fault := true.B
      }
    } .elsewhen(cmd === pagefault_cmd.load) {
      /************************************************************************/
      /* Load checks                                                          */
      /************************************************************************/
      when(!tlbentry.read) {
        when(csr_regs_output.mxr && tlbentry.execute) {

        } .otherwise {
          fault := true.B
        }
      }
    } .elsewhen(cmd === pagefault_cmd.execute) {
      /************************************************************************/
      /* Execute checks                                                       */
      /************************************************************************/
      when(!tlbentry.execute) {
        fault := true.B
      }
    }
  }
}
