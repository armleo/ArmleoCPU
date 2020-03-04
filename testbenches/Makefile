
build:
	#$(MAKE) -C cache/ build
	#$(MAKE) -C fetch/ build
	$(MAKE) -C regfile/ build
	$(MAKE) -C tlb/ build

	
execute: build
	$(MAKE) -C regfile/ execute
	$(MAKE) -C tlb/     execute