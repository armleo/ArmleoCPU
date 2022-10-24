# ArmleoCPU

ArmleoCPU is RV64GC CPU. The project is currently in progress to execute first instructions.

Core specification:
| Feature               | Status                                        |
|-----------------------|-----------------------------------------------|
| ISA                   | RISC-V RV64GC                                 |
| Supported modes       | Machine, Supervisor, User                     |
| Supported MMU         | SV39 w/ PTW, TLB                              |
| OS Support            | Debian, Fedora, Linux, Barebone               |
| Special features      | Multi core (SMP), weak store ordered, interrupts |
| Cache                 | Multi-way, highly configurable                |
| Cache data            | 64 byte (512 bit)                             |
| Frequency             | >100MHz @ 130nm                               |
| Area                  | 25k LUT4 / 20k FF + BRAMs                     |
| I/D-Bus               | 512-bit custom AXI4 inspired                  |
| Peripheral bus        | 64-bit AXI4-Lite                              |
| Peripherals for Linux | Interconnect, PLIC, CLINT                     |
| I/O on peripheral     | UART, GPIO                                    |
| I/O on I-Bus          | QSPI Flash                                    |
| I/O on I/D-Bus        | BRAM, Custom QSPI chipset bus to FPGA for ASICs |


ArmleoCPU logo:

<img src="docs/Logo.png" alt="ArmleoCPU Logo" width="128"/>

# Status

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
| axi_plic              | Not implemented yet           |
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

