netlist=netlist
simresult=./waveform.lxt2
vvpparams=-lxt2
iverilog=iverilog
files=armleocpu_tlb_tb.sv ../../armleocpu_tlb.sv
vvp=vvp
gtkwave=gtkwave

build: $(netlist)
	
execute: $(simresult)
	
view: $(simresult)
	$(gtkwave) $(simresult)

$(simresult): $(netlist)
	$(vvp) $(netlist) -lxt2 > logfile.txt
$(netlist): $(files) Makefile
	$(iverilog) -g2012  -o $(netlist) -DSIMRESULT="\"$(simresult)\"" -DDEBUG $(files) >> logfile.txt
clean:
	rm -f $(simresult)
	rm -f $(netlist)
	rm -f logfile.txt
	