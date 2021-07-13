
.PHONE: docker_check

ifeq ($(DOCKER_CHECK_FILE_INCLUDED),)

DOCKER_CHECK_FILE_INCLUDED=y

docker_check:
	@if [ -z "$$DOCKER_CHECK" ]; then echo "DOCKER_CHECK is not set, please execute from docker"; exit 1; fi

endif