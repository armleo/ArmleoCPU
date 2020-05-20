TEST_OUT32 = $(addsuffix .out32,$(basename $(wildcard *.S)))
TEST_DUMP = $(addsuffix .dump,$(basename $(wildcard *.S)))
TEST_BIN = $(addsuffix .bin,$(basename $(wildcard *.S)))


RISCV_PREFIX ?=  riscv-none-embed-
RISCV_GCC ?= $(RISCV_PREFIX)gcc
RISCV_SIM ?= spike
RISCV_OBJDUMP ?= $(RISCV_PREFIX)objdump --disassemble-all --disassemble-zeroes --section=.text --section=.text.startup --section=.text.init --section=.data
RISCV_OBJCOPY ?= $(RISCV_PREFIX)objcopy
default: all


%.out: %.elf
	$(RISCV_SIM) --isa=rv64im $< 2> $@

%.out32: %.elf
	$(RISCV_SIM) --isa=rv32im $< 2> $@

%.dump: %.elf
	$(RISCV_OBJDUMP) $< > $@

%.bin: %.elf
	$(RISCV_OBJCOPY) -O binary $< $@

%.elf: %.S env/riscv_test.h test_macros.h Makefile
	$(RISCV_GCC) -static -mcmodel=medany -fvisibility=hidden -nostdlib -nostartfiles \
		-march=rv32im -Ienv/ -Tenv/link.ld \
		$< \
		-o $@

all: $(TEST_DUMP) $(TEST_BIN) $(TEST_OUT32)

clean:
	rm -rf *.elf *.out32 *.out *.bin *.dump
