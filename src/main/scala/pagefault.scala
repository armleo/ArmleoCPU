package armleocpu

import chisel3._
import chisel3.util._


import chisel3.experimental.ChiselEnum

object pagefault_cmd {
  val none = 0x0.U(2.W)
  val load = 0x1.U(2.W)
  val store = 0x2.U(2.W)
  val execute = 0x3.U(2.W)
  val enum_type = UInt(2.W)
}

class Pagefault(
  // TODO: Add pagefault logging;
  // verbose: Boolean = true, instName: String = "iptw ",
  val c: CoreParams,
) extends Module {
  /**************************************************************************/
  /*Input/Output                                                            */
  /**************************************************************************/

  val cmd       = IO(Input(pagefault_cmd.enum_type))
  val mem_priv  = IO(Input(new MemoryPrivilegeState(c)))
  val tlbdata   = IO(Input(new tlb_data_t(c.apLen - c.pgoff_len)))

  val fault = IO(Output(Bool()))

  /**************************************************************************/
  /* MPRV/MPP based privilege calculation                                   */
  /**************************************************************************/
  val (vm_enabled, vm_privilege) = mem_priv.getVmSignals()

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
    when(!tlbdata.meta.valid) {
      fault := true.B
    }

    /************************************************************************/
    /* Supervisor/User checks                                               */
    /************************************************************************/
    when(vm_privilege === privilege_t.S) {
      when(tlbdata.meta.user && !mem_priv.sum) {
        fault := true.B
      }
    } .elsewhen(vm_privilege === privilege_t.USER) {
      when(!tlbdata.meta.user) {
        fault := true.B
      }
    }

    /************************************************************************/
    /* Access/Dirty checks                                                  */
    /************************************************************************/
    when(!tlbdata.meta.access) { 
      fault := true.B
    } .elsewhen(cmd === pagefault_cmd.store) {
      /************************************************************************/
      /* Store checks                                                         */
      /************************************************************************/
      when ((!tlbdata.meta.dirty || !tlbdata.meta.write)) {
        fault := true.B
      }
    } .elsewhen(cmd === pagefault_cmd.load) {
      /************************************************************************/
      /* Load checks                                                          */
      /************************************************************************/
      when(!tlbdata.meta.read) {
        when(mem_priv.mxr && tlbdata.meta.execute) {

        } .otherwise {
          fault := true.B
        }
      }
    } .elsewhen(cmd === pagefault_cmd.execute) {
      /************************************************************************/
      /* Execute checks                                                       */
      /************************************************************************/
      when(!tlbdata.meta.execute) {
        fault := true.B
      }
    }
  }
}