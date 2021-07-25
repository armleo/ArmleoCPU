////////////////////////////////////////////////////////////////////////////////
// 
// This file is part of ArmleoCPU.
// ArmleoCPU is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// 
// ArmleoCPU is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with ArmleoCPU.  If not, see <https://www.gnu.org/licenses/>.
// 
// Copyright (C) 2016-2021, Arman Avetisyan, see COPYING file or LICENSE file
// SPDX-License-Identifier: GPL-3.0-or-later
// 


#include <Varmleocpu_cache.h>
#define TRACE
#define TOP_MODULE_DECLARATION Varmleocpu_cache * armleocpu_cache;
#define TOP_ALLOCATION armleocpu_cache = new Varmleocpu_cache;
#include "verilator_template_header.cpp"


#include "verilator_template_main_start.cpp"
    cout << "Cache no tests :D" << endl;
#include <verilator_template_footer.cpp>
