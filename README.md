# ArmleoCPU

RISC-V RV32IM compatible CPU created from scratch. Designed to boot Linux.  


Old RTL is two staged and does not contain 
New CPU includes three stage (fetch, decode, execute) pipeline with I-Cache and D-Cache. Cores is MMU capable making it is theoretically linux capable with small adjustments to kernel.


# State
TODO:
* Rewrite Pipeline to have three stages (fetch, decode, execute)
* Rewrite Cache to include atomic instructions
* Write Execute/atomics
* Fix all TODOs

Maybe:
* Make OpenSBI-like firmware that implements minimal Linux features
* Make cache with MSI cache coherency. (Modified, Shared, Invalid)
* Add [PLIC](https://github.com/riscv/riscv-plic-spec/blob/master/riscv-plic.adoc)
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
|Y      |Y      |Execute/CSR    |
|Y      |None   |Execute/ALU    |
|Y      |Y      |Execute/MULDIV |
|Y      |To-Do  |Execute/BrCond |
|Y      |Y      |Execute/RegFile|
|Y      |None   |Execute/ImmGen |
|N      |To-Do  |Execute/Atomics|
