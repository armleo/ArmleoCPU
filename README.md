# ArmleoCPU

RISC-V RV32IMA compatible CPU created from scratch.  Work in progress to execute first instructions.

CPU includes pipeline with I-Cache and D-Cache. Cores also includes MMU making it is theoretically Linux capable with small adjustments to kernel.

Core is weak store ordered and multiple cores can be connected together for simultaneous multi processing (SMP).
Besides core, common peripherals are provided: PLIC, CLINT, AXI4 Memory and AXI4 Interconnect and many more.

Building minimal Linux capable core is target for this project.

See docs/docs.md for further information

0.0.1 branch is first implementation with bugged interrupts and branching. 0.0.2 is partial rewrite to fix this bugs and improve perfomance and add SMP support and finally boot Linux.

# Deps
It is required to: Install grep, make, gcc (for verilator) verilator, Icarus Verilog, gtkwave and yosys. Yosys, Verilator, Icarus Verilog need to be latest stable release. Most likely your Linux distribution does not come with latest stable release, so use Arch Linux or Manjaro.

All need to be in PATH before running make.  

For building test firmwares: You need: spike and riscv gnu toolchain https://github.com/riscv/riscv-gnu-toolchain for barebone rv32ima  
Note: Its okay to use prebuilt packages. You need newlib and linux variants for RV32IMA architecture.

For Arch Linux see install_deps.arch  

In the future this flow will be replaced with docker image capable of testing the core and building test firmware.

# Testing
To run yosys synthesis, all available tests run:
```
source install_deps.arch # Install deps for Arch Linux or Manjaro
make # Run all tests
```

# License
All source code for this project is under GPLv3 or later license (see COPYING file and file headers).  
If you want this project under different license contact me.

Verification tests are under BSD 3-clause license and license can be seen in tests/verif_tests folder.

# Bootloaders, emulators, reference implementations (FPGA, ASIC), Linux boot verification flow
Links will be added when repositories become public
