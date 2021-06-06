#ifndef RISCV_TEST_HEADER
#define RISCV_TEST_HEADER

#define INIT_XREG                                                       \
  li x1, 0;                                                             \
  li x2, 0;                                                             \
  li x3, 0;                                                             \
  li x4, 0;                                                             \
  li x5, 0;                                                             \
  li x6, 0;                                                             \
  li x7, 0;                                                             \
  li x8, 0;                                                             \
  li x9, 0;                                                             \
  li x10, 0;                                                            \
  li x11, 0;                                                            \
  li x12, 0;                                                            \
  li x13, 0;                                                            \
  li x14, 0;                                                            \
  li x15, 0;                                                            \
  li x16, 0;                                                            \
  li x17, 0;                                                            \
  li x18, 0;                                                            \
  li x19, 0;                                                            \
  li x20, 0;                                                            \
  li x21, 0;                                                            \
  li x22, 0;                                                            \
  li x23, 0;                                                            \
  li x24, 0;                                                            \
  li x25, 0;                                                            \
  li x26, 0;                                                            \
  li x27, 0;                                                            \
  li x28, 0;                                                            \
  li x29, 0;                                                            \
  li x30, 0;                                                            \
  li x31, 0;                                                            \
  la t0, trap_vector;                                                   \
  csrw mtvec, t0;                                                       

#define TESTNUM gp

#define RVTEST_PASS RVTEST_CODE_END
        

#define RVTEST_FAIL                                                     \
        fence;                                                          \
        ebreak;

#define RVTEST_RV64U
#define RVTEST_RV32U                                                    \
  trap_vector:                                                          \
    RVTEST_FAIL
#define RVTEST_RV32M                                                    \
  trap_vector:                                                          \
    la t5, mtvec_handler;                                               \
    beqz t5, 1f;                                                        \
    jr t5;                                                              \

#define RVTEST_CODE_BEGIN                                               \
        .section .text.init;                                            \
        .align  6;                                                      \
        .weak stvec_handler;                                            \
        .weak mtvec_handler;                                            \
        .globl _start;                                                  \
_start:                                                                 \
        INIT_XREG                                                       \
        j reset_vector;                                                 \
        .align 2;                                                       \
reset_vector:                                                           \
        

#define RVTEST_ENABLE_SUPERVISOR                                        \
  li a0, MSTATUS_MPP & (MSTATUS_MPP >> 1);                              \
  csrs mstatus, a0;                                                     \
  li a0, SIP_SSIP | SIP_STIP;                                           \
  csrs mideleg, a0;                                                     

#define RVTEST_RV32S                                                    \
  RVTEST_ENABLE_SUPERVISOR;                                             \
  trap_vector:                                                          \
    la t5, stvec_handler;                                               \
    beqz t5, 1f;                                                        \
    jr t5;                                                              


#define RVTEST_CODE_END                                                 \
        li a0, 0xD01E4A55;                                              \
        sw a0, 0(x0);                                                   \
        fence;                                                          \
        ebreak;

#define RVTEST_DATA_BEGIN                                               \
        .pushsection .tohost,"aw",@progbits;                            \
        .align 6; .global tohost; tohost: .dword 0;                     \
        .align 6; .global fromhost; fromhost: .dword 0;                 \
        .popsection;                                                    \
        .align 4; .global begin_signature; begin_signature:

#define RVTEST_DATA_END .align 4; .global end_signature; end_signature:

#endif