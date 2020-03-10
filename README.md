# ArmleoCPU

RISC-V RV32I compatible CPU created from scratch using chisel. Currently work in progress to execute first instructions.

CPU includes two stage (fetch, execute) pipeline with I-Cache and D-Cache. Cores is MMU capable making it theoretically linux capable with small adjustments to kernel.

# State
|Done   |Test   |Feature        |
|:-----:|:-----:|:-------------:|
|Stall  |Stall  |Top            |
|Y      |None   |Mem            |
|Stall  |Stall  |Fetch          |
|Stall  |Stall  |Cache          |
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
