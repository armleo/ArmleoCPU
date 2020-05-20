# ArmleoCPU

RISC-V RV32IM compatible CPU created from scratch. Currently work in progress to execute first instructions.

CPU includes two stage (fetch, execute) pipeline with I-Cache and D-Cache. Cores is MMU capable making it is theoretically linux capable with small adjustments to kernel.


# State
TODO:
* Test if cache using old mcurrent_privilege in exception start cycle
* Write CSR
* OPTIONAL: Test BR Cond
* OPTIONAL: Test ALU
* OPTIONAL: Test ImmGen
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


Y - Code freeze

|Done   |Test   |Feature        |
|:-----:|:-----:|:-------------:|
|WIP    |WIP    |Top            |
|Y      |None   |Mem            |
|Y      |Y      |Fetch          |
|Y      |Y      |Cache          |
|Y      |Y      |Cache/PTW      |
|Y      |Y      |Cache/TLB      |
|Y      |Y      |Cache/LoadGen  |
|Y      |Y      |Cache/StoreGen |
|WIP    |WIP    |Execute        |
|To-Do  |To-Do  |Execute/CSR    |
|Y      |To-Do  |Execute/ALU    |
|Y      |To-Do  |Execute/BrCond |
|Y      |Y      |Execute/RegFile|
|Y      |To-Do  |Execute/ImmGen |
