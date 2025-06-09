create_project xlxfpga_test ./test_run_dir/xlxfpga/xlxfpga_test -part xc7a200tffg1156-3
set_param general.maxThreads 2
add_files {./test_run_dir/xlxfpga/ArmleoCPUFormalWrapper.sv}
import_files -fileset constrs_1 -force -norecurse {./tests/fpga_builds/constraints.xdc}
update_compile_order -fileset sources_1
update_compile_order -fileset sim_1
launch_runs synth_1 -jobs 1
wait_on_run synth_1
open_run synth_1 -name netlist_1
report_timing_summary -delay_type max -report_unconstrained -check_timing_verbose \
-max_paths 10 -input_pins -file ./test_run_dir/xlxfpga/report_timing.rpt
launch_runs impl_1 -to_step write_bitstream -jobs 1
wait_on_run -quiet impl_1 
open_run impl_1
report_timing_summary -delay_type min_max -report_unconstrained -check_timing_verbose \
-max_paths 10 -input_pins -file ./Tutorial_Created_Data/project_bft/imp_timing.rpt
start_gui