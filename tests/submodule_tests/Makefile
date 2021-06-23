TOPTARGETS := test clean

SUBDIRS := $(wildcard */.)
SUBDIRS := $(filter-out cache/%,$(SUBDIRS))
SUBDIRS := $(filter-out cache_verilator/%,$(SUBDIRS))
SUBDIRS := $(filter-out csr/%,$(SUBDIRS))
SUBDIRS := $(filter-out fetch/%,$(SUBDIRS))
SUBDIRS := $(filter-out execute/%,$(SUBDIRS))

$(TOPTARGETS): $(SUBDIRS)
$(SUBDIRS):
	$(MAKE) -C $@ $(MAKECMDGOALS)

.PHONY: $(TOPTARGETS) $(SUBDIRS)


#random:
#	echo $(SUBDIRS)
