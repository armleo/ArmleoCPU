all: execute lint

lint:
	$(MAKE) -C regfile/ 			lint
	$(MAKE) -C tlb/     			lint
	$(MAKE) -C ptw/ 				lint
	$(MAKE) -C storegen/ 			lint
	$(MAKE) -C loadgen/ 			lint
	$(MAKE) -C cache_verilator/ 	lint
	$(MAKE) -C fetch/ 				lint

lint-xvlog:
	$(MAKE) -C regfile/ 			lint-xvlog
	$(MAKE) -C tlb/     			lint-xvlog
	$(MAKE) -C ptw/ 				lint-xvlog
	$(MAKE) -C storegen/ 			lint-xvlog
	$(MAKE) -C loadgen/ 			lint-xvlog

execute: build
	$(MAKE) -C regfile/ 			execute
	$(MAKE) -C tlb/     			execute
	$(MAKE) -C ptw/ 				execute
	$(MAKE) -C storegen/ 			execute
	$(MAKE) -C loadgen/ 			execute
	$(MAKE) -C pagefault/ 			execute
	$(MAKE) -C cache_verilator/ 	execute
	$(MAKE) -C fetch/ 				execute
	$(MAKE) -C execute/ 			execute

build:
	$(MAKE) -C regfile/ 			build
	$(MAKE) -C tlb/ 				build
	$(MAKE) -C ptw/ 				build
	$(MAKE) -C storegen/ 			build
	$(MAKE) -C loadgen/ 			build
	$(MAKE) -C pagefault/ 			build
	$(MAKE) -C cache_verilator/ 	build
	$(MAKE) -C fetch/ 				build
	$(MAKE) -C execute/ 			build
	
	
clean:
	$(MAKE) -C tlb/ 		        clean
	$(MAKE) -C regfile/ 	        clean
	$(MAKE) -C ptw/ 		        clean
	$(MAKE) -C storegen/ 	        clean
	$(MAKE) -C loadgen/ 	        clean
	$(MAKE) -C pagefault/ 			clean
	$(MAKE) -C cache_verilator/ 	clean
	$(MAKE) -C fetch/ 				clean
	$(MAKE) -C quartus/ 			clean