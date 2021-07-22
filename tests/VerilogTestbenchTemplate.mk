
default: clean test
test: simulate-iverilog synth-yosys lint-verilator
clean: clean-iverilog clean-yosys clean-verilator

include $(PROJECT_DIR)/files.mk
include $(PROJECT_DIR)/tests/IverilogTemplate.mk
include $(PROJECT_DIR)/tests/YosysTemplate.mk
include $(PROJECT_DIR)/tests/VerilatorTemplate.mk
