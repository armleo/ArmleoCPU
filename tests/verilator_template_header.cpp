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


#include <verilated.h>
#include <verilated_vcd_c.h>
#include <iostream>


vluint64_t simulation_time = 0;

using namespace std;

#ifdef TRACE
VerilatedVcdC	*m_trace;
#endif

#ifndef TOP_MODULE_DECLARATION
#error "Top module declaration macro definition missing"
#endif

TOP_MODULE_DECLARATION;

bool error_happened;

double sc_time_stamp() {
    return simulation_time;  // Note does conversion to real, to match SystemC
}

void dump_step() {
    simulation_time++;
    #ifdef TRACE
    m_trace->dump(simulation_time);
    #endif
}


void update() {
    TOP->eval();
    dump_step();
}

void posedge() {
    TOP->clk = 1;
    update();
    update();
}

void till_user_update() {
    TOP->clk = 0;
    update();
}
void after_user_update() {
    update();
}

void next_cycle() {
    after_user_update();

    posedge();
    till_user_update();
}

void start_test(string c) {
    cout << "Starting test: " << c << endl;
}

