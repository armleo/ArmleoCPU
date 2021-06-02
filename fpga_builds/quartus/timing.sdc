create_clock -period 10 [get_ports clk]

set_input_delay -clock clk -max 3 [all_inputs]
set_input_delay -clock clk -min 2 [all_inputs]

set_output_delay -clock clk -max 3 [all_outputs]
set_output_delay -clock clk -min 2 [all_outputs]