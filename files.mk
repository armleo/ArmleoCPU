
.PHONE: files_list_print

ifeq ($(FILES_MK_INCLUDED),)

FILES_MK_INCLUDED=y

files?=$(wildcard $(PROJECT_DIR)/src/*.sv)
includefiles=$(wildcard $(PROJECT_DIR)/src/*.vh) $(wildcard $(PROJECT_DIR)/tests/*.vh)
makefiles=$(wildcard $(PROJECT_DIR)/**/*.mk) $(wildcard $(PROJECT_DIR)/**/**/*/Makefile) $(wildcard $(PROJECT_DIR)/**/*/Makefile) $(PROJECT_DIR)/Makefile
includepaths=$(PROJECT_DIR)/tests $(PROJECT_DIR)/src
includepathsI=$(addprefix -I,$(includepaths))

MEM_CELLS_1RW_FILES=$(PROJECT_DIR)/src/armleocpu_mem_1rw.sv
MEM_CELLS=$(PROJECT_DIR)/src/armleocpu_mem_1rwm.sv $(MEM_CELLS_1RW_FILES)
MEM_CELLS_1RWM_FILES=$(MEM_CELLS)

DIVIDER_FILES=$(PROJECT_DIR)/src/armleocpu_unsigned_divider.sv
REGFILE_FILES=$(PROJECT_DIR)/src/armleocpu_regfile.sv $(PROJECT_DIR)/src/armleocpu_regfile_one_lane.sv
MULTIPLIER_FILES=$(PROJECT_DIR)/src/armleocpu_multiplier.sv

JTAG_TAP_FILES=$(PROJECT_DIR)/src/armleocpu_jtag_tap.sv
JTAG_DTM_FILES=$(JTAG_TAP_FILES) $(PROJECT_DIR)/src/armleocpu_jtag_dtm.sv
FETCH_FILES=$(PROJECT_DIR)/src/armleocpu_fetch.sv
BRCOND_FILES=$(PROJECT_DIR)/src/armleocpu_brcond.sv
EXECUTE_FILES=$(PROJECT_DIR)/src/armleocpu_execute.sv $(BRCOND_FILES) $(ALU_FILES) $(MULTIPLIER_FILES) $(DIVIDER_FILES)
DECODE_FILES=$(PROJECT_DIR)/src/armleocpu_decode.sv
CSR_FILES=$(PROJECT_DIR)/src/armleocpu_csr.sv

TLB_FILES=$(PROJECT_DIR)/src/armleocpu_tlb.sv $(MEM_CELLS)
STOREGEN_FILES=$(PROJECT_DIR)/src/armleocpu_storegen.sv
REGISTER_SLICE_FILES=$(PROJECT_DIR)/src/armleocpu_register_slice.sv
PTW_FILES=$(PROJECT_DIR)/src/armleocpu_ptw.sv
LOADGEN_FILES=$(PROJECT_DIR)/src/armleocpu_loadgen.sv
CACHE_PAGEFAULT_FILES=$(PROJECT_DIR)/src/armleocpu_cache_pagefault.sv
CACHE_FILES=$(PROJECT_DIR)/src/armleocpu_cache.sv $(PTW_FILES) $(CACHE_PAGEFAULT_FILES) $(LOADGEN_FILES) $(STOREGEN_FILES) $(TLB_FILES) $(REGISTER_SLICE_FILES)


AXI2SIMPLE_CONVERTER_FILES=$(PROJECT_DIR)/src/armleosoc_axi2simple_converter.sv
CLINT_FILES=$(PROJECT_DIR)/src/armleosoc_axi_clint.sv $(AXI2SIMPLE_CONVERTER_FILES)
BRAM_FILES=$(MEM_CELLS) $(PROJECT_DIR)/src/armleosoc_axi_bram.sv
EXCLUSIVE_MONITOR_FILES=$(PROJECT_DIR)/src/armleosoc_axi_exclusive_monitor.sv
ROUTER_FILES=$(PROJECT_DIR)/src/armleosoc_axi_router.sv $(PROJECT_DIR)/src/armleosoc_axi_write_router.sv $(PROJECT_DIR)/src/armleosoc_axi_read_router.sv
AXI_REGISTER_SLICE_FILES=$(PROJECT_DIR)/src/armleosoc_axi_register_slice.sv $(REGISTER_SLICE_FILES)

endif