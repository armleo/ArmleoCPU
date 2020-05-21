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
SRCS = ../../src/armleocpu.v ../../src/armleocpu_multiplier.v ../../src/armleocpu_alu.v ../../src/armleocpu_brcond.v ../../src/armleocpu_execute.v ../../src/armleocpu_fetch.v ../../src/armleocpu_cache.v ../../src/armleocpu_cache_pagefault.v ../../src/armleocpu_ptw.v ../../src/armleocpu_tlb.v ../../src/armleocpu_loadgen.v ../../src/armleocpu_regfile.v ../../src/armleocpu_storegen.v ../../src/mem_1w1r.v timing.sdc

###################################################################
# Main Targets
#
# all: build everything
# clean: remove output files and database
# program: program your device with the compiled design
###################################################################
include ../QuartusBuild.mk
