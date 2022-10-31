# ArmleoCPU

ArmleoCPU is RV64GC CPU. The project is currently in progress to execute first instructions.


Roadmap:

Core specification for milestone 1:
| Feature               | Status                                        |
|-----------------------|-----------------------------------------------|
| OS Support            | Linux, Barebone               |
| ISA                   | RV32IA                                       |
| Protection            | Machine, Supervisor SV32, User, PTW, TLB      |
| Special features      | Multi core (SMP), weak store ordered, interrupts |
| Cache                 | Multi-way, 64 byte, highly configurable, write-through |
| Frequency             | >100MHz @ 130nm                               |
| I/D-Bus               | up to 512-bit @ core clock, custom bus, with AXI5 converter |
| Peripheral bus        | 64-bit AXI4-Lite                              |

Milestone 2:
| Feature               | Status                                        |
|-----------------------|-----------------------------------------------|
| ISA                   | RV64IA                                        |
| Protection            | Machine, Supervisor SV39, User, PTW, TLB      |

Milestone 3:
| Feature               | Status                                        |
|-----------------------|-----------------------------------------------|
| Peripherals for Linux | Interconnect, PLIC, CLINT, UART, GPIO, SPI    |
| Off chip I/O          | Custom QSPI chipset bus to FPGA for ASICs |

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
the shifts ignore the top most bits, just use the (4, 0) or (5, 0)

**Code Freeze** -> No Changes planned, tested in simulation  
**Work in progress** -> Currently work in progress to implement and fully test  
**Outdated** -> Other modules changed, making this module not compatible and requires significant amount of changes  
**Stalled** -> Requires some other module that is currently outdated/not implemented  

Core features:
| Feature               | Status                    |
|-----------------------|---------------------------|
| ALU                   | Not implemented yet       |
| Branch                | Not implemented yet       |
| Jumps                 | Not implemented yet       |
| MULDIV                | Not implemented yet       |
| TLB                   | Not implemented yet       |
| Pagefault             | Not implemented yet       |
| LoadGen               | Not implemented yet       |
| StoreGen              | Not implemented yet       |
| PageTableWalker       | Not implemented yet       |
| Cache                 | Not implemented yet       |
| RegisterSlice         | Not implemented yet       |
| CSR                   | Not implemented yet       |
| Execute               | Not implemented yet       |
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

# License
All source code for this project is under GPLv3 or later license (see COPYING file and file headers).  
If you want this project under different license contact me.

Verification tests are under BSD 3-clause license and license can be seen in tests/verif_tests folder.

