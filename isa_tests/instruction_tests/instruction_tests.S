
.text
	addi sp, x0, 0x100
	addi x1, x0, 0xFF
	addi x2, x0. 1
	add x1, x2, x1
	
	bne sp, x1, fail
1: j 1b
	
fail:
	ebreak
