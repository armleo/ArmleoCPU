
execute: build
	$(MAKE) -C regfile/ execute
	$(MAKE) -C tlb/     execute
	$(MAKE) -C ptw/ 	execute

build:
	$(MAKE) -C regfile/ build
	$(MAKE) -C tlb/ build
	$(MAKE) -C ptw/ build
	
clean:
	$(MAKE) -C tlb/ clean
	$(MAKE) -C regfile/ clean
	$(MAKE) -C ptw/ clean
