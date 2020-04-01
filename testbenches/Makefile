
execute: build
	$(MAKE) -C regfile/ 	execute
	$(MAKE) -C tlb/     	execute
	$(MAKE) -C ptw/ 		execute
	$(MAKE) -C storegen/ 	execute
	$(MAKE) -C loadgen/ 	execute
	$(MAKE) -C cache/ 	    execute

build:
	$(MAKE) -C regfile/ 	build
	$(MAKE) -C tlb/ 		build
	$(MAKE) -C ptw/ 		build
	$(MAKE) -C storegen/ 	build
	$(MAKE) -C loadgen/ 	build
	$(MAKE) -C cache/ 	    build
	
clean:
	$(MAKE) -C tlb/ 		clean
	$(MAKE) -C regfile/ 	clean
	$(MAKE) -C ptw/ 		clean
	$(MAKE) -C storegen/ 	clean
	$(MAKE) -C loadgen/ 	clean
	$(MAKE) -C cache/ 	    clean