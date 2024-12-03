sudo mkdir -p $RISCV
sudo chown $USER -R $RISCV

sudo apt-get install autoconf automake autotools-dev curl python3 libmpc-dev libmpfr-dev libgmp-dev gawk build-essential bison flex texinfo gperf libtool patchutils bc zlib1g-dev libexpat-dev
sudo apt-get install device-tree-compiler

rm -rf build/riscv-isa-sim
git clone https://github.com/riscv-software-src/riscv-isa-sim build/riscv-isa-sim
cd build/riscv-isa-sim

mkdir build
cd build
../configure --prefix=$RISCV
make
make install
