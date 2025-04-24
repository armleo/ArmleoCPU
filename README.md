# ArmleoCPU

ArmleoCPU is RV64GC CPU. The project is currently in progress to execute first instructions.


Roadmap:

Core specification for milestone 1:
| Feature               | Status                                        |
|-----------------------|-----------------------------------------------|
| OS Support            | Linux, Barebone, Buildroot               |
| ISA                   | RV64IA                                        |
| Protection            | Machine, Supervisor SV39, User, PTW, TLB      |
| Special features      | Symmetric multiprocessing (SMP), weak store ordered, interrupts |
| Cache                 | Multi-way, write-through |
| Frequency             | >70MHz @ sky130                               |
| I/D-Bus               |64/128/256/512-bit @ core clock, with AXI5 converter |

Milestone 2:
| Feature               | Status                                        |
|-----------------------|-----------------------------------------------|
| Peripherals for Linux | Interconnect, PLIC, CLINT, UART, GPIO, SPI    |
| Off chip I/O          | Custom external bus interface chipset bus to FPGA for ASICs |

For next release following features are planned:
| Feature               | Status                                        |
|-----------------------|-----------------------------------------------|
| ISA                   | RV64GC                                        |
| OS Support            | Fedora, Debian, Linux, Barebone               |

ArmleoCPU logo:

<img src="docs/Logo.png" alt="ArmleoCPU Logo" width="128"/>

# Status


Known issues:
TODO: PTW needs the debug logic fixed  
TODO: MRET has to clear the MPRV  
TODO: JAL/JALR has to ignore pc LSB bit  
TODO: Check for Clearing the LSB of the addition result in JALR  
TODO: Check the error handling  
TODO: Test atomic instructions  
TODO: Test the non cached bus instructions

the shifts ignore the top most bits, just use the (4, 0) or (5, 0)

**Code Freeze** -> No Changes planned, tested in simulation  
**Work in progress** -> Currently work in progress to implement and fully test  
**Outdated** -> Other modules changed, making this module not compatible and requires significant amount of changes  
**Stalled** -> Requires some other module that is currently outdated/not implemented  

Core features:
| Feature               | Status                    |
|-----------------------|---------------------------|
| ALU                   | Not tested yet            |
| Branch                | Not tested yet            |
| Jumps                 | Not tested yet            |
| MULDIV                | Not implemented yet       |
| TLB                   | Not tested yet            |
| Pagefault             | Not tested yet            |
| PageTableWalker       | Not tested yet            |
| Cache                 | Not tested yet            |
| RegisterSlice         | Not implemented yet       |
| CSR                   | Not tested yet            |
| Execute               | Not implemented yet       |
| Debugger              | Not implemented yet       |
| Load/Store            | Not implemented yet       |
| PMA                   | Not tested yet            |
| PMP                   | Not implemented yet       |
| ISA Verification      | Not implemented yet       |
| CSR Verification      | Not implemented yet       |
| Linux boot tests      | Not implemented yet       |

Peripheral features:
| Feature               | Status                        |
|-----------------------|-------------------------------|
| bram                  | Not implemented yet           |
| clint                 | Not implemented yet           |
| exclusive_monitor     | Not implemented yet           |
| plic              | Not implemented yet           |
| arbiter               | Not implemented yet           |
| interconnect_router   | Not implemented yet           |
| interconnect_arbiter  | Not implemented yet           |
| interconnect          | Not implemented yet           |
| uart                  | Not implemented yet           |
| gpio                  | Not implemented yet           |

Current priority tasks:
```
Make placeholder TLB/PTW for RV64. We dont need it yet
Finish Store operations
Make tobus/frombus converter
Make PMP
Implement rest of CSR operations
Implement AMO operations
Implement EBREAK
Implement misalignment checks for Fetch
Make debug interface
Make debugger
Pass riscv 013 tests for OpenOCD

Implement decoupler for fetch/decode for better FMAX
Implement cache in three cycles instead of two
Write TLB/PTW for RV64 (the TLB needs to have multiple levels)
```

Verification goals:
```
Verilator wrapper with tracing
riscv-tests passing
Dromajo cosimultion using Verilator
```

```
git submodule update --init --recursive

```

# License
All source code for this project is under GPLv3 or later license (see COPYING file and file headers).  
If you want this project under different license contact me.

Verification tests are under BSD 3-clause license and license can be seen in tests/verif_tests folder.

