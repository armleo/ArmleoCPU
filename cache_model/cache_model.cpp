#include <iostream>
#include <vector>

class pte {
    uint32_t value;
    public:
        pte(uint32_t ppn1, uint32_t ppn0, uint32_t accesstag) {
            value = ppn1 << 20; //[31:20]
            value |= ppn0 << 10;//[19:10]
            value |= accesstag;// [9:0]
        }
};

class pteTable {
    private:
        std::vector<pte> pteStorage;
    public:
        void push(pte p) {
            pteStorage.push_back(p);
        }
};

class location {
    uint64_t address;
    pteTable table;
    public:
        location(uint64_t address, pteTable table) : address(address), table(table) {
            
        }
}

class MyMemory {
    std::vector<location> locations;
    public:
        void addTableAtLocation(location loc) {
            locations.push_back(loc);
        }
};

int main() {
    pteTable topPte;
    pte subPte_pointer();
    pte topLeafPte;
    topPte.push(subPte_pointer);
    topPte.push(topLeafPte);

    pteTable subPte;
    pte leafPte0;
    subPte.push(leafPte0);

    MyMemory mem;
    location loc0(4096, topPte);
    location loc1(8192, subPte);
    mem.addTableAtLocation(loc0);
    mem.addTableAtLocation(loc1);
}