SBT=sbt


all: check_sbt
	$(SBT)

subtests:
	$(SBT) testOnly

test: subtests
	
build:
	

check:
	gcc --version > check.log
	verilator --version >> check.log
	which java >> check.log
	which sbt >> check.log
