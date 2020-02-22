package armleocpu

import armleopc._
import chisel3._
import chisel3.util._

class MemHostIf extends Bundle {
	val address = Output(UInt(32.W))
	val burstcount = Output(UInt(4.W))
	val waitrequest = Input(Bool())

	val read = Output(Bool())
	val readdata = Input(UInt(32.W))
	val readdatavalid = Input(Bool())

	val write = Output(Bool())
	val writedata = Output(UInt(32.W))
}

class Cache(LANES_W : Int, TLB_ENTRIES_W: Int, debug: Boolean, mememulate: Boolean) extends Module {
	val io = IO(new Bundle {
		val memory = new MemHostIf()

		val address = Input(UInt(32.W))
		
		val pipeline_wait = Output(Bool())
		val done = Output(Bool())
		val pagefault = Output(Bool())
    val missAlligned = Output(Bool())

		val	st_type = Input(UInt(3.W))
		val write = Input(Bool())
		val writedata = Input(UInt(32.W))

		val ld_type = Input(UInt(2.W))
		val read = Input(Bool())
		val readdata = Output(UInt(32.W))
		//val accesstag = Output(new AccessTag())
		
		val flush = Input(Bool())
		val flushing = Output(Bool())
		val flushdone = Output(Bool())
		
		val satp_mode = Input(Bool())
		val satp_ppn = Input(UInt(20.W))
	})
  
	io.pipeline_wait := true.B
	io.done := false.B
	io.pagefault := false.B
	io.readdata := 0.U
	io.flushing := false.B
	io.flushdone := false.B

	io.memory.address := 0.U
	io.memory.burstcount := 8.U
	io.memory.read := 0.U
	io.memory.write := 0.U
	io.memory.writedata := 0.U
	assert(LANES_W <= 7)
	val LANES = 1 << LANES_W

	val PTAG_W = 20 - LANES_W

  val access = io.read || io.write
	
	val tlb = Module(new TLB(TLB_ENTRIES_W, debug, mememulate))
  val validStorage = Module(new Mem_1w1r(mememulate, LANES_W, 1))
  val dirtyStorage = Module(new Mem_1w1r(mememulate, LANES_W, 1))
  val ptagStorage = Module(new Mem_1w1r(mememulate, LANES_W, 20))
  val datastorage = Seq.fill(4) {
	  Module(new Mem_1w1r(mememulate, LANES_W + 3, 32))
	}
  
  val storeGen = Module(new StoreGen)
  val loadGen = Module(new LoadGen)
  
  // data storage. 32 bit words
  // contains 16 words per lane
  // LANES amount of lanes
  // address structure for data storage
  // Cat(lane(LANES_W bits), offset(3))

  // address structure
	// virtual tag (20 bits) | lanes number (LANES_W bits) | offset (3 bits) | inword_offset (2 bits)
	
	val vtag = io.address(32, 12)
	val lane = io.address(12, 12-LANES_W)
	val offset = io.address(4, 2) // 3 bits
	val inwordOffset = io.address(1, 0) // 2 bits

  //saved for FSM
  val target_lane = Reg(UInt(LANES_W.W))
  val target_refill_ptag = Reg(UInt(20.W))
  val taget_vtag = Reg(UInt(20.W))
  val va_vpn = Seq.fill(2) {
    Wire(UInt(10.W))
  }
  va_vpn(0) := target_vtag(9, 0)
  va_vpn(1) := target_vtag(19, 10)
  val table_base = Mux(ptw_level === 1.U, io.satp_ppn, leaf_pointer)
  val leaf_pointer = Reg(UInt(20.W))
  val pte_lowbits = io.memory.readdata(3, 0)
  val pte_correct_top_offset := io.memory.readdata(31, 30) === 0.U;
  // ptw state machine
  val ptw_level = Reg(UInt(1.W))
  
  storeGen.io.inwordOffset := inwordOffset
  storeGen.io.rawWritedata := io.writedata
  storeGen.io.st_type := io.st_type

  // TODO: first cycle store gen
  // TODO: second cycle load gen
  //storeGen.io.st_type := io.st_type

  loadGen.io.inwordOffset := inwordOffset
  loadGen.io.ld_type := io.ld_type
  loadGen.io.rawData := Cat(
      datastorage(3).io.readdata,
      datastorage(2).io.readdata, 
      datastorage(1).io.readdata, 
      datastorage(0).io.readdata
    )
  io.readdata := loadGen.io.result


	tlb.io.enable := io.satp_mode
	// tlb.io.virt := Mux(tlb.io.write, , vtag) // TODO:
	// tlb.io.resolve assigned in FSM
	// tlb.io.write assigned in FSM while PTWing
	// tlb.io.invalidate assigned in FSM while Flushing all
  tlb.io.invalidate := state === flush_all
	tlb.io.accesstag_input := io.memory.readdata(7, 0)
	tlb.io.phystag_input :=
			Cat(io.memory.readdata(29, 20), // high part
			Mux(ptw_level === 1.U, target_vtag(9, 0), io.memory.readdata(19, 10))) // low part
			//			   for Megapage use value from request
			//			   for 4K Page use value from leaf
  

	// state machine
	val idle = 0.U
	val ptw = 1.U
	val refill = 2.U
	val flush = 3.U
	val flush_all = 4.U

	val state = RegInit(0.U(4.W))
	
	val accessDone = RegInit(false.B)
	val accessAddress = RegInit(0.U(32.W))

  val accessVtag = accessAddress(32, 12)
	val accessLane = accessAddress(12, 12-LANES_W)
	val accessOffset = accessAddress(4, 2) // 3 bits
	val accessInwordOffset = accessAddress(1, 0) // 2 bits

  tlb.io.resolve := false.B
  physStorage.io.readaddress := lane
  validStorage.io.readaddress := lane
  dirtyStorage.io.readaddress := lane

  dirtyStorage.io.writeaddress := accessLane
  for(i <- 0 to 3) {
    datastorage(i).io.writeaddress := accessLane
  }
  
	switch (state) {
		is (idle) {
			when(io.flush) {
        // Flush request
				state := flush_all
			} .overwise {
				when(access) {
          // access request
					tlb.io.resolve := true.B
          physStorage.io.read := true.B
          validStorage.io.read := true.B
          dirtyStorage.io.read := true.B
          accessRead := io.read
          accessWrite := io.write
					accessAddress := io.address
          accessLd_type := io.ld_type
          accessSt_type := io.st_type
					// do tlb resolve
				}
				when(tlb.io.done) {
					when(!tlb.io.miss) {
						// valid
						when(
              validStorage.io.readdata && // valid
              tlb.io.phystag_output === physStorage.io.readdata // correct tag
            ) {
							// cache hit
              // read done by sync logic
              when(accessWrite) {
                //datastorage.io.readdata
                for(i <- 0 to 3) {
                  datastorage(i).io.writedata := Mux(loadGen.io.mask(i), loadGen.io.result((i+1)*8-1, (i)*8), datastorage(i).io.readdata((i+1)*8-1, (i)*8))
                  datastorage(i).io.write := true.B
                }
                dirtyStorage.write := true.B
                dirtyStorage.writedata := 1.U
              }

						}.overwise {
              // cache miss
              when(dirtyStorate.io.readdata && validStorage.io.readdata) {
                // TODO: flush and refill
                state := flush
              }.overwise {
                // TODO: refill
                state := refill
              }
							// cache miss
						}
					}.overwise {
						// tlb invalid
						state := ptw
					}
				}
			}
			
		}
		is (ptw) {
			state := idle
		}
		is (refill) {
			
		}
		is (flush) {
			
		}
		is (flush_all) {
			
		}
	}*/

}