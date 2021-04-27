# Branch status
This branch is developlemnt only. This branch is not supported, use two_stage branch instead


# ArmleoCPU

RISC-V RV32IM compatible CPU created from scratch.  
Currently executes all RV32IM Tests. WIP to implement Atomics and run Linux for the first time.

CPU includes two stage (fetch, execute) pipeline with I-Cache and D-Cache. Cores is MMU capable making it is theoretically linux capable with small adjustments to kernel.


# State
Current state can be seen in "Issues". Each issue is tracking one change


# Target
This branch targets design and developlemnt of multi stage pipelined RISC-V CPU capable of booting of Linux, because current two_stage is unable to do so.

# Installation

```
make # TODO: Fix make to actualy work :D
```
