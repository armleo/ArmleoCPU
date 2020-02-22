SBT=sbt


all: check_sbt
	sbt

subtests:
	

test: subtests
	
build:
	

check_sbt:
	$(SBT)