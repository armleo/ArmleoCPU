all: test

test: sbt_test_only # subtests

compile_top:
	sbt "runMain ArmleoCPUDriver --target-dir generated_vlog"

sbt_test_only:
	sbt "testOnly armleocpu.ALUTester" \
	"testOnly armleocpu.RegfileTester" \
	"testOnly armleocpu.CacheBackstorageTester" \
	"testOnly armleocpu.TLBTester" \
	"testOnly armleocpu.SRAMTester" \
	"testOnly armleocpu.StoreGenTester" \
	"testOnly armleocpu.LoadGenTester"


sbt_mount:
	sbt

subtests: compile_verilog
	cd testbenches && $(MAKE)

clean:
	rm -rf generated_vlog
	rm -rf target
	rm -rf test_run_dir
	#cd testbenches && $(MAKE) clean
	#rm -rf check.log
