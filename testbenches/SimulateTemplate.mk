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
	-$(vvp) $(netlist) $(vvpparams) &> execute_logfile.log
$(netlist): $(files) Makefile
	-$(iverilog) -Wall -g2005 $(includepathsI) -o $(netlist) -D__ICARUS__=1 -DSIMRESULT="\"$(simresult)\"" -DDEBUG $(files) $(tbfiles) &> compile_logfile.log
lint:
	-verilator --lint-only -Wall $(includepathsI) $(files) -DSIMRESULT="\"$(simresult)\"" &> verilator.lint.log
lint-xvlog:
	-xvlog $(files) $(addprefix -i ,$(includepaths)) &> xvlog.lint.log
clean:
	rm -f *.lxt2
	rm -f *.vcd
	rm -f $(netlist)
	rm -f execute_logfile.log
	rm -f compile_logfile.log
	rm -f verilator.lint.log