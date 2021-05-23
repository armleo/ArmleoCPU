# Branch status
This branch is developlemnt only. This branch is not supported, use two_stage branch instead


# ArmleoCPU

RISC-V RV32IM compatible CPU created from scratch.  
Currently executes all RV32IM Tests. WIP to implement Atomics and run Linux for the first time.

CPU includes two stage (fetch, execute) pipeline with I-Cache and D-Cache. Cores is MMU capable making it is theoretically linux capable with small adjustments to kernel.


# State
Current state can be seen in "Issues". Each issue is tracking one change

# Notes
All memory cells are read first in this design

# Target
This branch targets design and developlemnt of multi stage pipelined RISC-V CPU capable of booting of Linux, because current two_stage is unable to do so.

# Installation

```
make # TODO: Fix make to actualy work :D
```




Y - Code freeze

|Done     |Test     |Feature          |EstimatedTime (days) |
|:-------:|:-------:|:---------------:|:-------------------:|
|N        |N        |CCX              |4                    |
|N        |N        |Top              |1                    |
|N        |N        |MemIO            |0.5                  |
|Y        |Y        |SRAMCell         |0.5                  |
|N        |N        |Cache            |1                    |
|N        |N        |Cache/PTW        |1                    |
|Y        |Y        |Cache/TLB        |1                    |
|Y        |Y        |Cache/LoadGen    |0.5                  |
|Y        |Y        |Cache/StoreGen   |0.5                  |
|N        |N        |Cache/Atomics    |1                    |
|W        |WIP      |Cache/BackStorage|2                    |
|N        |N        |Cache/Coherency  |2                    |
|N        |N        |CSR              |2                    |
|Y        |Partial  |ALU              |                     |
|N        |N        |MULDIV           |2                    |
|Y        |Y        |RegFile          |                     |
|N        |N        |Fetch            |2                    |
|N        |N        |Decode           |2                    |
|N        |N        |Execute          |1                    |
|N        |N        |Execute/ImmGen   |0.5                  |
|N        |N        |Execute/Atomics  |0.5                  |
|N        |N        |Execute/CSR      |0.5                  |
|N        |N        |Execute/MULDIV   |0.5                  |
|N        |N        |MemoryStage      |1.5                  |
|N        |N        |WriteBack        |0.5                  |
|         |         |Total            |27.5                 |

