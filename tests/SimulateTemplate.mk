# accepts defines, includepaths, tbfiles, files
netlist=netlist.netlist
simresult=./dump.vcd
vvpparams=
iverilog=iverilog
vvp=vvp
gtkwave=gtkwave
includepaths+=../../ ../../../core_src/ ../../../peripheral_src/


includepathsI=$(addprefix -I,$(includepaths))

view: $(simresult)
	$(gtkwave) $(simresult)

build: $(netlist)
	
simulate: $(simresult)
test: simulate
	
$(simresult): $(netlist)
	$(vvp) $(netlist) $(vvpparams) | tee execute_logfile.log
	! grep "\!ERROR" execute_logfile.log

$(netlist): $(files) $(tbfiles) Makefile
	$(iverilog) $(iverilog_options) -Winfloop -Wall -g2012 $(includepathsI) -o $(netlist) -D__ICARUS__=1 -DSIMULATION -DSIMRESULT="\"$(simresult)\"" $(defines) $(files) $(tbfiles) 2>&1 | tee compile_logfile.log
	! grep "error:" compile_logfile.log
lint: $(files) Makefile
	verilator --lint-only -Wall $(verilator_options) $(includepathsI) $(files) -DSIMRESULT="\"$(simresult)\"" 2>&1 | tee verilator.lint.log
clean:
	rm -rf *.vcd *.lxt2 xvlog* xsim* verilator.lint.log compile_logfile.log execute_logfile.log $(netlist)