# ArmleoCPU

RISC-V RV32I compatible CPU created from scratch using chisel. Currently work in progress to execute first instructions.

CPU includes two stage (fetch, execute) pipeline with I-Cache and D-Cache. Cores is MMU capable making it theoretically linux capable with small adjustments to kernel.