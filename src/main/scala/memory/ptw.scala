package armleocpu

import chisel3._
import chisel3.util._


/*
class PTWReq(implicit val ccx: CCXParams) extends Bundle {
  val vaddr = UInt(ccx.xLen.W)
  val priv  = UInt(2.W)
}

class PTWResp(implicit val ccx: CCXParams) extends Bundle {
  val pte   = UInt(ccx.xLen.W)
  val hit   = Bool()
  val fault = Bool()
}



class PTWIO(implicit val ccx: CCXParams) extends Bundle {
  val req  = Flipped(Decoupled(new PTWReq(ccx)))
  val resp = Decoupled(new PTWResp(ccx))
  val csrRegs = Input(new CsrRegsOutput(ccx))
  // L2 TLB interfaces
  val l2tlb_giga = Flipped(new AssociativeMemoryIO(ways = ccx.l2tlbWays, sets = ccx.l2tlbSets, t = new tlb_entry_t(ccx, 2)))
  val l2tlb_mega = Flipped(new AssociativeMemoryIO(ways = ccx.l2tlbWays, sets = ccx.l2tlbSets, t = new tlb_entry_t(ccx, 1)))
  val l2tlb_kilo = Flipped(new AssociativeMemoryIO(ways = ccx.l2tlbWays, sets = ccx.l2tlbSets, t = new tlb_entry_t(ccx, 0)))
  // L1 TLB interface
  val l1tlb_write = Output(Bool())
  val l1tlb_entry = Output(new tlb_entry_t(ccx, 0))
  // Data cache array interface
  val cacheReq  = Decoupled(new CacheArrayReq(ccx, new CacheParams()))
  val cacheResp = Flipped(Decoupled(new CacheArrayResp(ccx, new CacheParams())))
  // Memory bus interface
  val bus       = new dbus_t(ccx)
}

class PTW(ccx: CCXParams, cp: CacheParams) extends Module {
  val io = IO(new PTWIO(ccx))

  val STATE_IDLE            = 0.U(3.W)
  val STATE_L2TLB           = 1.U(3.W)
  val STATE_CACHE           = 2.U(3.W)
  val STATE_AR              = 3.U(3.W)
  val STATE_R               = 4.U(3.W)
  val STATE_TABLE_WALKING   = 5.U(3.W)
  val STATE_RESP            = 6.U(3.W)
  val state = RegInit(STATE_IDLE)

  val saved_vaddr = Reg(UInt(ccx.xLen.W))
  val current_level = RegInit(2.U(2.W)) // 2: gigapage, 1: megapage, 0: kilopage
  val current_table_base = Reg(UInt((ccx.apLen - cp.pgoff_len).W))
  val pte_value   = Reg(UInt(ccx.xLen.W))
  val rvfiPtes   = Reg(Vec(3, UInt(ccx.xLen.W)))
  val pagefault    = RegInit(false.B)
  val accessfault  = RegInit(false.B)
  val cplt         = RegInit(false.B)

  // VPN extraction for SV39
  val vaddr_vpn = Wire(Vec(3, UInt(9.W)))
  vaddr_vpn(0) := saved_vaddr(20, 12)
  vaddr_vpn(1) := saved_vaddr(29, 21)
  vaddr_vpn(2) := saved_vaddr(38, 30)

  // TLB selection
  val l2tlb_sel = Wire(UInt(2.W))
  l2tlb_sel := current_level

  // Default disables
  io.l2tlb_giga.resolve := false.B
  io.l2tlb_mega.resolve := false.B
  io.l2tlb_kilo.resolve := false.B
  io.l1tlb_write := false.B
  io.l1tlb_entry := 0.U.asTypeOf(new tlb_entry_t(ccx, 0))
  io.cacheReq.valid := false.B
  io.cacheReq.bits := DontCare
  io.bus.ar.valid := false.B
  io.bus.ar.bits := DontCare
  io.bus.r.ready := false.B

  // PTE checks
  val pte_valid   = pte_value(0)
  val pte_read    = pte_value(1)
  val pte_write   = pte_value(2)
  val pte_execute = pte_value(3)
  val pte_invalid = !pte_valid || (!pte_read && pte_write) || (pte_value(63, 54).orR)
  val pte_isLeaf  = pte_read || pte_execute
  val pte_pointer = pte_value(3, 0) === "b0001".U

  // Save PTEs for RVFI
  when(state === STATE_R && io.bus.r.valid) {
    rvfiPtes(current_level) := pte_value
  }

  switch(state) {
    is(STATE_IDLE) {
      pagefault := false.B
      accessfault := false.B
      cplt := false.B
      when(io.req.valid) {
        saved_vaddr := io.req.bits.vaddr
        current_level := 2.U // Start at gigapage
        current_table_base := io.csrRegs.ppn
        state := STATE_L2TLB
      }
    }
    is(STATE_L2TLB) {
      // Select appropriate TLB and check tag
      val tlb_hit = Wire(Bool())
      val tlb_entry = Wire(new tlb_entry_t(ccx, current_level))
      tlb_hit := false.B
      tlb_entry := 0.U.asTypeOf(new tlb_entry_t(ccx, current_level))
      if (current_level == 2.U) {
        io.l2tlb_giga.resolve := true.B
        io.l2tlb_giga.s0.idx := vaddr_vpn(2)
        when(io.l2tlb_giga.s1.valid.asUInt.orR) {
          val hitIdx = PriorityEncoder(io.l2tlb_giga.s1.valid)
          val entry = io.l2tlb_giga.s1.rentry(hitIdx)
          when(entry.va_match(saved_vaddr) && entry.is_leaf()) {
            tlb_hit := true.B
            tlb_entry := entry
          }
        }
      } else if (current_level == 1.U) {
        io.l2tlb_mega.resolve := true.B
        io.l2tlb_mega.s0.idx := vaddr_vpn(1)
        when(io.l2tlb_mega.s1.valid.asUInt.orR) {
          val hitIdx = PriorityEncoder(io.l2tlb_mega.s1.valid)
          val entry = io.l2tlb_mega.s1.rentry(hitIdx)
          when(entry.va_match(saved_vaddr) && entry.is_leaf()) {
            tlb_hit := true.B
            tlb_entry := entry
          }
        }
      } else {
        io.l2tlb_kilo.resolve := true.B
        io.l2tlb_kilo.s0.idx := vaddr_vpn(0)
        when(io.l2tlb_kilo.s1.valid.asUInt.orR) {
          val hitIdx = PriorityEncoder(io.l2tlb_kilo.s1.valid)
          val entry = io.l2tlb_kilo.s1.rentry(hitIdx)
          when(entry.va_match(saved_vaddr) && entry.is_leaf()) {
            tlb_hit := true.B
            tlb_entry := entry
          }
        }
      }
      when(tlb_hit) {
        pte_value := tlb_entry.asUInt
        state := STATE_TABLE_WALKING
      } .otherwise {
        state := STATE_CACHE
      }
    }
    is(STATE_CACHE) {
      io.cacheReq.valid := true.B
      io.cacheReq.bits.addr := saved_vaddr
      io.cacheReq.bits.metaWrite := false.B
      io.cacheReq.bits.metaWdata := VecInit(Seq.fill(cp.ways)(0.U.asTypeOf(new CacheMeta(ccx, cp))))
      io.cacheReq.bits.metaMask  := 0.U
      io.cacheReq.bits.dataWrite := false.B
      io.cacheReq.bits.dataWdata := VecInit(Seq.fill(1 << (cp.waysLog2 + ccx.cacheLineLog2))(0.U(8.W)))
      when(io.cacheReq.ready) {
        state := STATE_AR
      }
      when(io.cacheResp.valid) {
        pte_value := io.cacheResp.bits.dataRdata(0) // Replace with correct offset logic
        state := STATE_TABLE_WALKING
      }
    }
    is(STATE_AR) {
      io.bus.ar.valid := true.B
      io.bus.ar.bits.addr := Cat(current_table_base, vaddr_vpn(current_level), "b00".U(2.W)).asSInt
      io.bus.ar.bits.size := log2Ceil(ccx.xLenBytes).U
      io.bus.ar.bits.lock := false.B
      io.bus.ar.bits.len  := 0.U
      when(io.bus.ar.ready) {
        state := STATE_R
      }
    }
    is(STATE_R) {
      io.bus.r.ready := true.B
      when(io.bus.r.valid) {
        pte_value := frombus(cp, io.bus.ar.bits.addr.asUInt, io.bus.r.bits.data)
        state := STATE_TABLE_WALKING
      }
    }
    is(STATE_TABLE_WALKING) {
      when(pte_invalid) {
        pagefault := true.B
        cplt := true.B
        state := STATE_RESP
      } .elsewhen(pte_isLeaf) {
        cplt := true.B
        // Populate L1 TLB on final resolution
        io.l1tlb_write := true.B
        io.l1tlb_entry := WireInit({
          val e = Wire(new tlb_entry_t(ccx, 0))
          e.vpn := Cat(vaddr_vpn(2), vaddr_vpn(1), vaddr_vpn(0))
          e.ppn := pte_value(53, 10)
          e.read := pte_read
          e.write := pte_write
          e.execute := pte_execute
          e.rvfiPtes := rvfiPtes
          e
        })
        state := STATE_RESP
      } .elsewhen(pte_pointer) {
        when(current_level === 0.U) {
          pagefault := true.B
          cplt := true.B
          state := STATE_RESP
        } .otherwise {
          current_level := current_level - 1.U
          current_table_base := pte_value(53, 10)
          state := STATE_AR
        }
      }
    }
    is(STATE_RESP) {
      io.resp.valid := true.B
      io.resp.bits.pte := pte_value
      io.resp.bits.hit := !pagefault && !accessfault
      io.resp.bits.fault := pagefault || accessfault
      when(io.resp.ready) {
        state := STATE_IDLE
      }
    }
  }
}
*/
/*
class PTW(instName: String = "iptw ",
  c: CoreParams = new CoreParams,
  verbose: Boolean = false
) extends Module {
  // FIXME: 64 bit variant
  // TODO: Add PTW tests in isa tests
  // memory access bus
  val bus                   = IO(new ibus_t(c))
  val bus_dataBytes        = ccx.busBytes

  // request
  val vaddr                 = IO(Input(UInt(ccx.xLen.W)))
  val resolve_req           = IO(Input(Bool()))

  // response
  val cplt                  = IO(Output(Bool()))
  val pagefault            = IO(Output(Bool()))
  val accessfault          = IO(Output(Bool()))
  //FIXME: val pte_o                 = IO(Output(UInt(ccx.xLen.W)))
  //FIXME: val rvfi_pte              = IO(Output(Vec(4, UInt(ccx.xLen.W))))
  
  val physical_address_top  = IO(Output(UInt((44).W)))
  val meta                  = IO(Output())


  // CSR values
  val csrRegs              = IO(Input(new CsrRegsOutput(c)))


  val log = new Logger(c.lp.coreName, instName, verbose)

  
  // constant outputs
  bus.ar.valid  := false.B

  // TODO: needs to be different depending on xLen value and csrRegs.mode
  bus.ar.bits.size   := log2Ceil(ccx.xLenBytes).U
  bus.ar.bits.lock   := false.B
  bus.ar.bits.len    := 0.U

  bus.r.ready   := false.B

  

  val current_table_base = Reg(UInt((ccx.apLen - c.pgoff_len).W)) // a from spec
  val current_level = Reg(UInt((log2Ceil(c.pagetableLevels) + 1).W)) // i from spec
  
  val STATE_IDLE            = 0.U(3.W)
  val STATE_PMA_PMP         = 1.U(3.W)
  val STATE_AR              = 2.U(3.W)
  val STATE_R               = 3.U(3.W)
  val STATE_TABLE_WALKING   = 4.U(3.W)
  val state = RegInit(STATE_IDLE)

  val pma_error = Reg(Bool())
  val bus_error = Reg(Bool())
  val saved_vaddr = Reg(UInt(ccx.avLen.W))

  val saved_vaddr_top = saved_vaddr(ccx.avLen - 1, c.pgoff_len)
  val saved_offset = saved_vaddr(c.pgoff_len - 1, 0)

  val vaddr_vpn = Wire(Vec(3, UInt(9.W)))
  vaddr_vpn(0) := saved_vaddr(20, 12)
  vaddr_vpn(1) := saved_vaddr(29, 21)
  vaddr_vpn(2) := saved_vaddr(38, 30)

  // TODO: RV64 VPN will be 9 bits each in 64 bit
  

  val pte_value   = Reg(UInt(ccx.xLen.W))

  val pte_valid   = pte_value(0)
  val pte_read    = pte_value(1)
  val pte_write   = pte_value(2)
  val pte_execute = pte_value(3)

  val pte_invalid = !pte_valid || (!pte_read && pte_write) || (pte_value(63, 54).orR)
  val pte_isLeaf  = pte_read || pte_execute
  val pte_leafMissaligned = (!pte_value(27, 10).orR && (current_level === 2.U)) || 
                            (!pte_value(18, 10).orR && (current_level === 1.U))
  val pte_pointer = pte_value(3, 0) === "b0001".U
  // outputs

  // We do no align according to bus_dataBytes, since we only request one specific PTE and not more
  bus.ar.bits.addr := Cat(current_table_base, vaddr_vpn(current_level), "b00".U(2.W)).asSInt;

  // FIXME: Test this below
  val physical_address_top_55_30 = pte_value(53, 28)
  val physical_address_top_29_21 = Mux(current_level >= 2.U, saved_vaddr(29, 21), pte_value(27, 19))
  val physical_address_top_20_12 = Mux(current_level >= 1.U, saved_vaddr(20, 12), pte_value(18, 10))
  physical_address_top := Cat(physical_address_top_55_30, physical_address_top_29_21, physical_address_top_20_12)


  cplt          := false.B
  pagefault    := false.B
  accessfault  := false.B
  meta          := pte_value(7, 0).asTypeOf(new tlbmeta_t)

  // TODO: Add PTE storage for RVFI
  
  
  val (defined, memory) = PMA(c, bus.ar.bits.addr.asUInt)
  
  
  switch(state) {
    is(STATE_IDLE) {
      current_level := 1.U
      saved_vaddr := vaddr
      current_table_base := csrRegs.ppn
      when(resolve_req) { // assumes csrRegs.mode -> 1 
                //because otherwise tlb would respond with hit and ptw request would not happen
        state := STATE_AR
        
        log(cf"Resolve requested for virtual address 0x%x, csrRegs.mode is 0x%x", vaddr, csrRegs.mode)
      }
    }
    /*
    is(STATE_PMA_PMP)
    */
    // FIXME: Add PMP/PMA Check
    // FIXME: Save ptes instead of the tlb data
    // FIXME: Save PTEs to return to top, for RVFI
    is(STATE_AR) {
      pma_error := false.B // Reset the PMA Error
      // Bus error does not need to be reset, because it's unconitionally set
      when(defined && memory) {
        bus.ar.valid := true.B
        when(bus.ar.ready) {
          state := STATE_R
          log(cf"AR request sent")
        }
      } .otherwise {
        log(cf"PMA Check failed defined=%x, memory=%x", defined, memory)
        state := STATE_TABLE_WALKING
        pma_error := true.B
      }
    }
    is(STATE_R) {
      when(bus.r.valid) {
        bus_error := bus.r.bits.resp =/= bus_const_t.OKAY
        state := STATE_TABLE_WALKING
        when(bus.r.bits.resp =/= bus_const_t.OKAY) {
          
          log(cf"Resolve failed because bus.r.bits.resp is 0x%x for address 0x%x", bus.r.bits.resp, bus.ar.bits.addr)
        } .otherwise {
            // We use saved_vaddr_top lsb bits to select the PTE from the bus
            // as the pte might be 32 bit, meanwhile the bus can be 128 bit
            // TODO: RV64 replace bus_dataBytes/4 with possibly /8 for xlen == 64
          pte_value := frombus(c, bus.ar.bits.addr.asUInt, bus.r.bits.data)
          
          log(cf"Bus request complete resp=0x%x data=0x%x ar.addr=0x%x pte_value=0x%x", bus.r.bits.resp, bus.r.bits.data, bus.ar.bits.addr.asUInt, pte_value)
          
          
        }
      }
      bus.r.ready := true.B
    }
    is(STATE_TABLE_WALKING) {
      state := STATE_IDLE
      when(pma_error) {
        accessfault := true.B
        cplt := true.B
        log(cf"Responding with pma error")
      } .elsewhen (bus_error) {
        accessfault := true.B
        cplt := true.B
        log(cf"Responding with bus error")
      } .elsewhen(pte_invalid) {
        cplt := true.B
        pagefault := true.B
        
        log(cf"Resolve failed because pte 0x%x is invalid is 0x%x for address 0x%x", pte_value(7, 0), pte_value, bus.ar.bits.addr)
      } .elsewhen(pte_isLeaf) {
        when(!pte_leafMissaligned) {
          cplt := true.B
          
          log(cf"Resolve cplt 0x%x for address 0x%x", Cat(physical_address_top, saved_offset), saved_vaddr)
        } .elsewhen (pte_leafMissaligned){
          cplt := true.B
          pagefault := true.B
          
          log(cf"Resolve missaligned 0x%x for address 0x%x, level = 0x%x", Cat(physical_address_top, saved_offset), saved_vaddr, current_level)
        }
      } .elsewhen (pte_pointer) { // pte is pointer to next level
        when(current_level === 0.U) {
          cplt := true.B
          pagefault := true.B
          
          log(cf"Resolve pagefault for address 0x%x", saved_vaddr)
        } .elsewhen(current_level === 1.U) {
          current_level := current_level - 1.U
          current_table_base := pte_value(31, 10)
          state := STATE_AR
          
          log(cf"Resolve going to next level for address 0x%x, pte = %x", saved_vaddr, pte_value)
        }
      }
    }
  }
}

import _root_.circt.stage.ChiselStage
import chisel3.stage.ChiselGeneratorAnnotation


object PTWGenerator extends App {
  (new ChiselStage).execute(Array("--target-dir", "generated_vlog"), Seq(ChiselGeneratorAnnotation(() => new PTW)))
}


*/