#include <queue>
#include <stdint.h>
#include <stdlib.h>     /* srand, rand */
#include <time.h>       /* time */

using namespace std;



template <
    typename ADDR_TYPE,
    typename ID_TYPE> 
class axi_addr {
    public:
    uint8_t * valid;
    uint8_t * ready;

    ADDR_TYPE * addr;
    uint8_t * len;
    uint8_t * size;
    uint8_t * burst;
    ID_TYPE * id;
    uint8_t * prot;
    uint8_t * lock;
        axi_addr(
            uint8_t * valid_in,
            uint8_t * ready_in,

            ADDR_TYPE * addr_in,
            uint8_t * len_in,
            uint8_t * size_in,
            uint8_t * burst_in,
            ID_TYPE * id_in,
            uint8_t * prot_in,
            uint8_t * lock_in
        ) :
        valid(valid_in),
        ready(ready_in),
        addr(addr_in),
        len(len_in),
        size(size_in),
        burst(burst_in),
        id(id_in),
        prot(prot_in),
        lock(lock_in)
        {

        };
};

template <typename ID_TYPE, typename DATA_TYPE> class axi_r {
    public:
    uint8_t * valid;
    uint8_t * ready;

    uint8_t * resp;
    DATA_TYPE * data;
    ID_TYPE * id;
    uint8_t * last;
        axi_r(
            uint8_t * valid_in,
            uint8_t * ready_in,
            uint8_t * resp_in,
            DATA_TYPE * data_in,
            ID_TYPE * id_in,
            uint8_t * last_in
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
    public:
    uint8_t * valid;
    uint8_t * ready;

    DATA_TYPE * data;
    STROBE_TYPE * strb;
    uint8_t * last;
        axi_w(
            uint8_t * valid_in,
            uint8_t * ready_in,
            DATA_TYPE * data_in,
            STROBE_TYPE * strb_in,
            uint8_t * last_in
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
    public:
    uint8_t * valid;
    uint8_t * ready;
    ID_TYPE * id;
    uint8_t * resp;
        axi_b(
            uint8_t * valid_in,
            uint8_t * ready_in,
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
    public:
    axi_addr<ADDR_TYPE, ID_TYPE> * 
                ar;
    axi_r<ID_TYPE, DATA_TYPE> * 
                r;
    
    axi_addr<ADDR_TYPE, ID_TYPE> * 
                aw;
    axi_w<DATA_TYPE, STROBE_TYPE> * 
                w;
    axi_b<ID_TYPE> * 
                b;
        axi_interface(
            axi_addr<ADDR_TYPE, ID_TYPE> * ar_in,
            axi_r<ID_TYPE, DATA_TYPE> * r_in,

            axi_addr<ADDR_TYPE, ID_TYPE> * aw_in,
            axi_w<DATA_TYPE, STROBE_TYPE> * w_in,
            axi_b<ID_TYPE> * b_in
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
class axi_simplifier {
    axi_interface<ADDR_TYPE, ID_TYPE, DATA_TYPE, STROBE_TYPE> * axi;
    uint8_t state;

    uint8_t cur_len;
    ADDR_TYPE cur_addr;
    ID_TYPE cur_id;
    uint8_t cur_burst;
    uint8_t cur_size;


    DATA_TYPE (*read_callback)(axi_simplifier * simplifier, ADDR_TYPE addr, uint8_t * resp);
    void (*write_callback)(axi_simplifier * simplifier, ADDR_TYPE addr, uint8_t * resp);
    public:
        axi_simplifier(
            axi_interface<ADDR_TYPE, ID_TYPE, DATA_TYPE, STROBE_TYPE> * axi_in,
            DATA_TYPE (*read_callback_in)(axi_simplifier * simplifier, ADDR_TYPE addr, uint8_t * resp),
            void (*write_callback_in)(axi_simplifier * simplifier, ADDR_TYPE addr, uint8_t * resp)
        ) {
            axi = axi_in;
            read_callback = read_callback_in;
            write_callback = write_callback_in;
        }
    void calculate_next_addr() {
        
    }

    void cycle() {
        if(state == 0) {
            if(*axi->ar->valid) {
                state = 1;
                *axi->ar->ready = 1;
                cur_len = *axi->ar->len;
                cur_addr = *axi->ar->addr;
                cur_id = *axi->ar->id;
                cur_burst = *axi->ar->burst;
                cur_size = *axi->ar->size;
            } else if(*axi->aw->valid) {
                state = 2;
                *axi->aw->ready = 1;
                cur_len = *axi->aw->len;
                cur_addr = *axi->aw->addr;
                cur_id = *axi->aw->id;
                cur_burst = *axi->aw->burst;
                cur_size = *axi->aw->size;
            }
        } else if(state == 1) { // Read active
            

            calculate_next_addr();
            // TODO: Call read callback
        } else if(state == 2) { // Write active

            calculate_next_addr();
            // TODO: Call write callback
        } else if(state == 3) { // Write response
            
        }
        
    }
};



void utils_init() {
    srand (RANDOM_SEED);
    

}

