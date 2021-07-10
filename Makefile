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

DOCKER_IMAGE?=armleocpu_tools:current
DOCKER_CMD=docker run -v .:/ArmleoCPU $(DOCKER_IMAGE)

all: clean test

test: tools_image check
	timeout --foreground 1000 $(DOCKER_CMD) "$(MAKE) -C tests"

mount: tools_image
	$(DOCKER_CMD) "bash"

tools_image: tools_repo
	cd toolset && $(MAKE) && cd ..
	

tools_repo:
	git submodule update --init --recursive

clean: 
	rm -rf check.log
	timeout --foreground 1000 $(DOCKER_CMD) "$(MAKE) -C tests clean"

check:
	$(MAKE) --version > check.log
	
