# ArmleoCPU

RISC-V RV32IMA compatible CPU created from scratch.  Work in progress to execute first instructions.

CPU includes pipeline with I-Cache and D-Cache. Cores is MMU capable making it is theoretically Linux capable with small adjustments to kernel.

Core is weak store ordered and multiple cores can be stacked. Besides core, peripherals are provided: PLIC, CLINT, AXI4 Memory and AXI4 Interconnect.

See docs/docs.md for further information