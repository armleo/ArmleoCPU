TEST_OUT32 = $(addprefix output/,$(addsuffix .out32,$(basename $(wildcard *.S))))
TEST_DUMP = $(addprefix output/,$(addsuffix .dump,$(basename $(wildcard *.S))))
TEST_BIN = $(addprefix output/,$(addsuffix .bin,$(basename $(wildcard *.S))))
TEST_ELF = $(addprefix output/,$(addsuffix .elf,$(basename $(wildcard *.S))))

RISCV_PREFIX ?=  riscv-none-embed-
RISCV_GCC ?= $(RISCV_PREFIX)gcc
RISCV_SIM ?= spike
RISCV_OBJDUMP ?= $(RISCV_PREFIX)objdump --disassemble-all --disassemble-zeroes --section=.text --section=.text.startup --section=.text.init --section=.data
RISCV_OBJCOPY ?= $(RISCV_PREFIX)objcopy
default: all


output/%.out: output/%.elf
	$(RISCV_SIM) --isa=rv64im $< 2> $@

output/%.out32: output/%.elf
	$(RISCV_SIM) --isa=rv32im $< 2> $@

output/%.dump: output/%.elf
	$(RISCV_OBJDUMP) $< > $@

output/%.bin: output/%.elf
	$(RISCV_OBJCOPY) -O binary $< $@

output/%.elf: %.S env/riscv_test.h test_macros.h Makefile
	$(RISCV_GCC) -static -mcmodel=medany -fvisibility=hidden -nostdlib -nostartfiles \
		-march=rv32im -mabi=ilp32 -Ienv/ -Tenv/link.ld \
		$< \
		-o $@
output:
	mkdir output

all: output $(TEST_DUMP) $(TEST_BIN)

clean:
	rm -rf output/*
