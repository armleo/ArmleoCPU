package armleocpu


import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import java.io._
import java.nio.ByteBuffer


// To generate the formal monitor:
// git clone https://github.com/SymbioticEDA/riscv-formal.git
// cd monitor
// python3 generate.py -i rv32i -c 1 -a -p armleocpu_rvfimon > armleocpu_rvfimon.v
// cp armleocpu_rvfimon.v ../../ArmleoCPU/src/test/resources/armleocpu_rvfimon.v

import chisel3.experimental._ // To enable experimental features

import chisel3.util.HasBlackBoxResource

/*
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

  /**************************************************************************/
  /*                                                                        */
  /*                INPUT/OUTPUT                                            */
  /*                                                                        */
  /**************************************************************************/

  val ibus            = IO(new ibus_t(c))
  val dbus            = IO(new dbus_t(c))
  val int             = IO(Input(new InterruptsInputs))
  val debug_req_i     = IO(Input(Bool()))
  val dm_haltaddr_i   = IO(Input(UInt(c.avLen.W))) // FIXME: use this for halting

  val errcode         = IO(Output(UInt(16.W)))
  val rvfi            = Wire(new rvfi_o(c))

  core.ibus <> ibus
  core.dbus <> dbus
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

class ArmleoCPUSpec extends AnyFlatSpec {
  val c = new CoreParams(
    reset_vector = 0,
    rvfi_enabled = true,
  )

  for (testname <- Seq(/*"lw", "addi", "add", */"lui")) {
    it should f"ArmleoCPU should test $testname" in {
      print(f"Running test $testname")
      simulate(new ArmleoCPUFormalWrapper(c)) { dut =>
        val bis = new BufferedInputStream(new FileInputStream(f"tests/verif_tests/verif_isa_tests/output/$testname.bin"))
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
              val arr = Array.concat(bArray.slice(ctx.addr.toInt, ctx.addr.toInt + c.bp.dataBytes), new Array[Byte](1))
              ibus.r.bits.data.poke(BigInt(arr.toSeq.reverse.toArray))
              println(f"memory_read_step ${ctx.name}: Memory data data cycle, addr: ${ctx.addr} len: ${ctx.len} data: ${arr.toSeq}")
              ctx.addr = ctx.addr + c.bp.dataBytes
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
        for(i <- 0 until 600) {
          memory_read_step(ictx, dut.ibus, dut)
          memory_read_step(dctx, dut.dbus, dut)
          dut.clock.step(0)
          dut.errcode.expect(0)
          dut.clock.step(1)
        }
        // FIXME: Add the dbus interface
        // FIXME: Add the check at the end for the fail/pass value in memory
        // FIXME: Load memory once from binary file into memory
      }
    }
  }
}
*/