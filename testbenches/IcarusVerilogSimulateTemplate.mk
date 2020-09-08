# accepts defines, includepaths, tbfiles, files
netlist=netlist.netlist
simresult=./waveform.vcd
vvpparams=
iverilog?=iverilog
vvp?=vvp
gtkwave?=gtkwave
includepaths+=../../src/


includepathsI=$(addprefix -I,$(includepaths))

default: view.iverilog

view: view.iverilog
view.iverilog: $(simresult)
	$(gtkwave) $(simresult)

build: build.iverilog
build.iverilog: $(netlist)

execute: execute.iverilog
execute.iverilog: $(simresult)

$(simresult): $(netlist) ../async_rst_clk_gen_template.vh ../sync_rst_clk_gen_template.vh ../IcarusVerilogSimulateTemplate.mk ../assert.vh Makefile
	-$(vvp) $(netlist) $(vvpparams) 2>&1 | tee execute_logfile.log
$(netlist): $(files) $(tbfiles) Makefile
	-$(iverilog) -Wall -g2012 $(includepathsI) -o $(netlist) -D__ICARUS__=1 -DSIMRESULT="\"$(simresult)\"" $(defines) $(files) $(tbfiles) 2>&1 | tee compile_logfile.log
clean: clean.iverilog
clean.iverilog:
	rm -rf *.vcd *.lxt2 xvlog* xsim* verilator.lint.log compile_logfile.log execute_logfile.log $(netlist)