# CoreVX

RISC-V RV32IM compatible CPU created from scratch. Currently work in progress to execute first instructions.

CPU includes two stage (fetch, execute) pipeline with I-Cache and D-Cache. Cores is MMU capable making it is theoretically linux capable with small adjustments to kernel.


# State
TODO:
* Write scratchmem
* Rewrite PTW for new ArmleoBUS
* Rewrite CACHE from scratch
* Test BR Cond
* Write Fetch
* Write execute
* Write Top


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
