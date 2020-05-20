#include "riscv_test.h"

RVTEST_RV32U        # Define TVM used by program.

# Test code region.
RVTEST_CODE_BEGIN   # Start of test code.
        addi x0, x0, 0;
        RVTEST_PASS           # Signal success.
RVTEST_CODE_END     # End of test code.

RVTEST_DATA_BEGIN   # Start of test output data region.
result:
        .dword -1
RVTEST_DATA_END     # End of test output data region.
