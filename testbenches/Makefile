
execute: build
	$(MAKE) -C regfile/ execute
	$(MAKE) -C tlb/     execute

build:
	$(MAKE) -C regfile/ build
	$(MAKE) -C tlb/ build

	
clean:
	$(MAKE) -C tlb/ clean
	$(MAKE) -C regfile/ clean
