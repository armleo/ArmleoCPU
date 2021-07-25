#include <queue>
#include <stdint.h>
#include <stdlib.h>     /* srand, rand */
#include <time.h>       /* time */

using namespace std;



template <
    typename ADDR_TYPE,
    typename ID_TYPE> 
class axi_addr {
    bool * valid;
    bool * ready;

    ADDR_TYPE * addr;
    uint8_t * len;
    uint8_t * size;
    uint8_t * burst;
    ID_TYPE * id;
    
    public:
        axi_addr(
            bool * valid_in,
            bool * ready_in,

            ADDR_TYPE * addr_in,
            uint8_t * len_in,
            uint8_t * size_in,
            uint8_t * burst_in,
            ID_TYPE * id_in
        ) :
        valid(valid_in),
        ready(ready_in),
        addr(addr_in),
        len(len_in),
        size(size_in),
        burst(burst_in),
        id(id_in)
        {

        };
};

template <typename ID_TYPE, typename DATA_TYPE> class axi_r {
    bool * valid;
    bool * ready;

    uint8_t * resp;
    DATA_TYPE * data;
    ID_TYPE * id;
    bool * last;
    public:
        axi_r(
            bool * valid_in,
            bool * ready_in,
            uint8_t * resp_in,
            DATA_TYPE * data_in,
            ID_TYPE * id_in,
            bool * last_in
        ) :
        valid(valid_in),
        ready(ready_in),
        resp(resp_in),
        data(data_in),
        id(id_in),
        last(last_in)
        {

        };
};

template <typename DATA_TYPE, typename STROBE_TYPE> class axi_w {
    bool * valid;
    bool * ready;

    DATA_TYPE * data;
    STROBE_TYPE * strb;
    bool * last;
    public:
        axi_w(
            bool * valid_in,
            bool * ready_in,
            DATA_TYPE * data_in,
            STROBE_TYPE * strb_in,
            bool * last_in
        ) :
        valid(valid_in),
        ready(ready_in),
        data(data_in),
        strb(strb_in),
        last(last_in)
        {

        }
};

template <typename ID_TYPE> class axi_b {
    bool * valid;
    bool * ready;
    ID_TYPE * id;
    uint8_t * resp;
    public:
        axi_b(
            bool * valid_in,
            bool * ready_in,
            ID_TYPE * id_in,
            uint8_t * resp_in
        ) :
        valid(valid_in),
        ready(ready_in),
        id(id_in),
        resp(resp_in)
        {

        }
};

template <
    typename ADDR_TYPE,
    typename ID_TYPE,
    typename DATA_TYPE,
    typename STROBE_TYPE> 
class axi_interface {
    axi_addr<ADDR_TYPE, ID_TYPE>
                ar;
    axi_r<ID_TYPE, DATA_TYPE>
                r;
    
    axi_addr<ADDR_TYPE, ID_TYPE>
                aw;
    axi_w<DATA_TYPE, STROBE_TYPE>
                w;
    axi_b<ID_TYPE>
                b;
    
    public:
        axi_interface(
            axi_addr<ADDR_TYPE, ID_TYPE> ar_in,
            axi_r<ID_TYPE, DATA_TYPE> r_in,

            axi_addr<ADDR_TYPE, ID_TYPE> aw_in,
            axi_w<DATA_TYPE, STROBE_TYPE> w_in,
            axi_b<ID_TYPE> b_in
        ) :
            ar(ar_in),
            r(r_in),
            aw(aw_in),
            w(w_in),
            b(b_in)
        {

        }
};


template <
    typename ADDR_TYPE,
    typename ID_TYPE,
    typename DATA_TYPE,
    typename STROBE_TYPE> 
class axi_bram {
    DATA_TYPE * storage;
    size_t storage_size;
    axi_interface<ADDR_TYPE, ID_TYPE, DATA_TYPE, STROBE_TYPE> axi;
    public:
        axi_bram(
            size_t storage_size,
            axi_interface<ADDR_TYPE, ID_TYPE, DATA_TYPE, STROBE_TYPE> axi_in

        ) {
            storage = new uint32_t[storage_size];
            axi = axi_in;
        }


    void cycle() {
        
    }
};



class expected_response {


};

queue<expected_response> * expected_response_queue;

void utils_init() {
    srand (time(NULL));
    

    expected_response_queue = new queue<expected_response>;
}

