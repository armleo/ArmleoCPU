# ArmleoCPU

RISC-V RV32IM compatible CPU created from scratch. Currently work in progress to execute first instructions.

CPU includes two stage (fetch, execute) pipeline with I-Cache and D-Cache. Cores is MMU capable making it is theoretically linux capable with small adjustments to kernel.


# State
TODO:
* Write CSR
* Write Execute/atomics
* OPTIONAL: Test BR Cond
* OPTIONAL: Test ALU
* OPTIONAL: Test ImmGen
* Write Execute
* Write Top
* Run isa_tests

Maybe:
* Make OPENSBI with atomics emulation
* Run epoxy-riscv
* Add [PLIC](https://github.com/riscv/riscv-plic-spec/blob/master/riscv-plic.adoc)
* Run Linux when OpenSBI is ready
* May be run stm32f103_ili9341_models3D demo?
* Make my own demo?


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
|N      |N      |Cache/Atomics  |
|WIP    |WIP    |Execute        |
|WIP    |None   |Execute/CSR    |
|Y      |None   |Execute/ALU    |
|Y      |Y      |Execute/MULDIV |
|Y      |To-Do  |Execute/BrCond |
|Y      |Y      |Execute/RegFile|
|Y      |None   |Execute/ImmGen |
|N      |To-Do  |Execute/Atomics|
