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

`define TIMEOUT 10000
`define SYNC_RST
`define CLK_HALF_PERIOD 1

`include "template.vh"

localparam WIDTH = 16;
localparam ELEMENTS_W = 3;

reg [ELEMENTS_W-1:0] address;
reg read;
wire[WIDTH-1:0] readdata;
reg write;
reg [WIDTH-1:0] writedata;

armleocpu_mem_1rw #(
	.ELEMENTS_W(ELEMENTS_W),
	.WIDTH(WIDTH)
) dut (
	.*
);

task write_req;
input [ELEMENTS_W-1:0] addr;
input [WIDTH-1:0] dat;
begin
	write = 1;
	writedata = dat;
	address = addr;
end
endtask


task read_req;
input [ELEMENTS_W-1:0] addr;
begin
	read = 1;
	address = addr;
end
endtask

task next_cycle;
begin
	@(negedge clk);
	read = 0;
	write = 0;
end
endtask

reg [WIDTH-1:0] mem[2**ELEMENTS_W -1:0];

localparam DEPTH = 2**ELEMENTS_W;

reg [ELEMENTS_W-1:0] word;

integer i;
initial begin
	read = 0;
	write = 0;
	next_cycle();

	$display("Test Read should be keeping its value after write");
	write_req(0, 0);
	next_cycle();

	read_req(0);
	next_cycle();
	`assert_equal(readdata, 0)

	write_req(0, 1);
	next_cycle();
	`assert_equal(readdata, 0)

	next_cycle();
	`assert_equal(readdata, 0)


	$display("Test write multiple data");
	for(i = 0; i < 2**ELEMENTS_W; i = i + 1) begin
		mem[i] = $random % (2**WIDTH);
		write_req(i, mem[i]);
		next_cycle();

		if(i < 10)
			$display("mem[%d] = 0x%x", i, mem[i]);
	end

	$display("Test read multiple data after modification");
	for(i = 0; i < 2**ELEMENTS_W; i = i + 1) begin
		read_req(i);
		next_cycle();
		`assert_equal(readdata, mem[i]);
		write_req(i, 0);
		next_cycle();
		`assert_equal(readdata, mem[i]);

		write_req(i, 1);
		next_cycle();
		`assert_equal(readdata, mem[i]);

		write_req(i, mem[i]);
		next_cycle();
	end

	$display("Test random read write");

	for(i = 0; i < 1000; i = i + 1) begin
		word = $urandom() % (DEPTH);
		
		if($urandom() & 1) begin
			mem[word] = $urandom() & ((2 ** WIDTH) - 1);
			$display("Random write addr = %d, data = 0x%x", word, mem[word]);
			
			write_req(word, //addr
					mem[word]
				);
			next_cycle();
		end else begin
			
			read_req(word);
			next_cycle();
			$display("Random read addr = %d, data = 0x%x, got value: 0x%x", word, mem[word], readdata);
			`assert_equal(readdata, mem[word]);
		end
	end
	$finish;
end


endmodule