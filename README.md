# ArmleoCPU

RISC-V RV32I compatible CPU created from scratch using chisel. Currently work in progress to execute first instructions.

CPU includes two stage (fetch, execute) pipeline with I-Cache and D-Cache. Cores is MMU capable making it theoretically linux capable with small adjustments to kernel.

# State
|Done   |Test   |Feature        |
|:-----:|:-----:|:-------------:|
|Y      |None   |Instructions   |
|Stall  |Stall  |Top            |
|Y      |None   |Mem            |
|Y      |None   |Instructions   |
|Y      |To-Do  |Fetch          |
|To-Do  |To-Do  |Cache          |
|To-Do  |To-Do  |Cache/PTW      |
|Y      |Y      |Cache/TLB      |
|Y      |Y      |Cache/LoadGen  |
|Y      |Y      |Cache/StoreGen |
|To-Do  |To-Do  |Execute/CSR    |
|Y      |To-Do  |Execute/ALU    |
|Y      |To-Do  |Execute/BrCond |
|Y      |None   |Execute/Control|
|Y      |Y      |Execute/RegFile|
|Y      |WIP    |Execute/ImmGen |
|Stall  |Stall  |Execute        |
