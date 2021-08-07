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
    public:
    axi_interface<ADDR_TYPE, ID_TYPE, DATA_TYPE, STROBE_TYPE> * axi;
    uint8_t state;

    uint8_t cur_burst_num;
    uint8_t cur_len;
    ADDR_TYPE cur_addr;
    ID_TYPE cur_id;
    uint8_t cur_burst;
    uint8_t cur_size;
    uint8_t cur_prot;
    uint8_t stall_cycle_done = 0;
    uint8_t read_done = 0;

    void (*read_callback)(axi_simplifier * simplifier, ADDR_TYPE addr, DATA_TYPE * rdata, uint8_t * rresp);
    void (*write_callback)(axi_simplifier * simplifier, ADDR_TYPE addr, DATA_TYPE * wdata, uint8_t * wresp);
    void (*update_callback)(axi_simplifier * simplifier);
    public:
        axi_simplifier(
            axi_interface<ADDR_TYPE, ID_TYPE, DATA_TYPE, STROBE_TYPE> * axi_in,
            void (*read_callback_in)(axi_simplifier * simplifier, ADDR_TYPE addr, DATA_TYPE * rdata, uint8_t * rresp),
            void (*write_callback_in)(axi_simplifier * simplifier, ADDR_TYPE addr, DATA_TYPE * wdata, uint8_t * wresp),
            void (*update_callback_in)(axi_simplifier * simplifier)
        ) {
            // TODO: Add checks for NULL
            axi = axi_in;
            read_callback = read_callback_in;
            write_callback = write_callback_in;
            update_callback = update_callback_in;
            state = 0;
        }
    void calculate_next_addr() {
        ADDR_TYPE incr = (1 << (cur_size));
        // Increment calculation: size = 011 -> 8 byte, 010 -> 4 byte, 001 -> 2 byte, 000 -> 1 byte
       
        
        // Assumed that cur_len == 0, 1, 3, 7, 15
        if(cur_burst == 0b10) {
            check((cur_len == 0)
                || (cur_len == 1)
                || (cur_len == 3)
                || (cur_len == 7)
                || (cur_len == 15), "Length of request for WRAP is not 0, 1, 3, 7, 15");
        }
        // Wrap mask calculation (cur_len)
        
        cur_burst_num++;

        if(cur_burst == 0b00) {
            cur_addr = cur_addr;
            
            check(cur_burst != 0b00, "TODO: Implement fixed burst");
        } else if(cur_burst == 0b01) { // INCR
            cur_addr = (cur_addr + incr) & ~(incr - 1);

        } else if(cur_burst == 0b10) {// WRAP

            uint8_t cur_len_clog2 = 0;
            // Switch get's optimized in some cases by GCC
            switch(cur_len) {
                case 0: cur_len_clog2 = 0;
                case 1: cur_len_clog2 = 1;
                case 3: cur_len_clog2 = 2;
                case 7: cur_len_clog2 = 3;
                case 15: cur_len_clog2 = 4;
            };

            ADDR_TYPE wrap_mask = (1 << (cur_size + cur_len_clog2)) - 1;
            //cout << "For cur_size: " << int(cur_size)
            //    << ", cur_len: " << int(cur_len)
            //    << ", follwing wrap mask was generated: " << hex << wrap_mask << dec << endl;
            cur_addr = (cur_addr & ~wrap_mask) | ((cur_addr + incr) & wrap_mask);
        }
    }
    void set_valid_ready_to_default() {
        *axi->aw->ready = 0;
        *axi->b->valid = 0;
        *axi->w->ready = 0;
        *axi->ar->ready = 0;
        *axi->r->valid = 0;
    }

    void cycle() {
        set_valid_ready_to_default();
        if(state == 0) {
            cur_burst_num = 0;
            stall_cycle_done = 0;
            read_done = 0;
            if(*axi->ar->valid) {
                state = 1;
                cur_len = *axi->ar->len;
                cur_addr = *axi->ar->addr;
                cur_id = *axi->ar->id;
                cur_burst = *axi->ar->burst;
                cur_size = *axi->ar->size;
                cur_prot = *axi->ar->prot;
                cout << "AXI Simplifier: AR request stalled" << endl;
            } else if(*axi->aw->valid) {
                state = 2;
                
                cur_len = *axi->aw->len;
                cur_addr = *axi->aw->addr;
                cur_id = *axi->aw->id;
                cur_burst = *axi->aw->burst;
                cur_size = *axi->aw->size;
                cur_prot = *axi->aw->prot;
                cout << "AXI Simplifier: AW request stalled" << endl;
            }
        } else if(state == 1) { // Read address active
            *axi->ar->ready = 1;
            check(cur_len == *axi->ar->len, "len not stable");
            check(cur_addr == *axi->ar->addr, "Addr not stable");
            check(cur_id == *axi->ar->id, "Id not stable");
            check(cur_burst == *axi->ar->burst, "Burst not stable");
            check(cur_size == *axi->ar->size, "Size not stable");
            check(cur_prot == *axi->ar->prot, "Prot not stable");
            cout << "AXI Simplifier: AR request accepted" << endl;
            state = 3;
            // TODO: Add checks for aligments
        } else if(state == 2) { // Write address active
            *axi->aw->ready = 1;
            check(cur_len == *axi->aw->len, "len not stable");
            check(cur_addr == *axi->aw->addr, "Addr not stable");
            check(cur_id == *axi->aw->id, "id not stable");
            check(cur_burst == *axi->aw->burst, "burst not stable");
            check(cur_size == *axi->aw->size, "Size not stable");
            check(cur_prot == *axi->aw->prot, "Prot not stable");
            cout << "AXI Simplifier: AW request accepted" << endl;
            state = 4;
            // TODO: Add checks for aligments
        } else if(state == 3) { // Read active
            if(!stall_cycle_done) {
                cout << "AXI Simplifier: R response not ready yet" << endl;
                *axi->r->valid = 0;
                stall_cycle_done = 1;
                read_done = 0;
                // First we stall for one cycle
            } else {
                // Then second cycle we do read callback and set rvalid
                
                if(!read_done) {
                    read_done = 1;
                    *axi->r->valid = 1;
                    *axi->r->id = cur_id;
                    *axi->r->resp = 0b10; // SLV ERR by default
                    
                    read_callback(this, cur_addr, axi->r->data, axi->r->resp);
                    *axi->r->last = (cur_burst_num == cur_len) ? 1 : 0;
                    cout << "AXI Simplifier: R response sent" << endl;
                }

                this->update_callback(this); // Make sure that "valid" has been processed
                

                if(*axi->r->ready) { // If read was accepted
                    cout << "AXI Simplifier: R response accepted" << endl;
                    read_done = 0;
                    stall_cycle_done = 0;
                    
                    
                    if(*axi->r->last) {
                        // No need to calculate next addr, just go to idle state
                        state = 0;
                        cout << "AXI Simplifier: Response sent going back to idle" << endl;
                    } else {
                        calculate_next_addr();
                    }
                }
            }

            

            // TODO: Call read callback
        } else if(state == 4) { // Write active
            if(!stall_cycle_done) {
                stall_cycle_done = 1;
            }
            uint8_t last = 0;
            check(0, "Write not implemented yet");
            write_callback(this, cur_addr, axi->w->data, axi->b->resp);
            calculate_next_addr();

            if(last)
                state = 5; // Go to write response
            // TODO: Call write callback
        } else if(state == 5) { // Write response
            *axi->b->id = cur_id;
            *axi->b->valid = 1;
        }
        
    }
};



void utils_init() {
    srand (RANDOM_SEED);
    

}

