sudo apt-get update
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/master/install.sh | bash
nvm install --lts node
nvm use --lts node
npm install --global xpm@latest
xpm install --global @xpack-dev-tools/riscv-none-elf-gcc@14.2.0-3.1 --verbose
~/.local/xPacks/@xpack-dev-tools/riscv-none-elf-gcc/14.2.0-3.1/.content/bin/riscv-none-elf-gcc --version


