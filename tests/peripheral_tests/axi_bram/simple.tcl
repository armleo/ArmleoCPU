yosys -import
# read design 

verilog_defaults -add -I../../../core_src/
read_verilog ../../../peripheral_src/armleocpu_axi_bram.v
read_verilog ../../../core_src/armleocpu_mem_1rw.v
read_verilog ../../../core_src/armleocpu_mem_1rwm.v


# elaborate design hierarchy
synth_intel -family cycloneiv -top armleocpu_axi_bram

# cleanup
clean

# write synthesized design
write_verilog synth.v