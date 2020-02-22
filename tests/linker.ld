OUTPUT_FORMAT(elf32-littleriscv)

MEMORY
{
	rom	: ORIGIN = 0x00000000,
		  LENGTH = 1024
	ram	: ORIGIN = 0x00000400,
		  LENGTH = 1024
}

SECTIONS
{
	__start = .;
	.text : AT (__start) {
		_stext = .;
		*(.text)
		_etext = .;
		*(.lit)
		*(.shdata)
		_endtext = .;
	}  > rom

	.rodata : {
		*(.rodata);
		*(.rodata.*)
	} > rom

	.data : {
		sdata = .;
		_sdata = .;
		*(.data)
		edata = .;
		_edata = .;
	} > ram

	.bss :
	{
		_bss_start = .;
		*(.bss)
		*(COMMON)
		_bss_end = .;
	} > ram
	__end = .;
}