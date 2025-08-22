
tbfiles=cache_tb.v $(PROJECT_DIR)/src/armleosoc_axi_bram.sv $(EXCLUSIVE_MONITOR_FILES) $(ROUTER_FILES)
defines+=-DDEBUG_CACHE -DFORMAL_RULES
top=armleocpu_cache
files=$(CACHE_FILES)

include $(PROJECT_DIR)/tests/VerilogTestbenchTemplate.mk
