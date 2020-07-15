# Register file
Uses Logic elements to make 1 sync write, 2 async read port memory.

# Cache
!IMPORTANT! Cachable region should be all read writabe or return error if address does not exist for both read and write

Cache is multiple way multi set physically tagged with one cycle latency on hit.
It reads from storage at index address idx and in first cycle and requests tlb address resolve.
On second cycle it compares all tags and tlb physical address and outputs data or generates a stall in case of miss or tlb miss.
On cycle idle:
	if cache access
		begin tlb read and backstorage read on port a
	if no tlb miss
		if address[31]
			then action is passed directly to memory bus
		else
			if missed lane is dirty and valid
				go to flush
			else if missed lane is not dirty
				go to refill
				record address of request
			else
				means that storage is valid and write or read is issued to cache
	else
		go to page table walk
		record address of request
Page table walk
	PTW block is mux into memory bus and is responsible to resolve memory request
	after resolving write tlb and address of request
Flush
	first_byte_read:
		read from sync storage to current_wdata
		
	write:
		write current_wdata to memory
		if done set current_wdata to next value of storage for address_counter
Refill
	Issue read to memory bus and write it to backstorage with at way under number in next_way_to_refill

flush_all:
	initial:
		register csrs
	fetch:
		if all lanes checked
			go to active
			reset flush_all_current_lane counter
		else
			fetch lanestate for all ways
	decide:
		if any way lanestate is dirty
			flush that way and lane
			substate go to fetch
		else if no dirty ways
			substate go to fetch incrementing lane number

# PTW
See source code. It's implementation of RISC-V Page table walker that generated pagefault for some cases and returns access bits with resolved physical address (always gives 4K Pages, because this is what Cache was designed for)
TODO: Test three level pointers to pagefault
# Fetch
Fetch issues icache read each cycle and records next pc into pc.
If icache misses ic2f_wait goes high on next cycle of fetch issue and nextpc outputs current value of register pc, so cache has chance to fetch instruction from correct location.

If fetch is not stalled then it will go to interrupt handler in case of interrupt and in case of pagefault/accessfault will go to according handlers


# Executing
Executes OP/OP_IMM/MULDIV/LUI/AUIPC and
MISC-MEM/SYSTEM instructions  
LOAD/STORE is processed in at least two cycles.  
LOAD/STORE sends CACHE read/write request.  

# Privileges

## CSR registers

|Done   |Test   |Feature             |
|:-----:|:-----:|:------------------:|
|Y      |Y      |machine_info_regs   |
|Y      |Y      |misa                |
|Y      |Y      |mstatus/tvm_tw_tsr  |
|Y      |Y      |mstatus/mxr_mprv_sum|
|Y      |Y      |mtvec               |
|Y      |Y      |mscratch            |
|Y      |Y      |sscratch            |
|Y      |Y      |mepc                |
|Y      |Y      |mcause              |
|Y      |Y      |mtval               |
|Y      |Y      |mcycle/mcycleh      |
|Y      |Y      |minstret/minstret   |
|Y      |Y      |stvec               |
|Y      |Y      |sepc                |
|Y      |Y      |scause              |
|Y      |Y      |stval               |
|N      |N      |satp                |
|N      |N      |interrupt_begin     |
|N      |N      |mret                |
|N      |N      |sret                |
|N      |N      |medeleg             |
|N      |N      |mideleg             |
|N      |N      |mie                 |
|N      |N      |mcounteren          |
|N      |N      |mip                 |
|N      |N      |sstatus             |
|N      |N      |sie                 |
|N      |N      |scounteren          |
|N      |N      |sip                 |


User CSR are not implemented, because we don't support user interrupts

We don't support floating points, so floating point CSR are not implemented

We don't support user interrupts, so sedeleg and sideleg is not implemented

satp is implemented and SV32 (34 bit physical addressing) is supported  

mvendorid, marchid, mimpid, mhartid is implemented as read-only registers parametrized from top

Only direct interrupt/exception mode is supported for mtvec/stvec
mtval is implemented but reads always zero   
mstatus bits:  
* FS and XS is hardwired to zero because no Floating point is implemented  
* SD is hardwired to zero because FS and XS is hardwired to zero  


# interrupts
When interrupt happens, CPU copies current pc to epc.
Privilege is set to machine and previous privilege is set old value of privilege
interrupt pending for that interrupt goes high
interrupt enable for that interrupt goes low
interrupt pending should be cleared and interrupt enabled should be high, when cpu `mret`s to user code.


Timer interrupt
External interrupt
Illegal instruction
Page fault
Memory Access Fault
ECALL
EBREAK
Fetch Address missaligned
Load/Store Address missaligned


# Memory managment
SFENCE.VMA, FENCE and FENCE.I are equivalent and flush ICACHE and DCACHE and TLB.

# DEBUG
When debug_req is hold high debug_ack will go high after some cycles
and CPU will enter debug mode and debug_mode will go high.
When debug_exit_request goes high, debug_ack will go high after some cycles and cpu will exit debug mode.
When in debug mode CPU is stopped.
Each command must be written to debug0.
Commands:
	DEBUG_RESET = 1
	DEBUG_SET_PC = 2
	DEBUG_GET_PC = 3
	DEBUG_SET_REG = 4
	DEBUG_GET_REG = 5
	DEBUG_WRITE_MEMORY = 6
	DEBUG_READ_MEMORY = 7
	DEBUG_SET_CSR = 8
	DEBUG_GET_CSR = 9
	DEBUG_FLUSH = 10
RESET resets whole cpu and outputs reset signal to peripheral
SET_PC sets PC to value of debug1
GET_PC gets PC and places value to debug1
SET_REG sets register number debug1 to value of debug2
GET_REG gets register number debug1 and places into debug2
WRITE_MEMORY writes data to memory with MMU enabled. debug1 is address and debug2 is value to write
READ_MEMORY reads data from memory with MMU enabled. debug1 is address and debug2 is value that was read
SET_CSR sets number debug1 csr with value debug2
GET_CSR gets number debug1 csr and places into debug2
FLUSH flushes cache and tlb

You need to write debug1 and debug2 and then set debug0 with command.
	when debug0 goes to 255 that means that command is executed and debug1 or debug2 holds correct value

To write to or read from physical address you need to execute
	GET CSR from msatp,
	SET_CSR to msatp with disabled mmu,
	FLUSH the cache and tlb,
	execute WRITE_MEMORY or READ_MEMORY,
	SET_CSR with old value of msatp,
	FLUSH the cache and tlb,
There is no hardware breakpoints, so to place breakpoint you need to place EBREAK into instruction stream for Machine code.
If code is user space then machine mode kernel should handle debug commands using separate interface or same interface. Because debug0,1,2 is ignored when not in debug mode.

	

