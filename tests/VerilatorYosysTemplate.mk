
test: test-verilator synth-yosys lint-verilator
debug: lint-verilator debug-verilator
clean: clean-verilator clean-yosys

include $(PROJECT_DIR)/files.mk
include $(PROJECT_DIR)/tests/VerilatorTemplate.mk
include $(PROJECT_DIR)/tests/YosysTemplate.mk