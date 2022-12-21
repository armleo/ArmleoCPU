rm -rf build/riscv-tests
git clone https://github.com/riscv-software-src/riscv-tests build/riscv-tests

export RISCV_PREFIX=$HOME/.local/xPacks/@xpack-dev-tools/riscv-none-elf-gcc/12.2.0-1.1/.content/bin/riscv-none-elf-
export RISCV_GCC_OPTS='-static -mcmodel=medany -fvisibility=hidden -nostdlib -nostartfiles'



cd build/riscv-tests
git submodule update --init --recursive
autoconf
./configure --prefix=$RISCV/target
make isa
