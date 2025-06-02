package armleocpu


import chisel3._

// To generate the formal monitor:
// git clone https://github.com/SymbioticEDA/riscv-formal.git
// cd monitor
// python3 generate.py -i rv32i -c 1 -a -p armleocpu_rvfimon > armleocpu_rvfimon.v
// cp armleocpu_rvfimon.v ../../ArmleoCPU/src/test/resources/armleocpu_rvfimon.v

import chisel3.experimental._ // To enable experimental features

import chisel3.util.HasBlackBoxResource
import chisel3.util.experimental.loadMemoryFromFile
import chisel3.util.experimental.loadMemoryFromFileInline
import java.io.File
import chisel3.stage._
import java.io.PrintWriter
import scala.sys.process._
import circt.stage.ChiselStage
import chisel3.util.HexMemoryFile
import chisel3.util.Fill

class armleocpu64_rvfimon(ccx: CCXParams) extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {
    val clock = Input(Bool())
    val reset = Input(Bool())
    val rvfi = Input(new rvfi_o(ccx))
    val rvfi_mem_extamo = Input(Bool())
    val errcode = Output(UInt(16.W))
  })
  addResource("armleocpu64_rvfimon.v")
}


class ArmleoCPUFormalWrapper(ccx: CCXParams, imemFile:String) extends Module {
  val mon = Module(new armleocpu64_rvfimon(ccx))
  val core = Module(new Core(ccx))
  val bram = Module(new BRAM(16 * 1024, "h40000000".asUInt, new HexMemoryFile(imemFile), ccx))
  //val bus_mux = Module(new dbus_mux(bram.io, 2, true))

  //bus_mux.io.upstream(0) <> core.dbus
  //bus_mux.io.upstream(1) <> core.ibus


  /**************************************************************************/
  /*                                                                        */
  /*                INPUT/OUTPUT                                            */
  /*                                                                        */
  /**************************************************************************/

  val int             = IO(Input(new InterruptsInputs))
  val debug_req_i     = IO(Input(Bool()))
  val dm_haltaddr_i   = IO(Input(UInt(ccx.avLen.W))) // FIXME: use this for halting

  val errcode         = IO(Output(UInt(16.W)))
  val rvfi            = Wire(new rvfi_o(ccx))

  core.dynRegs.resetVector  := "h40000000".U
  core.dynRegs.mtVector     := "h40002000".U
  core.dynRegs.stVector     := "h40004000".U
  

  core.dynRegs.mvendorid    := "h0A1AA1E0".U
  core.dynRegs.marchid      := 1.U
  core.dynRegs.mimpid       := 1.U
  core.dynRegs.mhartid      := 0.U
  core.dynRegs.mconfigptr   := "h100".U

  core.staticRegs.pmpcfg_default(0) := "b00011111".U // Allow all access, unlocked, NAPOT addressing
  core.staticRegs.pmpaddr_default(0) := Fill(ccx.apLen, 1.U(1.W))
  

  //core.staticRegs := 
  core.ibus <> bram.io
  core.int <> int
  core.debug_req_i <> debug_req_i
  core.dm_haltaddr_i <> dm_haltaddr_i

  rvfi := core.rvfi
  mon.io.rvfi := rvfi
  errcode := mon.io.errcode
  mon.io.reset := reset.asBool
  mon.io.clock := clock.asBool
  mon.io.rvfi_mem_extamo := false.B
}


import chisel3.simulator.VCDHackedEphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class ArmleoCPUSpec extends AnyFlatSpec {
  val ccx = new CCXParams(
    rvfi_enabled = true
  )

  for (testname <- Seq(/*"lw", "addi", "add", */"lui")) {
    it should "generate Verilog, run Verilator, and check testbench output" in {
      // Create the dirsc
      val verilogDirRel = "test_run_dir/verilator_gen"
      val verilogDir = new File(verilogDirRel).getAbsolutePath
      val verilogFile = s"$verilogDir/ArmleoCPUFormalWrapper.v"
      val _ = new File(verilogDir).mkdirs()

      // Convert ELF to binary using objcopy with RISCV_PREFIX
      val riscvPrefix = sys.env.getOrElse("RISCV_PREFIX", "riscv64-unknown-elf-")
      val objcopyCmd = s"${riscvPrefix}objcopy"
      val elfFile = "build/riscv-tests/isa/rv64ui-p-add"
      val binFile = s"$verilogDir/rv64ui-p-add.bin"
      val objcopyArgs = Seq("-O", "binary", elfFile, binFile, "-V")
      val objcopyResult = scala.sys.process.Process(objcopyCmd +: objcopyArgs).!
      assert(objcopyResult == 0, "Failed to convert ELF to binary with objcopy")


      // Generate imem.hex32 from binary using the python script
      val hexFile = s"$verilogDir/imem.hex32"
      val pythonScript = "scripts/convert_binary_to_verilog_hmem.py"
      val pythonCmd = Seq("python3", pythonScript, binFile, hexFile, "4")
      val pythonResult = scala.sys.process.Process(pythonCmd).!
      assert(pythonResult == 0, "Failed to generate imem.hex32 from binary")


      // Generate verilog
      ChiselStage.emitSystemVerilogFile(
        new ArmleoCPUFormalWrapper(ccx, hexFile),
          Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", verilogDir, "--target", "verilog"),
          Array("--lowering-options=disallowPackedArrays,disallowLocalVariables", "--disable-all-randomization")
      )
      

      // Remove the marker line and anything past it
      val lines = scala.io.Source.fromFile(verilogFile).getLines().toList
      val marker = "// ----- 8< ----- FILE \"firrtl_black_box_resource_files.f\" ----- 8< -----"
      val markerIdx = lines.indexWhere(_.trim == marker)
      val cleanedLines =
        if (markerIdx >= 0) lines.take(markerIdx) else lines
      val writerVerilog = new PrintWriter(new File(verilogFile))
      cleanedLines.foreach(writerVerilog.println)
      writerVerilog.close()

      // 2. Write a simple C++ testbench
      val cppTestbench =
        f"""
        #include <verilated.h>
        #include "VArmleoCPUFormalWrapper.h"
        #include <verilated_vcd_c.h>
        #include <iostream>
        int main(int argc, char **argv) {
            Verilated::commandArgs(argc, argv);
            VArmleoCPUFormalWrapper* top = new VArmleoCPUFormalWrapper;
            VerilatedVcdC* tfp = new VerilatedVcdC;
            Verilated::traceEverOn(true);
            top->trace(tfp, 99);
            tfp->open("$verilogDir/sim.vcd");

            top->reset = 1;
            top->clock = 0;
            for (int i = 0; i < 2; ++i) {
                top->clock = !top->clock;
                top->eval();
                tfp->dump(i);
            }
            top->reset = 0;
            for (int i = 2; i < 22; ++i) {
                top->clock = !top->clock;
                top->eval();
                tfp->dump(i);
            }
            tfp->close();
            std::cout << "TESTBENCH_DONE" << std::endl;
            delete top;
            delete tfp;
            return 0;
        }
        """
      val cppFile = s"$verilogDir/testbench.cpp"
      val writer = new PrintWriter(new File(cppFile))
      writer.write(cppTestbench)
      writer.close()

      // 3. Call Verilator to compile
      val verilatorCmd =
        s"verilator -O1 --cc ArmleoCPUFormalWrapper.v --exe testbench.cpp --build -j 0 -DENABLE_INITIAL_MEM_=1 --trace"
      val verilatorResult = Process(verilatorCmd, new File(verilogDir)).!

      assert(verilatorResult == 0, "Verilator failed to build the testbench")

      // 4. Run the generated executable and check output
      val simExe = s"$verilogDir/obj_dir/VArmleoCPUFormalWrapper"
      val simCode = Process(simExe).!
      assert(simCode == 0, "Testbench did not complete correctly")
    }
  }
}


