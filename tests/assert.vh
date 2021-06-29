
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

`ifndef MAXIMUM_ERRORS
    `define MAXIMUM_ERRORS 1
`endif
integer assert_errors = 0;


`define assert(expr) \
    if ((!(expr)) === 1) begin \
        $display("[%d] !ERROR! ASSERTION FAILED in %m: ", $time, expr); \
        assert_errors = assert_errors + 1; \
        if(assert_errors == `MAXIMUM_ERRORS) \
            $fatal; \
    end


`define assert_equal(signal, value) \
        if ((signal) !== (value)) begin \
            $display("[%d] !ERROR! ASSERTION FAILED in %m: signal(%d) != value(%d)", $time, signal, value); \
            assert_errors = assert_errors + 1; \
            if(assert_errors == `MAXIMUM_ERRORS) \
                $fatal; \
        end