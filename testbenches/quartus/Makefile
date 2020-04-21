###################################################################
# Project Configuration: 
# 
# Specify the name of the design (project) and the Quartus II
# Settings File (.qsf)
###################################################################

PROJECT = corevx_tlb_testbuild
TOP_LEVEL_ENTITY = corevx_tlb
ASSIGNMENT_FILES = $(PROJECT).qpf $(PROJECT).qsf

###################################################################
# Part, Family, Boardfile DE1 or DE2
FAMILY = "Cyclone III"
PART = EP3C40F780C8
BOARDFILE = nexus5_pins
###################################################################

###################################################################
# Setup your sources here
SRCS = ../../src/corevx_tlb.sv ../../src/mem_1w1r.sv timing.sdc

###################################################################
# Main Targets
#
# all: build everything
# clean: remove output files and database
# program: program your device with the compiled design
###################################################################
include ../QuartusBuild.mk
