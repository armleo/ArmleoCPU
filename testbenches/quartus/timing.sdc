create_clock -period 10 [get_ports clk]

set_input_delay -clock clk 1.5 [all_inputs]
set_output_delay -clock clk 0.5 [all_outputs]