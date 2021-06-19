yosys -import
# read design 

verilog_defaults -add -I../../../core_src/
read_verilog ../../../peripheral_src/armleocpu_axi_router.v


# elaborate design hierarchy
synth_intel -family cycloneiv -top armleocpu_axi_exclusive_monitor

# cleanup
clean

# write synthesized design
write_verilog synth.v