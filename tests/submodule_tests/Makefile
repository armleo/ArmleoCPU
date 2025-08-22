
TOPTARGETS := test clean

SUBDIRS := $(wildcard */.)
SUBDIRS := $(filter-out jtag_dtm/%,$(SUBDIRS))
SUBDIRS := $(filter-out jtag_tap/%,$(SUBDIRS))
SUBDIRS := $(filter-out cache_lane6/%,$(SUBDIRS))
# To disable some folder do: SUBDIRS := $(filter-out execute/%,$(SUBDIRS))

$(TOPTARGETS): $(SUBDIRS)
$(SUBDIRS):
	$(MAKE) -C $@ $(MAKECMDGOALS)

.PHONY: $(TOPTARGETS) $(SUBDIRS)
