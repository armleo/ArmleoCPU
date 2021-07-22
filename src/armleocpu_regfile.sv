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
// Filename: armleocpu_regfile.v
// Project:	ArmleoCPU
//
// Purpose:	RegFile is cores register file unit
//
////////////////////////////////////////////////////////////////////////////////

`include "armleocpu_defines.vh"

`TIMESCALE_DEFINE

module armleocpu_regfile(
	input clk,
	input rst_n,

	input				rs1_read,
	input		[4:0]	rs1_addr,
	output     [31:0]	rs1_rdata,

	input				rs2_read,
	input		[4:0]	rs2_addr,
	output     [31:0]	rs2_rdata,
	
	
	input		[4:0]		rd_addr,
	input	   [31:0]		rd_wdata,
	input					rd_write
);

wire write = !rst_n || (rd_write && (rd_addr != 0));
wire [4:0] writeaddress = rst_n ? rd_addr : 0;
wire [31:0] writedata = rst_n ? rd_wdata : 0;


armleocpu_regfile_one_lane #(.ELEMENTS_W(5), .WIDTH(32)) lane0(
	.clk(clk),

	.readaddress(rs1_addr),
	.read(rs1_read),
	.readdata(rs1_rdata),

	.write(write),
	.writeaddress(writeaddress),
	.writedata(writedata)
);



armleocpu_regfile_one_lane #(.ELEMENTS_W(5), .WIDTH(32)) lane1(
	.clk(clk),

	.readaddress(rs2_addr),
	.read(rs2_read),
	.readdata(rs2_rdata),

	.write(write),
	.writeaddress(writeaddress),
	.writedata(writedata)
);


endmodule


`include "armleocpu_undef.vh"

