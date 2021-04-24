package armleocpu

import chisel3._
import chisel3.util._
import armleo_common._

class TLB(ENTRIES_W : Int, debug: Boolean, mememulate: Boolean) extends Module {
    val io = IO(new Bundle{
        val enable = Input(Bool())
        val virt = Input(UInt(20.W))

        val invalidate = Input(Bool())
        val resolve = Input(Bool())
        val miss = Output(Bool())
        val done = Output(Bool())
        val write = Input(Bool())

        val accesstag_output = Output(UInt(8.W))
        val phystag_output = Output(UInt(20.W))

        val accesstag_input = Input(UInt(8.W))
        val phystag_input = Input(UInt(20.W))
    })
    
    val PHYS_W = 32 - 12
    val VIRT_W = 32 - 12 - ENTRIES_W;
    val ENTRIES = 1 << ENTRIES_W;
    val index = io.virt(ENTRIES_W-1, 0)
    val virt_tag = io.virt(19, ENTRIES_W)

    val valid = Mem(ENTRIES, Bool())
    val accesstagStorage    = Module(new Mem_1w1r(mememulate, ENTRIES_W, 7))
    val vtagStorage         = Module(new Mem_1w1r(mememulate, ENTRIES_W, VIRT_W))
    val physStorage         = Module(new Mem_1w1r(mememulate, ENTRIES_W, PHYS_W))
    
    val access = RegNext(io.resolve)

    val index_r = RegEnable(index, io.resolve)
    val enable_r = RegEnable(io.enable, io.resolve)
    val virt_tag_r = RegEnable(virt_tag, io.resolve)

    io.done := access
    io.miss := false.B

    accesstagStorage.io.read := io.resolve
    accesstagStorage.io.write := io.write
    accesstagStorage.io.readaddress := index
    accesstagStorage.io.writeaddress := index
    accesstagStorage.io.writedata := io.accesstag_input(7, 1)
    io.accesstag_output := Mux(enable_r, Cat(accesstagStorage.io.readdata, valid(index)), "b11011111".U)

    vtagStorage.io.read := io.resolve
    vtagStorage.io.write := io.write
    vtagStorage.io.readaddress := index
    vtagStorage.io.writeaddress := index
    vtagStorage.io.writedata := virt_tag

    physStorage.io.read := io.resolve
    physStorage.io.write := io.write
    physStorage.io.readaddress := index
    physStorage.io.writeaddress := index
    physStorage.io.writedata := io.phystag_input
    io.phystag_output := physStorage.io.readdata

    when(access) {
        when(enable_r) {
            // virtual memory enabled
            when(valid(index_r) && (virt_tag_r === vtagStorage.io.readdata)) {
                // hit
                //io.done := true.B
                io.miss := false.B
                if(debug) {
                    printf("[TLB] Hit, phys=0x%x, virt=0x%x, accesstag=0x%x\n", io.phystag_output, io.virt, io.accesstag_output)
                }
            }.otherwise {
                // miss
                //io.done := true.B
                io.miss := true.B
                if(debug) {
                    printf("[TLB] virt = 0x%x, Miss\n", io.virt)
                }
            }
        }.otherwise {
            if(debug) {
                printf("[TLB] Hit %x virtual memory disabled\n", io.virt)
            }
            // virtual memory disabled
            // always hit
            //io.done := true.B
            io.miss := false.B
        }
    }
    when(io.write) {
        /*  
        accesstag[index] <= accesstag_w[7:1];
        valid[index] <= accesstag_w[0];
        phys[index] <= phys_w;
        tag[index] <= virt_tag;
        */
        valid(index) := io.accesstag_input(0)
        printf("[TLB] TLB Write, phys=0x%x, virt=0x%x, accesstag=0x%x\n", io.phystag_input, io.virt, io.accesstag_input)

    }.elsewhen(io.invalidate) {
        for(i <- 0 until ENTRIES) {
            valid(i) := false.B
        }
    }
}