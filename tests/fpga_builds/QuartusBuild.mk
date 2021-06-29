###############################################################################
# 
# This file is part of ArmleoCPU.
# ArmleoCPU is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
# 
# ArmleoCPU is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
# 
# You should have received a copy of the GNU General Public License
# along with ArmleoCPU.  If not, see <https:#www.gnu.org/licenses/>.
# 
# Copyright (C) 2016-2021, Arman Avetisyan, see COPYING file or LICENSE file
# SPDX-License-Identifier: GPL-3.0-or-later
# 

all: clean smart.log $(PROJECT).asm.rpt $(PROJECT).sta.rpt 

clean:
	rm -rf $(PROJECT).sdc *.rpt *.chg smart.log *.htm *.eqn *.pin *.sof *.pof *.qpf *.qsf *.qws *.summary *.smsg *.jdi *.jdi db incremental_db simulation

map: smart.log $(PROJECT).map.rpt
fit: smart.log $(PROJECT).fit.rpt
asm: smart.log $(PROJECT).asm.rpt
sta: smart.log $(PROJECT).sta.rpt
smart: smart.log

###################################################################
# Executable Configuration
###################################################################

MAP_ARGS = --read_settings_files=on $(addprefix --source=,$(SRCS))

FIT_ARGS = --part=$(PART) --read_settings_files=on
ASM_ARGS =
STA_ARGS =

###################################################################
# Target implementations
###################################################################

$(PROJECT).sdc: timing.sdc
	cp timing.sdc $(PROJECT).sdc

STAMP = echo done >

$(PROJECT).map.rpt: $(SOURCE_FILES) $(PROJECT).sdc
	quartus_map $(MAP_ARGS) $(PROJECT)

$(PROJECT).fit.rpt: $(PROJECT).map.rpt
	quartus_fit $(FIT_ARGS) $(PROJECT)

$(PROJECT).asm.rpt: $(PROJECT).fit.rpt
	quartus_asm $(ASM_ARGS) $(PROJECT)

$(PROJECT).sta.rpt: $(PROJECT).fit.rpt
	quartus_sta $(STA_ARGS) $(PROJECT) 

smart.log: $(ASSIGNMENT_FILES)
	quartus_sh --determine_smart_action $(PROJECT) > smart.log
	

###################################################################
# Project initialization
###################################################################

$(ASSIGNMENT_FILES):
	quartus_sh --prepare -f $(FAMILY) -t $(TOP_LEVEL_ENTITY) $(PROJECT)
	-cat qsf_append >> $(PROJECT).qsf
	-cat $(BOARDFILE) >> $(PROJECT).qsf

###################################################################
# Programming the device
###################################################################

program: $(PROJECT).sof
	quartus_pgm --no_banner --mode=jtag -o "P;$(PROJECT).sof"

program-pof: $(PROJECT).pof
	quartus_pgm --no_banner --mode=jtag -o "BVP;$(PROJECT).pof"
