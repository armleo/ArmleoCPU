
execute: build
	$(MAKE) -C regfile/ execute
	$(MAKE) -C tlb/     execute

build:
	#$(MAKE) -C cache/ build
	#$(MAKE) -C fetch/ build
	$(MAKE) -C regfile/ build
	$(MAKE) -C tlb/ build

	
clean:
	$(MAKE) -C tlb/ clean
	$(MAKE) -C regfile/ clean
