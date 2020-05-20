#include "riscv_test.h"

RVTEST_RV32U        # Define TVM used by program.

# Test code region.
RVTEST_CODE_BEGIN   # Start of test code.
        RVTEST_PASS           # Signal success.
fail:
        RVTEST_FAIL
RVTEST_CODE_END     # End of test code.

RVTEST_DATA_BEGIN   # Start of test output data region.
result:
        .dword -1
RVTEST_DATA_END     # End of test output data region.
