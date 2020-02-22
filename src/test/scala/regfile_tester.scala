
package armleocpu


import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}
import chisel3._


class RegfileUnitTester(c: Regfile) extends PeekPokeTester(c) {
    poke(c.io.rd.address, 31)
    poke(c.io.rd.data, 300)
    poke(c.io.rd.write, 1)
    poke(c.io.rs1.address, 31)
    poke(c.io.rs2.address, 31)
    step(1)
    poke(c.io.rd.write, 1)
    poke(c.io.rd.address, 30)
    poke(c.io.rd.data, 400)
    expect(c.io.rs1.data, 300)
    //expect(c.io.rs2.data, 400)
    step(1)
    poke(c.io.rs1.address, 31)
    expect(c.io.rs1.data, 300)

    poke(c.io.rs2.address, 30)
    expect(c.io.rs2.data, 400)
}


class RegfileTester extends ChiselFlatSpec {
    "RegfileTest" should s"" in {
        
        Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "firrtl", "--target-dir", "test_run_dir/memtest", "--top-name", "armleocpu_memtest"), () => new Regfile(true)) {
            c => new RegfileUnitTester(c)
        } should be (true)

        //Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "verilator", "--target-dir", "test_run_dir/regfiletest", "--top-name", "armleocpu__regfile"), () => new Regfile)

    }
}