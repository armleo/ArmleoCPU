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

class armleocpu64_rvfimon(c: CoreParams) extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {
    val clock = Input(Bool())
    val reset = Input(Bool())
    val rvfi = Input(new rvfi_o(c))
    val rvfi_mem_extamo = Input(Bool())
    val errcode = Output(UInt(16.W))
  })
  addResource("armleocpu64_rvfimon.v")
}


class ArmleoCPUFormalWrapper(c: CoreParams) extends Module {
  val mon = Module(new armleocpu64_rvfimon(c))
  val core = Module(new Core(c))
  //val bram = Module(new BRAM(c, 4096, "h40000000".asUInt, verbose = true, instName = "bram0"))
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
  val dm_haltaddr_i   = IO(Input(UInt(c.avLen.W))) // FIXME: use this for halting

  val errcode         = IO(Output(UInt(16.W)))
  val rvfi            = Wire(new rvfi_o(c))

  core.int <> int
  core.debug_req_i <> debug_req_i
  core.dm_haltaddr_i <> dm_haltaddr_i

  rvfi := core.rvfi
  mon.io.rvfi := rvfi
  errcode := mon.io.errcode
  mon.io.reset := reset.asBool
  mon.io.clock := clock.asBool
  mon.io.rvfi_mem_extamo := false.B


  loadMemoryFromFileInline(core.fetch.memory, "../../../tests/verif_tests/verif_isa_tests/output/add.hex32")
}


import chisel3.simulator.VCDHackedEphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class ArmleoCPUSpec extends AnyFlatSpec {
  val c = new CoreParams(
    reset_vector = 0,
    rvfi_enabled = true,
  )

  for (testname <- Seq(/*"lw", "addi", "add", */"lui")) {
    /*
    it should f"ArmleoCPU should test $testname" in {
      print(f"Running test $testname")
      simulate("BasicCPUtester", new ArmleoCPUFormalWrapper(c)) { dut =>
        /*val bis = new BufferedInputStream(new FileInputStream(f"tests/verif_tests/verif_isa_tests/output/$testname.bin"))
        val bArray = LazyList.continually(bis.read).takeWhile(i => -1 != i).map(_.toByte).toArray

        val memory = new Array[Byte](64 * 1024)
        System.arraycopy(bArray, 0, memory, 0, bArray.length)
        
        class bus_ctx(val name: String) {
          
          var state = 0
          var substate = 0
          var addr: BigInt = 0
          var len = dut.ibus.ar.bits.len.peek().litValue +  1
        }
        

        def memory_read_step(ctx: bus_ctx, ibus: ibus_t, dut: ArmleoCPUFormalWrapper): Unit = {
          ibus.ar.ready.poke(false)
          ibus.r.valid.poke(false)
          ibus.r.bits.data.poke(0)
          ibus.r.bits.last.poke(false)

          if(ctx.state == 0) {
            ctx.addr = ibus.ar.bits.addr.peek().litValue
            ctx.substate = 0
            ctx.len = ibus.ar.bits.len.peek().litValue + 1
            
            if(ibus.ar.valid.peek().litValue != 0) {
              ctx.state = 1
              println(f"memory_read_step ${ctx.name}: Memory request addr: ${ctx.addr} len: ${ctx.len}")
            }
            
          } else if(ctx.state == 1) {
            ibus.ar.bits.addr.expect(ctx.addr)
            ibus.ar.valid.expect(true.B)
            ibus.ar.bits.len.expect(ctx.len - 1)
            ibus.ar.ready.poke(true)

            ctx.state = 2
            println(f"memory_read_step ${ctx.name}: Memory request wait cycle, addr: ${ctx.addr} len: ${ctx.len}")
          } else if(ctx.state == 2) {
            ibus.ar.ready.poke(false)
            ibus.ar.valid.expect(false.B)
            ibus.r.valid.poke(false)
            ibus.r.bits.data.poke(0)
            ibus.r.bits.last.poke(false)

            if(ctx.substate == 1) {
              
              ibus.r.valid.poke(true)
              val arr = Array.concat(bArray.slice(ctx.addr.toInt, ctx.addr.toInt + c.busBytes), new Array[Byte](1))
              ibus.r.bits.data.poke(BigInt(arr.toSeq.reverse.toArray))
              println(f"memory_read_step ${ctx.name}: Memory data data cycle, addr: ${ctx.addr} len: ${ctx.len} data: ${arr.toSeq}")
              ctx.addr = ctx.addr + c.busBytes
              ctx.substate = 0

              if(ctx.len == 1) {
                ctx.state = 0
                ibus.r.bits.last.poke(true)
              }

              ctx.len = ctx.len - 1
            } else {
              println(f"memory_read_step ${ctx.name}: Memory data wait cycle, addr: ${ctx.addr} len: ${ctx.len}")
              ctx.substate = 1
            }
            
            ibus.r.ready.expect(true.B) 
          }
        }

        // FIXME: Add check for tohost/fromhost and also make sure they have proper addresses

        val ictx: bus_ctx = new bus_ctx("ibus")
        val dctx: bus_ctx = new bus_ctx("dbus")
        */


        for(i <- 0 until 10) {
          //memory_read_step(ictx, dut.ibus, dut)
          //memory_read_step(dctx, dut.dbus, dut)
          //dut.clock.step(0)
          //dut.errcode.expect(0)
          dut.clock.step(1)
        }
        // FIXME: Add the dbus interface
        // FIXME: Add the check at the end for the fail/pass value in memory
        // FIXME: Load memory once from binary file into memory
      }
    }*/

    it should "generate Verilog, run Verilator, and check testbench output" in {
      // 1. Generate Verilog
      val verilogDir = "test_run_dir/verilator_gen"
      val verilogFile = s"$verilogDir/ArmleoCPUFormalWrapper.v"
      val _ = new File(verilogDir).mkdirs()
      ChiselStage.emitSystemVerilogFile(
        new ArmleoCPUFormalWrapper(c),
          Array(/*"-frsq", "-o:memory_configs",*/ "--target-dir", verilogDir, "--target", "verilog"),
          Array("--lowering-options=disallowPackedArrays,disallowLocalVariables")
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
        """
        #include <verilated.h>
        #include "VArmleoCPUFormalWrapper.h"
        #include <iostream>
        int main(int argc, char **argv) {
            Verilated::commandArgs(argc, argv);
            VArmleoCPUFormalWrapper* top = new VArmleoCPUFormalWrapper;
            top->reset = 1;
            top->clock = 0;
            for (int i = 0; i < 2; ++i) {
                top->clock = !top->clock;
                top->eval();
            }
            top->reset = 0;
            for (int i = 0; i < 20; ++i) {
                top->clock = !top->clock;
                top->eval();
            }
            std::cout << "TESTBENCH_DONE" << std::endl;
            delete top;
            return 0;
        }
        """
      val cppFile = s"$verilogDir/testbench.cpp"
      val writer = new PrintWriter(new File(cppFile))
      writer.write(cppTestbench)
      writer.close()

      // 3. Call Verilator to compile
      val verilatorCmd =
        s"verilator --cc ArmleoCPUFormalWrapper.v --exe testbench.cpp --build -j 0"
      val verilatorResult = Process(verilatorCmd, new File(verilogDir)).!

      assert(verilatorResult == 0, "Verilator failed to build the testbench")

      // 4. Run the generated executable and check output
      val simExe = s"$verilogDir/obj_dir/VArmleoCPUFormalWrapper"
      val simOutput = Process(simExe).!!

      assert(!simOutput.contains("TESTBENCH_DONE"), "Testbench did not complete correctly")
    }
  }
}


