rm -rf build/riscv-tests
git clone https://github.com/riscv-software-src/riscv-tests build/riscv-tests

export RISCV_GCC_OPTS='-static -mcmodel=medany -fvisibility=hidden -nostdlib -nostartfiles'

cd build/riscv-tests
git submodule update --init --recursive
autoconf
./configure --prefix=$RISCV/target
make isa
