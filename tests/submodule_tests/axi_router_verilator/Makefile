\
cpp_files=sim_main.cpp
defines=

verilator_options=-GADDR_WIDTH=16

verilator_options+=-GOPT_NUMBER_OF_CLIENTS=4
verilator_options+=-GREGION_COUNT=4
verilator_options+=-GID_WIDTH=2

# REGION_CLIENT_NUM = {2'b01, 2'b00, 2'b10, 2'b11}
verilator_options+=-GREGION_CLIENT_NUM=8\'b01_00_10_11

# REGION_BASE_ADDRS = 
verilator_options+=-GREGION_BASE_ADDRS=64\'h2000_1000_3000_4000

# REGION_END_ADDRS =
verilator_options+=-GREGION_END_ADDRS=64\'h3000_2000_4000_5000

# REGION_CLIENT_BASE_ADDRS =
verilator_options+=-GREGION_CLIENT_BASE_ADDRS=64\'h2000_1000_3000_4000

top=armleosoc_axi_router
files=$(ROUTER_FILES)

include $(PROJECT_DIR)/tests/VerilatorCXXTestbenchTemplate.mk
#include $(PROJECT_DIR)/tests/VerilatorYosysTemplate.mk
