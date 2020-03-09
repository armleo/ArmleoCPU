netlist=netlist.netlist
simresult=./waveform.lxt2
vvpparams=-lxt2
iverilog=iverilog
vvp=vvp
gtkwave=gtkwave


build: $(netlist)
	
execute: $(simresult)
	
view: $(simresult)
	$(gtkwave) $(simresult)

$(simresult): $(netlist)
	$(vvp) $(netlist) $(vvpparams) > execute_logfile.log
$(netlist): $(files) Makefile
	$(iverilog) -g2012 $(includepaths) -o $(netlist) -DSIMRESULT="\"$(simresult)\"" -DDEBUG $(files) > compile_logfile.log
clean:
	rm -f $(simresult)
	rm -f $(netlist)
	rm -f execute_logfile.log
	rm -f compile_logfile.log