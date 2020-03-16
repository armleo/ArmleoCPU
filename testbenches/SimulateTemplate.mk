netlist=netlist.netlist
simresult=./waveform.lxt2
vvpparams=-lxt2
iverilog=iverilog
vvp=vvp
gtkwave=gtkwave


view: $(simresult)
	$(gtkwave) $(simresult)

build: $(netlist)
	
execute: $(simresult)
	
$(simresult): $(netlist) ../clk_gen_template.svh ../sync_clk_gen_template.svh ../SimulateTemplate.mk Makefile
	$(vvp) $(netlist) $(vvpparams) > execute_logfile.log
$(netlist): $(files) Makefile
	$(iverilog) -g2012 $(includepaths) -o $(netlist) -DSIMRESULT="\"$(simresult)\"" -DDEBUG $(files) > compile_logfile.log
clean:
	rm -f $(simresult)
	rm -f $(netlist)
	rm -f execute_logfile.log
	rm -f compile_logfile.log