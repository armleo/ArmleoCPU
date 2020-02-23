SBT=sbt


all: check_sbt
	$(SBT)

subtests:
	$(SBT) testOnly

test: subtests
	
build:
	

check_sbt:
	$(SBT)