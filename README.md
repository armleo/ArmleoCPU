# ArmleoCPU

RISC-V RV32IMA compatible CPU created from scratch.  Work in progress to execute first instructions.

CPU includes pipeline with I-Cache and D-Cache. Cores is MMU capable making it is theoretically Linux capable with small adjustments to kernel.

Core is weak store ordered and multiple cores can be stacked. Besides core, peripherals are provided: PLIC, CLINT, AXI4 Memory and AXI4 Interconnect.

See docs/docs.md for further information

0.0.1 branch is bugged implementation. 0.0.2 is partial rewrite to fix this bugs.

# Deps
It is required to: Install grep, make, gcc (for verilator) verilator, icarus verilog, gtkwave and yosys. All need to be in PATH before running make
For building test firmwares: You need: spike and riscv gnu toolchain https://github.com/riscv/riscv-gnu-toolchain for barebone rv32ima  
Note: Its okay to use prebuilt packages. You need newlib and linux variants for RV32IMA architecture.

For Arch Linux see install_deps.arch  
For Ubuntu see install_deps.ubuntu  

# Testing
To run yosys synthesis, all available tests run:
```
source install_deps.arch # Install deps for Arch Linux or Manjaro
make # Run all tests
```

# License
All source code for this project is under GPLv3 or later license (see COPYING file and file headers).  
If you want this project under different license contact me arman.avetisyan2000+os@gmail.com.

Verification tests are under BSD 3-clause license and license can be seen in tests/verif_tests folder.  
Berkley Boot Loader is under BSD 3-clause license and license can be seen in bootloader's folder.  
OpenSBI is under BSD 2-clause license and license can be seen in OpenSBI's folder.
