
package armleocpu


import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}
import chisel3._


class RegfileUnitTester(c: Regfile) extends PeekPokeTester(c) {
    def rd_write(regnum: Int, regvalue: Int) {
        poke(c.io.rd.write, 1)
        poke(c.io.rd.address, regnum)
        poke(c.io.rd.data, regvalue)
    }
    def rd_nowrite() {
        poke(c.io.rd.write, 0)
    }
    def rs_read(rs: RegfileReadIf, addr: Int) {
        poke(rs.read, 1)
        poke(rs.address, addr)
    }
    def rs_noread(rs: RegfileReadIf) {
        poke(rs.read, 0)
    }
    // Test case: Do nothing
    rd_nowrite()
    rs_noread(c.io.rs1)
    rs_noread(c.io.rs2)

    // Test case: From reset reg0 value should be zero
    rs_read(c.io.rs1, 0)
    rs_read(c.io.rs2, 0)
    step(1)
    expect(c.io.rs1.data, 0)
    expect(c.io.rs2.data, 0)

    // Test case: Writing reg0 will discard written value
    rd_write(0, 1024)
    expect(c.io.rs1.data, 0)
    expect(c.io.rs2.data, 0)
    step(1)
    rs_noread(c.io.rs1)
    rs_noread(c.io.rs2)
    expect(c.io.rs1.data, 0)
    expect(c.io.rs2.data, 0)
    step(1)
    rs_read(c.io.rs1, 0)
    rs_read(c.io.rs2, 0)
    step(1)
    rs_noread(c.io.rs1)
    rs_noread(c.io.rs2)
    expect(c.io.rs1.data, 0)
    expect(c.io.rs2.data, 0)

    // Test case: Write value to 31 and then read it back (also tests read after write)
    step(1)
    rd_write(31, 1024)
    rs_read(c.io.rs1, 31)
    rs_read(c.io.rs2, 31)
    expect(c.io.rs1.data, 0)
    expect(c.io.rs2.data, 0)

    // Read it back
    step(1)
    rd_nowrite()
    expect(c.io.rs1.data, 0)
    expect(c.io.rs2.data, 0)
    rs_read(c.io.rs1, 31)
    rs_read(c.io.rs2, 31)
    step(1)
    expect(c.io.rs1.data, 1024)
    expect(c.io.rs2.data, 1024)
}


class RegfileTester extends ChiselFlatSpec {
    "RegfileTest" should s"" in {
        
        Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "verilator", "--target-dir", "test_run_dir/regfile_test", "--top-name", "armleocpu_regfile"), () => new Regfile) {
            c => new RegfileUnitTester(c)
        } should be (true)

        //Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "verilator", "--target-dir", "test_run_dir/regfiletest", "--top-name", "armleocpu__regfile"), () => new Regfile)

    }
}