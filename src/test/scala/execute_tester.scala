
/*package armleocpu


import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}
import chisel3._


object instGenerate {
    def BinNumToStr(num:Int, bits: Int): String = {
        val rawNum = Integer.toBinaryString(num)
        val bitsToAdd = bits - rawNum.length()
        if(bits < rawNum.length)
            println("invalid length")
        var result = new String
        for(i <- 0 until bitsToAdd) result += "0"
        result += rawNum
        result
    }
    def addi(rs1: Int, rd: Int, imm: Int): Int = {
         Integer.parseInt(BinNumToStr(imm, 12) + BinNumToStr(rs1, 5) + "000" + BinNumToStr(rd, 5) + "0010011", 2)
    }
    def add(rs1: Int, rs2: Int, rd: Int):Int = {
        Integer.parseInt("0000000" + BinNumToStr(rs1, 5) + BinNumToStr(rs2, 5) + "000" + BinNumToStr(rd, 5) + "0110011", 2)
    }
    def nop():Int = {
        Integer.parseInt("00000000000000000000000000010011", 2)
    }
    def bne(rs1: Int, rs2: Int, imm: Int):Int = {
        //Integer.parseInt((imm & (1 << 12)) + 
        0
    }
}

class ExecuteUnitTester(c: Execute) extends PeekPokeTester(c) {
    println(instGenerate.BinNumToStr(31, 5))
    println(instGenerate.BinNumToStr(0, 5))
    
    poke(c.io.instr, instGenerate.addi(0, 1, 255))
    poke(c.io.pc, 0)
    step(1)

    poke(c.io.instr, instGenerate.addi(0, 2, 127))
    poke(c.io.pc, 4)
    step(1)

    poke(c.io.instr, instGenerate.add(1, 2, 3))
    poke(c.io.pc, 8)
    step(1)

    poke(c.io.instr, instGenerate.nop())
    poke(c.io.pc, 12)
    step(1)
    //expect(c.regfile.io.rd.data, 0)
    //expect(c.regfile.io.rd.write, 1)
    
}


class ExecuteTester extends ChiselFlatSpec {
    "ExecuteTest" should s"" in {
        
        Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "firrtl", "--target-dir", "test_run_dir/execute", "--top-name", "armleocpu_execute"), () => new Execute(true)) {
            c => new ExecuteUnitTester(c)
        } should be (true)
    }
}*/