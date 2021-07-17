# ArmleoCPU

RISC-V RV32IMA compatible CPU created from scratch.  Work in progress to execute first instructions.

CPU includes pipeline with I-Cache and D-Cache. Cores also includes MMU making it is theoretically Linux capable with small adjustments to kernel.

Core is weak store ordered and multiple cores can be connected together for simultaneous multi processing (SMP).
Besides core, common peripherals are provided: PLIC, CLINT, AXI4 Memory and AXI4 Interconnect and many more.

Building minimal Linux capable core is target for this project.

See docs/docs.md for further information

This branch is partial rewrite to fix this bugs, improve perfomance and add SMP support and finally boot Linux.

# ArmleoPC
This core is used in ArmleoPC to boot Linux. See: https://github.com/armleo/ArmleoPC

# Testing
Note: Docker installation is required. User should be in docker group.

To run all available tests run:

```bash
make docker-all # Run all tests inside docker image
```

To activate docker image in interactive mode:
```bash
make interactive # Run docker in interactive mode
make # run all tests inside
```

# License
All source code for this project is under GPLv3 or later license (see COPYING file and file headers).  
If you want this project under different license contact me.

Verification tests are under BSD 3-clause license and license can be seen in tests/verif_tests folder.

