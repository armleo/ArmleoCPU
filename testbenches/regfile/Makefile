netlist=netlist
simresult=../build/tests/regfile/waveform.lxt2
vvpparams=-lxt2
iverilog=iverilog
files=armleocpu_regfile_tb.sv ../../armleocpu_regfile.sv
vvp=vvp
gtkwave=gtkwave
build: $(netlist)
	
execute: $(simresult)

view: $(simresult)
	$(gtkwave) $(simresult)
$(simresult): $(netlist)
	$(vvp) $(netlist) -lxt2 > logfile.txt
$(netlist): $(files) Makefile
	$(iverilog) -g2012  -o $(netlist) -DPERIODS=100 -DSIMRESULT="\"$(simresult)\"" -DDEBUG $(files)
clean:
	rm -f $(simresult)
	rm -f $(netlist)
	rm -f logfile.txt