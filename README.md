# ArmleoCPU

ArmleoCPU is RV64GC CPU. The project is currently in progress to execute first instructions.


Roadmap:

Core specification for milestone 1: Run Doom
| Feature               | Status                                        |
|-----------------------|-----------------------------------------------|
| ISA                   | RV64IA                                        |
| Protection            | Machine, Supervisor SV39, User, PTW, TLB      |
| Special features      | Symmetric multiprocessing (SMP), Total Store Order, interrupts |
| Cache                 | L1 Cache up to 32KB, L3 Cache with cache coherency |
| Frequency             | >70MHz @ sky130                               |
| I/D-Bus               | 512-bit @ core clock |

Milestone 2: Boot linux
| Feature               | Status                                        |
|-----------------------|-----------------------------------------------|
| OS Support            | Linux, Barebone, Buildroot               |
| Peripherals for Linux | Interconnect, PLIC, CLINT, UART, GPIO, SPI    |
| Off chip I/O          | Custom external bus interface chipset bus to FPGA for ASICs |

Milestone 3: Boot desktop distrubitions without recompilation
| Feature               | Status                                        |
|-----------------------|-----------------------------------------------|
| ISA                   | RVA23                                         |
| OS Support            | Fedora, Debian, Linux, Barebone               |

ArmleoCPU logo:

<img src="docs/Logo.png" alt="ArmleoCPU Logo" width="128"/>

# Status


Current tasks:
- Write L3 Cache
  - Follows the ACE-like architecture.
  - Has split directory/victim storage
- Write L1 Cache
- Get TLB working
- MRET has to clear the MPRV  
- JAL/JALR has to ignore pc LSB bit  
- Check for Clearing the LSB of the addition result in JALR  
- Create reset generation module
- Create the debug module
- Create the debug transport module (JTAG)

Core features:
| Feature               | Status                    |
|-----------------------|---------------------------|
| L3Cache               | WIP                       |
| TLB                   | WIP                       |
| PMA                   | WIP                       |
| PMP                   | WIP                       |
| L1Cache               | WIP                       |
| ALURetire             | WIP                       |
| BranchRetire          | WIP                       |
| JumpsRetire           | WIP                       |
| LoadStoreRetire       | WIP                       |
| Pagefault             | WIP                       |
| PageTableWalker       | WIP                       |
| CSR                   | Not tested yet            |
| Debugger              | Not implemented yet       |
| MULDIV                | Not implemented yet       |
| ISA Verification      | Not implemented yet       |
| Cosimulation          | Not implemented yet       |
| CSR Verification      | Not implemented yet       |
| Linux boot tests      | Not implemented yet       |
| clint                 | Not implemented yet           |
| plic                  | Not implemented yet           |
| interconnect_router   | Not implemented yet           |
| interconnect          | Not implemented yet           |
| uart                  | Not implemented yet           |
| gpio                  | Not implemented yet           |
| spi                   | Not implemented yet           |
| qspi flash            | Not implemented yet           |
| bram                  | Done                          |
| interconnect_arbiter  | WIP                           |
| ALUExecute            | Done                      |
| BranchExecute         | Done                      |
| JumpsExecute          | Done                      |
| LoadStoreExecute      | Done                      |

# Licenses
Verification tests are under BSD 3-clause license and license can be seen in tests/verif_tests folder.

