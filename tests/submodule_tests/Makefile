TOPTARGETS := test clean

SUBDIRS := $(wildcard */.)
SUBDIRS := $(filter-out csr/%,$(SUBDIRS))
SUBDIRS := $(filter-out fetch/%,$(SUBDIRS))
SUBDIRS := $(filter-out execute/%,$(SUBDIRS))

$(TOPTARGETS): $(SUBDIRS)
$(SUBDIRS):
	timeout --foreground 1000 $(MAKE) -C $@ $(MAKECMDGOALS)

.PHONY: $(TOPTARGETS) $(SUBDIRS)


#random:
#	echo $(SUBDIRS)
