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

DOCKER_IMAGE?=armleo/armleocpu_toolset:latest
DOCKER_ARG=-it $(DOCKER_IMAGE)
DOCKER_CMD=docker run -v $(shell pwd):/ArmleoCPU

all: clean test

test: check
	timeout --foreground 1000 $(DOCKER_CMD) -w "/ArmleoCPU/tests" $(DOCKER_ARG) $(MAKE)

mount:
	$(DOCKER_CMD) -w "/ArmleoCPU" $(DOCKER_ARG) "bash"

clean: 
	rm -rf check.log
	timeout --foreground 1000 $(DOCKER_CMD) -w "/ArmleoCPU/tests" $(DOCKER_ARG) $(MAKE) clean

check:
	$(MAKE) --version > check.log
	
