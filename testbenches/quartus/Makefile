###################################################################
# Project Configuration: 
# 
# Specify the name of the design (project) and the Quartus II
# Settings File (.qsf)
###################################################################

PROJECT = corevx_cache_testbuild
TOP_LEVEL_ENTITY = corevx_cache
ASSIGNMENT_FILES = $(PROJECT).qpf $(PROJECT).qsf

###################################################################
# Part, Family, Boardfile DE1 or DE2
FAMILY = "Cyclone III"
PART = EP3C40F780C8
BOARDFILE = nexus5_pins
###################################################################

###################################################################
# Setup your sources here
SRCS = ../../src/corevx_cache.sv ../../src/corevx_ptw.sv ../../src/corevx_tlb.sv ../../src/corevx_tlb_way.sv ../../src/corevx_loadgen.sv ../../src/corevx_storegen.sv ../../src/mem_1w1r.sv timing.sdc

###################################################################
# Main Targets
#
# all: build everything
# clean: remove output files and database
# program: program your device with the compiled design
###################################################################
include ../QuartusBuild.mk
