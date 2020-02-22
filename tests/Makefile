PREFIX=riscv-none-embed-
OBJCOPY=$(PREFIX)objcopy
LINKER=$(PREFIX)ld
AS=$(PREFIX)gcc -c
CC=$(PREFIX)gcc
OBJDUMP=$(PREFIX)objdump -x -d
ARCHO=-march=rv32i -mabi=ilp32
FILES=instruction_tests/instruction_tests.S.bin
FILES+=instruction_tests/instruction_tests.S.dump

build: Makefile linker.ld $(FILES)

%.dump: %.elf
	$(OBJDUMP) $< > $@

%.vmem: %.bin
	srec_cat $< -binary -byte-swap 4 -o $@ -VMem

%.bin: %.elf
	$(OBJCOPY) $< -O binary $@

%.elf: %.o linker.ld
	$(LINKER) $< -o $@ -Tlinker.ld

%.S.o: %.S
	$(AS) $(ARCHO) $< -o $@

%.c.elf: %.c entry.S
	$(CC) -Wl,--build-id=none -mno-relax -static -nostdlib -ffreestanding $(ARCHO) -Tlinker.ld --entry 0 -O1 entry.S $< -o $@

clean:
	rm -rf **/*.elf **/*.o **/*.bin **/*.dump