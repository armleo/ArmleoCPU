# Can be specified by user
DOCKER_IMAGE?=armleo/armleocpu_toolset:latest


# One hour timeout by default
DOCKER_TIMEOUT?=3600

# Should be set to project's directory (workspace base)
PROJECT_DIR?=$(shell pwd)

DOCKER_ARG=-t $(DOCKER_IMAGE)
DOCKER_ARG_INTERACTIVE=-i $(DOCKER_ARG)
DOCKER_CMD=docker run -v $(PROJECT_DIR):/ArmleoCPU -e PROJECT_DIR=/ArmleoCPU -e DOCKER_CHECK=1

docker-%:
	timeout --foreground $(DOCKER_TIMEOUT) \
		$(DOCKER_CMD) -w "/ArmleoCPU" $(DOCKER_ARG) $(MAKE) $*

interactive:
	$(DOCKER_CMD) -w "/ArmleoCPU" $(DOCKER_ARG_INTERACTIVE)
	
