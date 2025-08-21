package armleocpu

import chisel3._
import chisel3.util._


import chisel3.util._

object pageFault_cmd extends ChiselEnum {
  val none, load, store, execute = Value
}



class PageFault(
  // TODO: Add pageFault logging;
  // verbose: Boolean = true, instName: String = "iptw ",
  implicit val ccx: CCXParams
) extends Module {
  /**************************************************************************/
  /*Input/Output                                                            */
  /**************************************************************************/

  val cmd             = IO(Input(pageFault_cmd()))
  val csrRegs = IO(Input(new CsrRegsOutput))
  val tlbEntry         = IO(Input(new TlbKiloEntry))
  val tlbEntryValid = IO(Input(Bool())) // Valid bit of the TLB entry, used to check if the entry is valid

  val fault = IO(Output(Bool()))


  /**************************************************************************/
  /* MPRV/MPP based privilege calculation                                   */
  /**************************************************************************/
  
  fault := false.B

  /**************************************************************************/
  /* VM disabled                                                            */
  /**************************************************************************/
  when(!csrRegs.vmEnabled) {
    // Machine mode or Bare mode (User/supervisor), no pageFault possible
  } .otherwise {
    /************************************************************************/
    // Invalid TLB data
    /************************************************************************/
    when(!tlbEntryValid) {
      fault := true.B
    }

    /************************************************************************/
    /* Supervisor/User checks                                               */
    /************************************************************************/
    when(csrRegs.vmPrivilege === Privilege.S) {
      when(tlbEntry.user && !csrRegs.sum) {
        fault := true.B
      }
    } .elsewhen(csrRegs.vmPrivilege === Privilege.USER) {
      when(!tlbEntry.user) {
        fault := true.B
      }
    }

    /************************************************************************/
    /* Access/Dirty checks                                                  */
    /************************************************************************/
    when(!tlbEntry.access) { 
      fault := true.B
    } .elsewhen(cmd === pageFault_cmd.store) {
      /************************************************************************/
      /* Store checks                                                         */
      /************************************************************************/
      when ((!tlbEntry.dirty || !tlbEntry.write)) {
        fault := true.B
      }
    } .elsewhen(cmd === pageFault_cmd.load) {
      /************************************************************************/
      /* Load checks                                                          */
      /************************************************************************/
      when(!tlbEntry.read) {
        when(csrRegs.mxr && tlbEntry.execute) {

        } .otherwise {
          fault := true.B
        }
      }
    } .elsewhen(cmd === pageFault_cmd.execute) {
      /************************************************************************/
      /* Execute checks                                                       */
      /************************************************************************/
      when(!tlbEntry.execute) {
        fault := true.B
      }
    }
  }
}
