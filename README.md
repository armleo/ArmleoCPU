# CoreVX

RISC-V RV32IM compatible CPU created from scratch. Currently work in progress to execute first instructions.

CPU includes two stage (fetch, execute) pipeline with I-Cache and D-Cache. Cores is MMU capable making it is theoretically linux capable with small adjustments to kernel.


# State
TODO:
* Write scratchmem
* Rewrite PTW
* Write Cache (to include change of tlb)
* Test BR Cond
* Test ALU
* Write CSR
* Write Fetch
* Write ImmGen
* Write Execute
* Write Top
* Copy isa_tests from RISC-V Repository
* Run isa_tests on qemu and CoreVX to see results
* Make decision on which memory is Cached (0x8000_0000 or 0x0000_0000)

Maybe:
* Make OPENSBI with atomics emulation
* Run epoxy-riscv
* Make cycle-accurate simulation test as in [leiwand_rv32](https://franzflasch.github.io/debugging/risc-v/verilog/2019/07/31/riscv-core-debugging-with-qemu.html)
* [QEMU Disasm](https://en.wikibooks.org/wiki/QEMU/Invocation) might be useful
* Add [PLIC](https://github.com/riscv/riscv-plic-spec/blob/master/riscv-plic.adoc)
* Run Linux when OpenSBI is ready



|Done   |Test   |Feature        |
|:-----:|:-----:|:-------------:|
|Stall  |Stall  |Top            |
|Y      |None   |Mem            |
|Stall  |Stall  |Fetch          |
|WIP    |WIP    |Cache          |
|Y      |Y      |Cache/PTW      |
|Y      |Y      |Cache/TLB      |
|Y      |Y      |Cache/LoadGen  |
|Y      |Y      |Cache/StoreGen |
|Stall  |Stall  |Execute        |
|To-Do  |To-Do  |Execute/CSR    |
|Y      |To-Do  |Execute/ALU    |
|Y      |To-Do  |Execute/BrCond |
|Y      |Y      |Execute/RegFile|
|To-Do  |WIP    |Execute/ImmGen |
