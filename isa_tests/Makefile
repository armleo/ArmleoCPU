TEST_OBJS = $(addsuffix .o,$(basename $(wildcard *.S)))
default: $(TEST_OBJS)
	
%.o: %.S riscv_test.h test_macros.h
	$(RISCV_TOOLCHAIN_PREFIX)gcc -c -march=rv32im -o $@ -DTEST_FUNC_NAME=$(notdir $(basename $<)) \
		-DTEST_FUNC_TXT='"$(notdir $(basename $<))"' -DTEST_FUNC_RET=$(notdir $(basename $<))_ret $<
