package armleocpu.l3cache



class MultibankerIO(bankCount: Int, bankCbp: CoherentBusParams)(implicit val ccx: CCXParams, val cbp: CoherentBusParams)
    extends Bundle {
  val up = Vec(ccx.coreCount, Flipped(new CoherentBus()(cbp)))
  val down = Vec(bankCount, new ReadWriteBus()(bankCbp))
}



class Multibanker(bankCount: Int)(implicit ccx: CCXParams, outerCbp: CoherentBusParams) extends CCXModule {
  require(bankCount > 1)
  require(isPow2(bankCount))
  require(outerCbp.addrWidth > log2Ceil(bankCount))

  private val bankBits = log2Ceil(bankCount)
  private val lineBits = ccx.cacheLineLog2
  private val fullCbp = outerCbp
  private val bankCbp = new CoherentBusParams(fullCbp.addrWidth - bankBits)

  val io = IO(new L3CacheBankWrapperIO(bankCount, bankCbp))

  private val banks = {
    implicit val cbp: CoherentBusParams = bankCbp
    Seq.fill(bankCount)(Module(new Bank))
  }

  private def getBankIdx(addr: UInt): UInt = {
    addr(lineBits + bankBits - 1, lineBits)
  }

  private def narrowAddr(addr: UInt): UInt = {
    Cat(addr(fullCbp.addrWidth - 1, lineBits + bankBits), addr(lineBits - 1, 0))
  }

  for (bankIdx <- 0 until bankCount) {
    io.down(bankIdx) <> banks(bankIdx).io.down

    val bank = banks(bankIdx)
    for (core <- 0 until ccx.coreCount) {
      bank.io.up(core).ar.valid := false.B
      bank.io.up(core).ar.bits := 0.U.asTypeOf(bank.io.up(core).ar.bits)
      bank.io.up(core).aw.valid := false.B
      bank.io.up(core).aw.bits := 0.U.asTypeOf(bank.io.up(core).aw.bits)
      bank.io.up(core).w.valid := false.B
      bank.io.up(core).w.bits := 0.U.asTypeOf(bank.io.up(core).w.bits)
      bank.io.up(core).creq.valid := false.B
      bank.io.up(core).creq.bits := 0.U.asTypeOf(bank.io.up(core).creq.bits)

      bank.io.up(core).r.ready := false.B
      bank.io.up(core).b.ready := false.B
      bank.io.up(core).cresp.ready := false.B
      bank.io.up(core).cdata.ready := false.B
    }
  }

  for (core <- 0 until ccx.coreCount) {
    val arBank = getBankIdx(io.up(core).ar.bits.addr)
    val awBank = getBankIdx(io.up(core).aw.bits.addr)
    val creqBank = getBankIdx(io.up(core).creq.bits.addr)

    io.up(core).ar.ready := Mux1H((0 until bankCount).map(i => (arBank === i.U) -> banks(i).io.up(core).ar.ready))
    io.up(core).aw.ready := Mux1H((0 until bankCount).map(i => (awBank === i.U) -> banks(i).io.up(core).aw.ready))
    io.up(core).creq.ready := Mux1H((0 until bankCount).map(i => (creqBank === i.U) -> banks(i).io.up(core).creq.ready))

    for (bankIdx <- 0 until bankCount) {
      val bank = banks(bankIdx)

      bank.io.up(core).ar.valid := io.up(core).ar.valid && arBank === bankIdx.U
      bank.io.up(core).ar.bits.op := io.up(core).ar.bits.op
      bank.io.up(core).ar.bits.addr := narrowAddr(io.up(core).ar.bits.addr)
      bank.io.up(core).ar.bits.len := io.up(core).ar.bits.len
      bank.io.up(core).ar.bits.id := io.up(core).ar.bits.id

      bank.io.up(core).aw.valid := io.up(core).aw.valid && awBank === bankIdx.U
      bank.io.up(core).aw.bits.op := io.up(core).aw.bits.op
      bank.io.up(core).aw.bits.addr := narrowAddr(io.up(core).aw.bits.addr)
      bank.io.up(core).aw.bits.len := io.up(core).aw.bits.len
      bank.io.up(core).aw.bits.id := io.up(core).aw.bits.id

      bank.io.up(core).creq.valid := io.up(core).creq.valid && creqBank === bankIdx.U
      bank.io.up(core).creq.bits.op := io.up(core).creq.bits.op
      bank.io.up(core).creq.bits.addr := narrowAddr(io.up(core).creq.bits.addr)
    }

    val wBank = RegInit(0.U(bankBits.W))
    when(io.up(core).aw.fire) {
      wBank := awBank
    }

    io.up(core).w.ready := Mux1H((0 until bankCount).map(i => (wBank === i.U) -> banks(i).io.up(core).w.ready))

    for (bankIdx <- 0 until bankCount) {
      banks(bankIdx).io.up(core).w.valid := io.up(core).w.valid && wBank === bankIdx.U
      banks(bankIdx).io.up(core).w.bits := io.up(core).w.bits
    }

    val rArb = Module(new Arbiter(chiselTypeOf(io.up(core).r.bits), bankCount))
    val bArb = Module(new Arbiter(chiselTypeOf(io.up(core).b.bits), bankCount))
    val crespArb = Module(new Arbiter(chiselTypeOf(io.up(core).cresp.bits), bankCount))
    val cdataArb = Module(new Arbiter(chiselTypeOf(io.up(core).cdata.bits), bankCount))

    for (bankIdx <- 0 until bankCount) {
      rArb.io.in(bankIdx) <> banks(bankIdx).io.up(core).r
      bArb.io.in(bankIdx) <> banks(bankIdx).io.up(core).b
      crespArb.io.in(bankIdx) <> banks(bankIdx).io.up(core).cresp
      cdataArb.io.in(bankIdx) <> banks(bankIdx).io.up(core).cdata
    }

    io.up(core).r <> rArb.io.out
    io.up(core).b <> bArb.io.out
    io.up(core).cresp <> crespArb.io.out
    io.up(core).cdata <> cdataArb.io.out
  }
}
