SBT=sbt


all: check_sbt
	$(SBT)

subtests:
	$(SBT) testOnly

test: subtests
	
build:
	

check_sbt:
	which gcc >> check_sbt.log
	verilator --help > check_sbt.log
	which java > check_sbt.log
	which sbt > check_sbt.log