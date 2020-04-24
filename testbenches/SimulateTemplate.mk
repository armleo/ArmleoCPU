# accepts defines, includepaths, tbfiles, files
netlist=netlist.netlist
simresult=./waveform.vcd
vvpparams=
iverilog=iverilog
vvp=vvp
gtkwave=gtkwave
includepaths+=../ ../../src/includes/


includepathsI=$(addprefix -I,$(includepaths))

view: $(simresult)
	$(gtkwave) $(simresult)

build: $(netlist)
	
execute: $(simresult)
	
$(simresult): $(netlist) ../clk_gen_template.svh ../sync_clk_gen_template.svh ../SimulateTemplate.mk ../assert.svh Makefile
	-$(vvp) $(netlist) $(vvpparams) 2>&1 | tee execute_logfile.log
$(netlist): $(files) $(tbfiles) Makefile
	-$(iverilog) -Wall -g2012 $(includepathsI) -o $(netlist) -D__ICARUS__=1 -DSIMRESULT="\"$(simresult)\"" $(defines) $(files) $(tbfiles) 2>&1 | tee compile_logfile.log
lint: $(files) Makefile
	-verilator --lint-only -Wall $(includepathsI) $(files) -DSIMRESULT="\"$(simresult)\"" 2>&1 | tee verilator.lint.log
lint-xvlog: $(files) Makefile
	-xvlog $(files) $(addprefix -i ,$(includepaths)) 2>&1 | tee xvlog.lint.log
clean:
	rm -rf *.vcd *.lxt2 xvlog* xsim* verilator.lint.log compile_logfile.log execute_logfile.log $(netlist)