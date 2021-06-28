TOPTARGETS := test clean

SUBDIRS := $(wildcard */.)

$(TOPTARGETS): $(SUBDIRS)
$(SUBDIRS):
	timeout --foreground 1000 $(MAKE) -C $@ $(MAKECMDGOALS)

.PHONY: $(TOPTARGETS) $(SUBDIRS)