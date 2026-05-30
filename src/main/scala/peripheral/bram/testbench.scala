package armleocpu.peripheral.bram

import chisel3._
import chisel3.util._
import armleocpu._
import armleocpu.busConst._
import armleocpu.peripheral.BRAM

class ScoreboardWrite(addrWidth: Int, busBytes: Int) extends Bundle {
  val addr = UInt(addrWidth.W)
  val data = UInt((busBytes * 8).W)
  val strb = UInt(busBytes.W)
}

class ScoreboardIO(addrWidth: Int, busBytes: Int) extends Bundle {
  val write = Input(Valid(new ScoreboardWrite(addrWidth, busBytes)))
  val readAddr = Input(UInt(addrWidth.W))
  val readData = Output(UInt((busBytes * 8).W))
  val readValid = Output(UInt(busBytes.W))
}

class Scoreboard(implicit bp: BusParams) extends Module {
  private val words = 1 << (bp.addrWidth - log2Ceil(bp.busBytes))

  val io = IO(new ScoreboardIO(bp.addrWidth, bp.busBytes))

  val mem = Mem(words, Vec(bp.busBytes, UInt(8.W)))
  val valid = RegInit(VecInit(Seq.fill(words)(VecInit(Seq.fill(bp.busBytes)(false.B)))))
  private def wordIdx(addr: UInt): UInt = {
    if (words == 1) 0.U else addr(bp.addrWidth - 1, log2Ceil(bp.busBytes))
  }
  val writeIdx = wordIdx(io.write.bits.addr)
  val readIdx = wordIdx(io.readAddr)
  val wdata = io.write.bits.data.asTypeOf(Vec(bp.busBytes, UInt(8.W)))

  when(io.write.valid) {
    for (idx <- 0 until bp.busBytes) {
      when(io.write.bits.strb(idx)) {
        mem(writeIdx)(idx) := wdata(idx)
        valid(writeIdx)(idx) := true.B
      }
    }
  }

  io.readData := mem(readIdx).asUInt
  io.readValid := valid(readIdx).asUInt
}

class Stimulator(maxTransactions: Int = 1024)(implicit bp: BusParams) extends Module {
  val io = IO(new Bundle {
    val bus = new ReadWriteBus()(bp)
    val done = Output(Bool())
  })

  val sIdle :: sAw :: sW :: sB :: sAr :: sR :: Nil = Enum(6)
  val state = RegInit(sIdle)
  val txCount = RegInit(0.U(log2Ceil(maxTransactions + 1).W))
  val beat = Reg(UInt((bp.lenWidth + 1).W))
  val savedLen = Reg(UInt(bp.lenWidth.W))
  val savedAddr = Reg(UInt(bp.addrWidth.W))
  val rng = chisel3.util.random.LFSR(32)

  private val alignBits = log2Ceil(bp.busBytes)
  private val alignedAddr =
    if (bp.busBytes == 1) rng(bp.addrWidth - 1, 0)
    else if (bp.addrWidth == alignBits) 0.U(bp.addrWidth.W)
    else Cat(rng(bp.addrWidth - 1, alignBits), 0.U(alignBits.W))
  private val misalignedAddr =
    if (bp.busBytes == 1) alignedAddr
    else if (bp.addrWidth == alignBits) 1.U(bp.addrWidth.W)
    else Cat(rng(bp.addrWidth - 1, alignBits), 1.U(alignBits.W))
  private val chooseMisaligned = rng(7, 0) < 26.U
  private val chooseBurst = rng(4)
  private val nextLen = Mux(chooseBurst, 3.U(bp.lenWidth.W), 0.U(bp.lenWidth.W))
  private val nextAddr = Mux(chooseMisaligned, misalignedAddr, alignedAddr)

  io.bus.ar.valid := false.B
  io.bus.ar.bits := 0.U.asTypeOf(io.bus.ar.bits)
  io.bus.aw.valid := false.B
  io.bus.aw.bits := 0.U.asTypeOf(io.bus.aw.bits)
  io.bus.w.valid := false.B
  io.bus.w.bits := 0.U.asTypeOf(io.bus.w.bits)
  io.bus.r.ready := true.B
  io.bus.b.ready := true.B
  io.done := txCount === maxTransactions.U

  switch(state) {
    is(sIdle) {
      when(!io.done) {
        savedLen := nextLen
        savedAddr := nextAddr
        beat := 0.U
        state := Mux(rng(5), sAw, sAr)
      }
    }

    is(sAw) {
      io.bus.aw.valid := true.B
      io.bus.aw.bits.addr := savedAddr
      io.bus.aw.bits.len := savedLen
      when(io.bus.aw.fire) {
        state := sW
      }
    }

    is(sW) {
      io.bus.w.valid := true.B
      io.bus.w.bits.data := Cat(rng, rng)((bp.busBytes * 8) - 1, 0)
      io.bus.w.bits.strb := Fill(bp.busBytes, 1.U(1.W))
      io.bus.w.bits.last := beat === savedLen
      when(io.bus.w.fire) {
        beat := beat + 1.U
        when(io.bus.w.bits.last) {
          state := sB
        }
      }
    }

    is(sB) {
      when(io.bus.b.fire) {
        txCount := txCount + 1.U
        state := sIdle
      }
    }

    is(sAr) {
      io.bus.ar.valid := true.B
      io.bus.ar.bits.addr := savedAddr
      io.bus.ar.bits.len := savedLen
      when(io.bus.ar.fire) {
        state := sR
      }
    }

    is(sR) {
      when(io.bus.r.fire && io.bus.r.bits.last) {
        txCount := txCount + 1.U
        state := sIdle
      }
    }
  }
}

class Checker(implicit bp: BusParams) extends Module {
  val io = IO(new Bundle {
    val arFire = Input(Bool())
    val arBits = Input(new ARPayload)
    val rFire = Input(Bool())
    val rBits = Input(new RPayload)
    val awFire = Input(Bool())
    val awBits = Input(new AWPayload)
    val wFire = Input(Bool())
    val wBits = Input(new WPayload)
    val bFire = Input(Bool())
    val bBits = Input(new BPayload)
    val scoreboardReadAddr = Output(UInt(bp.addrWidth.W))
    val scoreboardReadData = Input(UInt((bp.busBytes * 8).W))
    val scoreboardReadValid = Input(UInt(bp.busBytes.W))
    val scoreboardWrite = Output(Valid(new ScoreboardWrite(bp.addrWidth, bp.busBytes)))
    val failed = Output(Bool())
  })

  private val alignBits = log2Ceil(bp.busBytes)
  private def misaligned(addr: UInt): Bool = if (bp.busBytes == 1) false.B else addr(alignBits - 1, 0) =/= 0.U

  val failed = RegInit(false.B)
  val readAddr = Reg(UInt(bp.addrWidth.W))
  val readMisaligned = Reg(Bool())
  val writeAddr = Reg(UInt(bp.addrWidth.W))
  val writeMisaligned = Reg(Bool())
  val writeActive = RegInit(false.B)

  io.scoreboardReadAddr := readAddr
  io.scoreboardWrite.valid := false.B
  io.scoreboardWrite.bits.addr := writeAddr
  io.scoreboardWrite.bits.data := io.wBits.data
  io.scoreboardWrite.bits.strb := io.wBits.strb
  io.failed := failed

  when(io.arFire) {
    readAddr := io.arBits.addr
    readMisaligned := misaligned(io.arBits.addr)
  }

  when(io.rFire) {
    when(readMisaligned) {
      failed := failed || io.rBits.resp =/= DECERR
    } .otherwise {
      val got = io.rBits.data.asTypeOf(Vec(bp.busBytes, UInt(8.W)))
      val expected = io.scoreboardReadData.asTypeOf(Vec(bp.busBytes, UInt(8.W)))
      val mismatch = VecInit((0 until bp.busBytes).map(idx =>
        io.scoreboardReadValid(idx) && got(idx) =/= expected(idx)
      )).asUInt.orR
      failed := failed || io.rBits.resp =/= OKAY || mismatch
    }
    readAddr := readAddr + bp.busBytes.U
  }

  when(io.awFire) {
    writeAddr := io.awBits.addr
    writeMisaligned := misaligned(io.awBits.addr)
    writeActive := true.B
  }

  when(writeActive && io.wFire) {
    io.scoreboardWrite.valid := !writeMisaligned
    writeAddr := writeAddr + bp.busBytes.U
  }

  when(io.bFire) {
    failed := failed || Mux(writeMisaligned, io.bBits.resp =/= DECERR, io.bBits.resp =/= OKAY)
    writeActive := false.B
  }
}

class SynthesizableTestbench(maxTransactions: Int = 1024)(implicit ccx: CCXParams, bp: BusParams) extends Module {
  implicit val memoryFile: MemoryFile = new HexMemoryFile("")

  val io = IO(new Bundle {
    val done = Output(Bool())
    val failed = Output(Bool())
  })

  val stimulator = Module(new Stimulator(maxTransactions))
  val bram = Module(new BRAM(bp))
  val scoreboard = Module(new Scoreboard)
  val checker = Module(new Checker)

  bram.io <> stimulator.io.bus

  checker.io.arFire := stimulator.io.bus.ar.fire
  checker.io.arBits := stimulator.io.bus.ar.bits
  checker.io.rFire := stimulator.io.bus.r.fire
  checker.io.rBits := stimulator.io.bus.r.bits
  checker.io.awFire := stimulator.io.bus.aw.fire
  checker.io.awBits := stimulator.io.bus.aw.bits
  checker.io.wFire := stimulator.io.bus.w.fire
  checker.io.wBits := stimulator.io.bus.w.bits
  checker.io.bFire := stimulator.io.bus.b.fire
  checker.io.bBits := stimulator.io.bus.b.bits

  scoreboard.io.write := checker.io.scoreboardWrite
  scoreboard.io.readAddr := checker.io.scoreboardReadAddr
  checker.io.scoreboardReadData := scoreboard.io.readData
  checker.io.scoreboardReadValid := scoreboard.io.readValid

  io.done := stimulator.io.done
  io.failed := checker.io.failed
}
