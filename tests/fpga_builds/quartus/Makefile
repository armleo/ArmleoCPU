
###################################################################
# Project Configuration: 
# 
# Specify the name of the design (project) and the Quartus II
# Settings File (.qsf)
###################################################################

PROJECT = armleocpu_testbuild
TOP_LEVEL_ENTITY = armleocpu
ASSIGNMENT_FILES = $(PROJECT).qpf $(PROJECT).qsf

###################################################################
# Part, Family, Boardfile DE1 or DE2
FAMILY = "Cyclone III"
PART = EP3C40F780C8
BOARDFILE = nexus5_pins
###################################################################

###################################################################
# Setup your sources here
SRCS = timing.sdc
SRCS += ../../src/mem_1w1r.v
SRCS += ../../src/armleocpu_multiplier.v
SRCS += ../../src/armleocpu_unsigned_divider.v 
SRCS += ../../src/armleocpu_alu.v 
SRCS += ../../src/armleocpu_brcond.v 
SRCS += ../../src/armleocpu_cache_pagefault.v 
SRCS += ../../src/armleocpu_ptw.v 
SRCS += ../../src/armleocpu_tlb.v 
SRCS += ../../src/armleocpu_loadgen.v 
SRCS += ../../src/armleocpu_regfile.v 
SRCS += ../../src/armleocpu_storegen.v 
SRCS += ../../src/armleocpu_csr.v 
SRCS += ../../src/armleocpu_execute.v 
SRCS += ../../src/armleocpu_fetch.v 
SRCS += ../../src/armleocpu_cache.v 
SRCS += ../../src/armleocpu.v


include ../QuartusBuild.mk
