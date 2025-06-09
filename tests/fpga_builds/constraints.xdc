create_clock -name clock -period 10.0 [get_ports clock];
set_output_delay -clock clock -max 10.0 [all_outputs];
set_input_delay -clock clock -max 10.0 [all_inputs];