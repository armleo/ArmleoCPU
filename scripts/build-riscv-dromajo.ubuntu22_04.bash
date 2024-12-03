sudo apt install cmake -y
git clone https://github.com/chipsalliance/dromajo.git build/dromajo

cd build/dromajo
mkdir build
cd build
# Debug build
cmake ..
# Release build Ofast compile option
cmake -DCMAKE_BUILD_TYPE=Release ..
make
sudo make install
cd ../../../