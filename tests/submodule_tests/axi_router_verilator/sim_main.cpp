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
////////////////////////////////////////////////////////////////////////////////


#include <Varmleosoc_axi_router.h>
#define TRACE
#define TOP_MODULE_DECLARATION Varmleosoc_axi_router * armleosoc_axi_router;
#define TOP_ALLOCATION armleosoc_axi_router = new Varmleosoc_axi_router;
#include "verilator_template_header.cpp"

#include <bitset>

/*
#define AXI_ADDR_TYPE uint64_t
#define AXI_ID_TYPE uint8_t
#define AXI_DATA_TYPE uint32_t
#define AXI_STROBE_TYPE uint8_t

#define AXI_SIMPLIFIER_TEMPLATED axi_simplifier<AXI_ADDR_TYPE, AXI_ID_TYPE, AXI_DATA_TYPE, AXI_STROBE_TYPE>


axi_addr<AXI_ADDR_TYPE, AXI_ID_TYPE> * ar;
axi_r<AXI_ID_TYPE, AXI_DATA_TYPE> * r;

axi_addr<AXI_ADDR_TYPE, AXI_ID_TYPE> * aw;
axi_w<AXI_DATA_TYPE, AXI_STROBE_TYPE> * w;
axi_b<AXI_ID_TYPE> * b;
axi_interface<AXI_ADDR_TYPE, AXI_ID_TYPE, AXI_DATA_TYPE, AXI_STROBE_TYPE> * interface;

AXI_SIMPLIFIER_TEMPLATED * simplifier;
*/

#include "verilator_template_main_start.cpp"
    //utils_init();
    
    TOP->rst_n = 0;
    next_cycle();
    TOP->rst_n = 1;
    
    // TODO: Test simple read to region 0/1/2/3
    // TODO: Test simple read outside of regions
    // TODO: 
    // TODO: Test simple write
    // TODO: 

#include <verilator_template_footer.cpp>
