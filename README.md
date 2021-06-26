# ArmleoCPU

RISC-V RV32IMA compatible CPU created from scratch.  Work in progress to execute first instructions.

CPU includes pipeline with I-Cache and D-Cache. Cores is MMU capable making it is theoretically Linux capable with small adjustments to kernel.

Core is weak store ordered and multiple cores can be stacked. Besides core, peripherals are provided: PLIC, CLINT, AXI4 Memory and AXI4 Interconnect.

See docs/docs.md for further information

# Deps
It is required to: Install grep, make, gcc (for verilator) verilator, icarus verilog, gtkwave and yosys. All need to be in PATH before running make
For building test firmwares: You need: spike and riscv gnu toolchain https://github.com/riscv/riscv-gnu-toolchain for barebone rv32ima
Note: Its okay to use riscv64-elf-gcc, riscv64-elf-newlib and spike packages in Arch Linux

For Arch Linux see install_deps.arch

# Testing
To run yosys synthesis, all available tests run:
```
source install_deps.arch # Install deps for Arch Linux or Manjaro
make # Run all tests
```
