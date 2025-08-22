
#include <Varmleocpu_csr.h>
#define TRACE
#define TOP_MODULE_DECLARATION Varmleocpu_csr * armleocpu_csr;
#define TOP_ALLOCATION armleocpu_csr = new Varmleocpu_csr;
#include "verilator_template_header.cpp"


const int ARMLEOCPU_CSR_CMD_NONE = (0);
const int ARMLEOCPU_CSR_CMD_READ = (1);
const int ARMLEOCPU_CSR_CMD_WRITE = (2);
const int ARMLEOCPU_CSR_CMD_READ_WRITE = (3);
const int ARMLEOCPU_CSR_CMD_READ_SET = (4);
const int ARMLEOCPU_CSR_CMD_READ_CLEAR = (5);
const int ARMLEOCPU_CSR_CMD_MRET = (6);
const int ARMLEOCPU_CSR_CMD_SRET = (7);
const int ARMLEOCPU_CSR_CMD_INTERRUPT_BEGIN = (8);
const int ARMLEOCPU_CSR_CMD_EXCEPTION_BEGIN = (9);

const int MACHINE = 3;
const int SUPERVISOR = 1;
const int USER = 0;

const uint32_t csr_mcause_ssi = (1 << 31) | (1);
const uint32_t csr_mcause_msi = (1 << 31) | (3);
const uint32_t csr_mcause_sti = (1 << 31) | (5);
const uint32_t csr_mcause_mti = (1 << 31) | (7);
const uint32_t csr_mcause_sei = (1 << 31) | (9);
const uint32_t csr_mcause_mei = (1 << 31) | (11);

const uint32_t csr_mstatus_mie = 1 << 3;
const uint32_t csr_mstatus_mpie = 1 << 7;
const uint32_t csr_mstatus_sie = 1 << 1;

const uint32_t csr_mie_meie = 1 << 11;
const uint32_t csr_mie_seie = 1 << 9;
const uint32_t csr_mie_mtie = 1 << 7;
const uint32_t csr_mie_stie = 1 << 5;
const uint32_t csr_mie_msie = 1 << 3;
const uint32_t csr_mie_ssie = 1 << 1;




void check_no_cmd_error() {
    check(armleocpu_csr->csr_cmd_error == 0, "Unexpected: Invalid access");
}


void csr_read(uint32_t address) {
    armleocpu_csr->csr_cmd = ARMLEOCPU_CSR_CMD_READ;
    armleocpu_csr->csr_address = address;
    armleocpu_csr->eval();
    check_no_cmd_error();
}

void csr_write_nocheck(uint32_t address, uint32_t data) {
    armleocpu_csr->csr_cmd = ARMLEOCPU_CSR_CMD_WRITE;
    armleocpu_csr->csr_address = address;
    armleocpu_csr->csr_from_rs = data;
    armleocpu_csr->eval();
}


void csr_write(uint32_t address, uint32_t data) {
    csr_write_nocheck(address, data);
    check_no_cmd_error();
}

void csr_read_check(uint32_t val) {
    armleocpu_csr->eval();
    if(armleocpu_csr->csr_to_rd != val)
        cout << "Unexpected csr_to_rd for address: 0x" << hex << armleocpu_csr->csr_address
        << ", value is 0x" << armleocpu_csr->csr_to_rd
        << ", expected: 0x" << val << endl << dec;
    check(armleocpu_csr->csr_to_rd == val, "Unexpected readdata value");
}


void csr_read_set(uint32_t address, uint32_t data) {
    armleocpu_csr->csr_cmd = ARMLEOCPU_CSR_CMD_READ_SET;
    armleocpu_csr->csr_address = address;
    armleocpu_csr->csr_from_rs = data;
    armleocpu_csr->eval();
    check_no_cmd_error();
}

void csr_read_reset(uint32_t address, uint32_t data) {
    armleocpu_csr->csr_cmd = ARMLEOCPU_CSR_CMD_READ_CLEAR;
    armleocpu_csr->csr_address = address;
    armleocpu_csr->csr_from_rs = data;
    armleocpu_csr->eval();
    check_no_cmd_error();
}


void csr_none() {
    armleocpu_csr->csr_cmd = ARMLEOCPU_CSR_CMD_NONE;
    armleocpu_csr->eval();
    //check_no_cmd_error();
}


void test_mro(uint32_t address, uint32_t expected_value) {
    csr_read(address);
    csr_read_check(expected_value);
    next_cycle();

    csr_write_nocheck(address, 0xDEADBEEF);
    check(armleocpu_csr->csr_cmd_error == 1, "MRO: Failed check invalid == 1");
    //check();
    next_cycle();


    csr_read(address);
    csr_read_check(expected_value);
    next_cycle();
}

void test_masked(uint32_t address, uint32_t mask) {
    cout << "Testbench: test_masked: Writing all ones to scratch" << endl;
    csr_write(address, 0xFFFFFFFF);
    next_cycle();

    cout << "Testbench: test_masked: reading all ones" << endl;
    csr_read(address);
    
    csr_read_check(0xFFFFFFFF & mask);
    next_cycle();

    cout << "Testbench: test_masked: Reseting using READ_RESET" << endl;
    for(int i = 0; i < 32; ++i) {
        csr_read_reset(address, (1 << 31) >> i);
        csr_read_check(((0xFFFFFFFFU << i) >> i) & mask);
        next_cycle();
    }

    csr_read(address);
    csr_read_check(0);
    next_cycle();

    cout << "Testbench: test_masked: set'ing using READ_SET" << endl;
    for(int i = 0; i < 32; ++i) {
        csr_read_set(address, (1 << i));
        csr_read_check(((1 << (i)) - 1) & mask);
        next_cycle();
    }

    cout << "Testbench: test_masked: writing zero" << endl;
    csr_write(address, 0);
    next_cycle();

    cout << "Testbench: test_masked: Reading all zero" << endl;
    csr_read(address);
    csr_read_check(0);
    next_cycle();

    csr_none();
    next_cycle();

    cout << "Testbench: test_masked: Reading after dummy cycle" << endl;
    csr_read(address);
    csr_read_check(0);
    next_cycle();

    csr_none();
}



void test_const(uint32_t address, uint32_t expected_value) {
    csr_read(address);
    csr_read_check(expected_value);
    next_cycle();

    csr_write(address, 0xDEADBEEF);
    next_cycle();


    csr_read(address);
    csr_read_check(expected_value);
    next_cycle();


    csr_write(address, 0xFFFFFFFF);
    next_cycle();

    csr_read(address);
    csr_read_check(expected_value);
    next_cycle();


    csr_write(address, 0x0);
    next_cycle();

    csr_read(address);
    csr_read_check(expected_value);
    next_cycle();

    csr_none();
}

void test_scratch(uint32_t address) {
    test_masked(address, 0xFFFFFFFF);
}


void force_to_machine() {
    armleocpu_csr->csr_cmd = ARMLEOCPU_CSR_CMD_EXCEPTION_BEGIN;
    armleocpu_csr->csr_exc_cause = 3;
    next_cycle();

    csr_none();

    check(armleocpu_csr->csr_mcurrent_privilege == MACHINE, "GOTOPRIVILEGE: Unexpected target privilege");
}

void from_machine_go_to_privilege(uint32_t target_privilege) {
    csr_write(0xBC0, target_privilege);
    next_cycle();

    csr_none();
    check(armleocpu_csr->csr_mcurrent_privilege == target_privilege, "GOTOPRIVILEGE: Unexpected target privilege");
}


void test_cmd_error() {
    start_test("CMD error test");
    for(int i = 10; i < 16; i++) {
        armleocpu_csr->csr_cmd = i;
        armleocpu_csr->eval();
        check(armleocpu_csr->csr_cmd_error == 1, "Expected error for incorrect cmd");
        next_cycle();
    }

    csr_none();
    armleocpu_csr->eval();
    check_no_cmd_error();
}


const uint8_t IRQ_BITS_MEIP = (1 << 0);
const uint8_t IRQ_BITS_MSIP = (1 << 1);
const uint8_t IRQ_BITS_MTIP = (1 << 2);
const uint8_t IRQ_BITS_SEIP = (1 << 3);
const uint8_t IRQ_BITS_SSIP = (1 << 4);
const uint8_t IRQ_BITS_STIP = (1 << 5);

void set_irq_bits(uint8_t irq_bits) {
    armleocpu_csr->irq_meip_i = (irq_bits & IRQ_BITS_MEIP) ? 1 : 0;
    armleocpu_csr->irq_msip_i = (irq_bits & IRQ_BITS_MSIP) ? 1 : 0;
    armleocpu_csr->irq_mtip_i = (irq_bits & IRQ_BITS_MTIP) ? 1 : 0;

    armleocpu_csr->irq_seip_i = (irq_bits & IRQ_BITS_SEIP) ? 1 : 0;
    armleocpu_csr->irq_ssip_i = (irq_bits & IRQ_BITS_SSIP) ? 1 : 0;
    armleocpu_csr->irq_stip_i = (irq_bits & IRQ_BITS_STIP) ? 1 : 0;
    armleocpu_csr->eval();
}

void test_exception(int priv, uint32_t initial_mstatus) {
    uint32_t cause = rand() & 0x8FFFFFFF;
    uint32_t epc = rand() & 0xFFFFFFFC;
    uint32_t mtvec = rand() & 0xFFFFFFFC;

    force_to_machine();
    
    // Write to MSTATUS MIE, to see if MPIE is overwritten
    cout << "Exception: Writing initial mstatus 0x" << hex << initial_mstatus << endl;
    csr_write(0x300, initial_mstatus);
    next_cycle();

    cout << "Exception: Writing mtvec 0x" << hex << mtvec << dec << endl;
    csr_write(0x305, mtvec);
    next_cycle();

    from_machine_go_to_privilege(priv);

    armleocpu_csr->csr_cmd = ARMLEOCPU_CSR_CMD_EXCEPTION_BEGIN;
    armleocpu_csr->csr_exc_cause = cause;
    armleocpu_csr->csr_exc_epc = epc;
    armleocpu_csr->eval();
    check(armleocpu_csr->csr_next_pc == mtvec, "Exception start has wrong next_pc value");
    next_cycle();

    armleocpu_csr->csr_exc_cause = 1000; // Some random values
    armleocpu_csr->csr_exc_epc = 1004; // Some random values
    check(armleocpu_csr->csr_mcurrent_privilege == MACHINE, "Expected after exception to be in machine privilege");
    
    
    csr_read(0x341);
    csr_read_check(epc);
    next_cycle();

    csr_read(0x342);
    csr_read_check(cause);
    next_cycle();

    uint32_t expected_mstatus = initial_mstatus;
    uint32_t initial_mie = !!(initial_mstatus & csr_mstatus_mie);
    expected_mstatus &= ~(csr_mstatus_mie); // Clear the MIE
    expected_mstatus &= ~(csr_mstatus_mpie); // Clean mpie
    expected_mstatus |= (initial_mie ? csr_mstatus_mpie : 0);// write mpie
    expected_mstatus |= priv << 11; // Expected MPP
    csr_read(0x300);
    csr_read_check(expected_mstatus);
    next_cycle();


    csr_none();

}

void test_interrupt(uint32_t from_privilege, uint32_t mstatus, uint32_t mie,
    uint8_t irq_bits, uint32_t mip = 0) {
    uint32_t mcause = 100; // Some random unreachable value
    bool expecting_interrupt = true;
    start_test("Interrupt test");
    cout << "Starting interrupt test:" << endl
        << "from_privilege: " << from_privilege << endl
        << "mstatus: " << hex << mstatus << endl
        << "mie: " << mie << endl
        << "irq bits: " << (int)irq_bits << endl
        << dec
        ;

    if(from_privilege == MACHINE) {
        if(mstatus & csr_mstatus_mie) {
            // Can accept interrupts
            if((mie & csr_mie_meie) && (irq_bits & IRQ_BITS_MEIP)) {
                mcause = csr_mcause_mei;
            } else if((mie & csr_mie_msie) && (irq_bits & IRQ_BITS_MSIP)) {
                mcause = csr_mcause_msi;
            } else if((mie & csr_mie_mtie) && (irq_bits & IRQ_BITS_MTIP)) {
                mcause = csr_mcause_mti;
            } else {
                expecting_interrupt = false;
            }
        } else {
            expecting_interrupt = false;
        }
    } else if(from_privilege == SUPERVISOR) {
        if((mie & csr_mie_meie) && (irq_bits & IRQ_BITS_MEIP)) {
            mcause = csr_mcause_mei;
        } else if((mie & csr_mie_msie) && (irq_bits & IRQ_BITS_MSIP)) {
            mcause = csr_mcause_msi;
        } else if((mie & csr_mie_mtie) && (irq_bits & IRQ_BITS_MTIP)) {
            mcause = csr_mcause_mti;
        } else {
            if(mstatus & csr_mstatus_sie) {
                if((mie & csr_mie_seie)  &&  (irq_bits & IRQ_BITS_SEIP)) {
                    mcause = csr_mcause_sei;
                } else if((mie & csr_mie_ssie)  &&  (irq_bits & IRQ_BITS_SSIP)) {
                    mcause = csr_mcause_ssi;
                } else if((mie & csr_mie_stie)  &&  (irq_bits & IRQ_BITS_STIP)) {
                    mcause = csr_mcause_sti;
                } else {
                    expecting_interrupt = false;
                }
            } else {
                expecting_interrupt = false;
            }
        }
    } else if(from_privilege == USER) {
        // No way to disable interrupts other than MIE specific bit being reset
        if((mie & csr_mie_meie) && (irq_bits & IRQ_BITS_MEIP)) {
            mcause = csr_mcause_mei;
        } else if((mie & csr_mie_msie) && (irq_bits & IRQ_BITS_MSIP)) {
            mcause = csr_mcause_msi;
        } else if((mie & csr_mie_mtie) && (irq_bits & IRQ_BITS_MTIP)) {
            mcause = csr_mcause_mti;
        } else if((mie & csr_mie_seie)  &&  (irq_bits & IRQ_BITS_SEIP)) {
            mcause = csr_mcause_sei;
        } else if((mie & csr_mie_ssie)  &&  (irq_bits & IRQ_BITS_SSIP)) {
            mcause = csr_mcause_ssi;
        } else if((mie & csr_mie_stie)  &&  (irq_bits & IRQ_BITS_STIP)) {
            mcause = csr_mcause_sti;
        } else {
            expecting_interrupt = false;
        }
    } else {
        throw std::invalid_argument("Invalid from_privilege");
    }
    
    cout << "expecting_interrupt " << expecting_interrupt << endl
        << "mcause (irq = " << ((mcause & 31) ? 1 : 0) << ") = " << (mcause & ~(1 << 31)) << endl
        ;
    set_irq_bits(0); // Reset all IRQ bits
    armleocpu_csr->eval();
    check(armleocpu_csr->csr_mcurrent_privilege == MACHINE, "Expecting to be in machine privilege at the start of the test");
    
    cout << "Writing mstatus" << endl;
    csr_write(0x300, mstatus);
    next_cycle();
    
    cout << "Writing mie" << endl;
    csr_write(0x304, mie);
    next_cycle();

    cout << "Writing mip" << endl;
    csr_write(0x344, mip);
    next_cycle();

    cout << "Writing mtvec" << endl;
    uint32_t mtvec = rand() & 0xFFFFFFFC;
    csr_write(0x305, mtvec);
    next_cycle();


    csr_none();

    cout << "Going to privilege " << from_privilege << endl;
    from_machine_go_to_privilege(from_privilege);

    // set csr_exc_epc
    // set csr_exc_cause to random value
    set_irq_bits(irq_bits);
    cout << "irq_meip_i = " << (int)(armleocpu_csr->irq_meip_i) << " "
        << "irq_msip_i = " << (int)(armleocpu_csr->irq_msip_i) << " "
        << "irq_mtip_i = " << (int)(armleocpu_csr->irq_mtip_i) << " "
        << "irq_seip_i = " << (int)(armleocpu_csr->irq_seip_i) << " "
        << "irq_ssip_i = " << (int)(armleocpu_csr->irq_ssip_i) << " "
        << "irq_stip_i = " << (int)(armleocpu_csr->irq_stip_i) << endl;
    armleocpu_csr->eval();


    check(armleocpu_csr->interrupt_pending_output == expecting_interrupt, "Expecting interrupt pending");
    
    // TODO: Calculate MIP/SIP expected values and check
    

    if(expecting_interrupt) {
        armleocpu_csr->csr_cmd = ARMLEOCPU_CSR_CMD_INTERRUPT_BEGIN;
        uint32_t csr_exc_epc = rand() & 0xFFFFFFFC;
        armleocpu_csr->csr_exc_epc = csr_exc_epc;
        armleocpu_csr->eval();
        check_no_cmd_error();
        check(armleocpu_csr->csr_next_pc == mtvec, "Expecting next_pc to be mtvec, but it's not");

        
        next_cycle();
        check(armleocpu_csr->csr_mcurrent_privilege == MACHINE, "Expecting interrupt to be accepted, but it's not");
        armleocpu_csr->csr_exc_epc = 200; // some random value
        
        csr_none();
        next_cycle();

        // Check for MSTATUS to be MIE = 1
        // && MPIE = initial MSTATUS & MIE
        // Check for MPP to be same as from_privilege
        
        // Check for MPP
        uint32_t expected_mstatus_value = mstatus | (from_privilege << 11); // Check that MPP is same as from_privilege
        
        // Check for MPIE
        uint32_t initial_mie = !!(mstatus & csr_mstatus_mie);
        expected_mstatus_value &= ~(csr_mstatus_mpie); // Clean mpie
        expected_mstatus_value |= initial_mie << 7; // MPIE = initial mstatus.mie

        // Check for MIE = 0
        expected_mstatus_value &= ~csr_mstatus_mie; // MIE = 0
        
        csr_read(0x300);
        csr_read_check(expected_mstatus_value);
        next_cycle();

        csr_read(0x342);
        csr_read_check(mcause);
        next_cycle();

        csr_read(0x341);
        csr_read_check(csr_exc_epc);
        next_cycle();

        set_irq_bits(0); // Reset all IRQ bits
        csr_none();
        force_to_machine();
        
        armleocpu_csr->eval();
    } else {
        cout << "Was not expecting an interrupt, test passed" << endl;
        set_irq_bits(0); // Reset all IRQ bits
        csr_none();
        force_to_machine();
    }
}

void enable_interrupt(uint32_t mask) {
    csr_write(0x300, csr_mstatus_mie | csr_mstatus_sie);
    next_cycle(); // Enable interrupts

    csr_write(0x304, mask); // Set MIE bits
    next_cycle();
}

void check_interrupt_pending(int interrupt_pending, auto msg) {
    check(armleocpu_csr->interrupt_pending_output == !!interrupt_pending, msg);
}


void subtest_mip_sip(uint32_t mask, uint8_t irq_bits, bool is_supervisor_bit,
        bool set_the_bit, bool set_the_irq) {
    cout << "set_the_bit: " << set_the_bit << endl
        << "set_the_irq: " << set_the_irq << endl;
    enable_interrupt(mask);
    
    csr_write(0x344, 0); // Reset the bit
    set_irq_bits(0); // Clear all interrupts
    next_cycle();

    cout << "Testing for the resetting the bit and the irq bit should cause MIP bit to go low" << endl;
    csr_read(0x344); // Check for bit to be reset
    check((armleocpu_csr->csr_to_rd & mask) == 0, "Expected bit to be reset");
    check_interrupt_pending(0, "Expected for reset values the pending bit to be reset");
    next_cycle();
    
    if(set_the_bit) {
        cout << "Setting the MIP bit" << endl;
        csr_write(0x344, mask); // set the bit
        next_cycle();
    }

    if(set_the_irq) {
        cout << "Setting the IRQ input bit" << endl;
        set_irq_bits(irq_bits);
    }

    if(set_the_irq || set_the_bit) {
        cout << "Either IRQ or the pending bit is set" << endl;
        if(!set_the_irq) {
            cout << "Set the pendig bit test started w/ set_the_irq = 0; set_the_bit = 1" << endl;
            csr_read(0x344);
            if(is_supervisor_bit) {
                check(!!(armleocpu_csr->csr_to_rd & mask), "Expected bit to be set because it's stored");
            } else if(!is_supervisor_bit) {
                check(!(armleocpu_csr->csr_to_rd & mask), "Expected bit to be reset because it's not stored");
            }
            check_interrupt_pending(0, "Expected for the pending output to be reset, because the value is not stored");
            next_cycle();

            cout << "Checking in supervisor mode" << endl;
            from_machine_go_to_privilege(SUPERVISOR);
            csr_read(0x144);
            if(is_supervisor_bit) {
                check(!!(armleocpu_csr->csr_to_rd & mask), "Expected bit to be set because it's stored");
                check_interrupt_pending(1, "Expected for the pending output to be set, because the value is stored");
            } else if(!is_supervisor_bit) {
                check(!(armleocpu_csr->csr_to_rd & mask), "Expected bit to be reset because it's not stored");
                check_interrupt_pending(0, "Expected for the pending output to be reset, because the value is not stored");
            }
            next_cycle();
            csr_none();
            
            cout << "Checking in user mode" << endl;
            force_to_machine();
            from_machine_go_to_privilege(USER);
            if(is_supervisor_bit) {
                check_interrupt_pending(1, "Expected for the pending output to be set, because the value is stored (USER)");
            } else if(!is_supervisor_bit) {
                check_interrupt_pending(0, "Expected for the pending output to be reset, because the value is not stored (USER)");
            }
            next_cycle();
        } else {
            cout << "Set the pendig bit test started w/ set_the_irq = 1; set_the_bit = 1" << endl;
            // We can only check for register to be set, and output for both modes
            csr_read(0x344);
            check(!!(armleocpu_csr->csr_to_rd & mask), "Expected bit to be set");
            if(!is_supervisor_bit) {
                check_interrupt_pending(1, "Expected for the pending output to be set in machine mode for machine interrupt");
            } else {
                check_interrupt_pending(0, "Expected for the pending output to be reset in machine mode for supervisor interrupt");
            }
            next_cycle();

            cout << "Checking in supervisor mode" << endl;
            from_machine_go_to_privilege(SUPERVISOR);
            csr_read(0x144);
            if(is_supervisor_bit) {
                check(!!(armleocpu_csr->csr_to_rd & mask), "Expected bit to be set because it's visible in SIP");
            } else {
                check(!(armleocpu_csr->csr_to_rd & mask), "Expected bit to be reset because it's not visible in SIP");
            }
            check_interrupt_pending(1, "Expected for the pending output to be set in supervisor mode for supervisor/machine interrupt");
            next_cycle();
            csr_none();

            cout << "Checking in user mode" << endl;
            force_to_machine();
            from_machine_go_to_privilege(USER);
            check_interrupt_pending(1, "Expected for the pending output to be set in user mode for supervisor/machine interrupt");
            next_cycle();
        }
    } else {
        cout << "Test for MIP started w/ set_the_irq = 0; set_the_bit = 0" << endl;
        csr_read(0x344);
        check(!(armleocpu_csr->csr_to_rd & mask), "Expected bit to be reset");
        check_interrupt_pending(0, "Expected for reset values the pending bit to be reset");
        next_cycle();

        cout << "Checking interrupt from supervisor mode" << endl;
        from_machine_go_to_privilege(SUPERVISOR);
        csr_read(0x144);
        check(!(armleocpu_csr->csr_to_rd & mask), "Expected bit to be reset");
        check_interrupt_pending(0, "Expected no interrupt pending");
        next_cycle();
        csr_none();

        cout << "Checking interrupt from user mode" << endl;
        force_to_machine();
        from_machine_go_to_privilege(USER);
        check_interrupt_pending(0, "Expected no interrupt pending");
        next_cycle();
    }

    cout << "Test completed, going to machine mode" << endl;
    force_to_machine();
    enable_interrupt(mask);

    /*
    if(is_supervisor_bit) {
        // If supervisor bit then it should not be pending
        check(armleocpu_csr->interrupt_pending_output == 0, "Expected for reset values the pending bit to be reset");
    }
    from_machine_go_to_privilege(SUPERVISOR);
    check_interrupt_pending(0, "Expected for reset values the pending bit to be reset");
    */
}

void test_mip_sip(uint32_t mask, uint8_t irq_bits, bool is_supervisor_bit) {
    // TODO: Test the supervisor SIP bits behaviour too
    cout << "Test mip sip, mask = 0x" << hex << mask << endl
        << "  irq_bits = 0x" << int(irq_bits) << " is_supervisor_bit = " << is_supervisor_bit << endl
        << dec;
    
    subtest_mip_sip(mask, irq_bits, is_supervisor_bit, 0, 0);
    subtest_mip_sip(mask, irq_bits, is_supervisor_bit, 0, 1);
    subtest_mip_sip(mask, irq_bits, is_supervisor_bit, 1, 0);
    subtest_mip_sip(mask, irq_bits, is_supervisor_bit, 1, 1);
    
    /*
    cout << "Testing for interrupt set and MIP bit reset" << endl;
    force_to_machine();
    csr_write(0x300, csr_mstatus_mie);
    next_cycle(); // Enable interrupts

    set_irq_bits(irq_bits); // Set the interrupt signal
    next_cycle();

    csr_read(0x344);
    check((armleocpu_csr->csr_to_rd & mask) != 0, "Expected bit to be set");
    if(!is_supervisor_bit) {
        // If machine mode bit, then the interrupt have to be pending
        check(armleocpu_csr->interrupt_pending_output == 1, "If machine mode bit, then the interrupt have to be pending");
    } else {
        // If supervisor bit, then the interrupt should not happen, because it's lower privilege
        check(armleocpu_csr->interrupt_pending_output == 0, "If supervisor bit, then the interrupt should not happen, because it's lower privilege");
    }
    next_cycle();

    if(is_supervisor_bit) {
        from_machine_go_to_privilege(SUPERVISOR);
        check(armleocpu_csr->interrupt_pending_output == 1, "Interrupt should be pending if according bit is set");
    } else {
        check(armleocpu_csr->interrupt_pending_output == 1, "Interrupt should be pending if according bit is set");
    }

    cout << "Testing for interrupt set and MIP bit set" << endl;
    force_to_machine();
    csr_write(0x300, csr_mstatus_mie);
    next_cycle(); // Enable interrupts

    csr_read_set(0x344, mask);
    next_cycle();

    csr_read(0x344);
    check((armleocpu_csr->csr_to_rd & mask) != 0, "Expected bit to be set");
    check(armleocpu_csr->interrupt_pending_output == 1, "Expected for reset values the pending bit to be set");
    
    cout << "Testing for interrupt clear and MIP bit set" << endl;
    force_to_machine();
    csr_write(0x300, csr_mstatus_mie);
    next_cycle(); // Enable interrupts
    
    set_irq_bits(0);
    next_cycle();

    csr_read(0x344);
    if(!is_supervisor_bit) {
        check((armleocpu_csr->csr_to_rd & mask) == 0, "Expected bit to be reset, because value has not been stored");
        // If machine mode bit, then the interrupt have to be pending
        check(armleocpu_csr->interrupt_pending_output == 0, "If machine mode bit, then interrupt should not be pending because stored bit was not actually stored");
    } else {
        check((armleocpu_csr->csr_to_rd & mask) == 0, "Expected bit to be set, because value has been stored");
        // If supervisor bit, then the interrupt should not happen, because it's lower privilege
        check(armleocpu_csr->interrupt_pending_output == 0, "If supervisor bit, then the interrupt should not happen, because it's lower privilege");
    }
    next_cycle();

    if(is_supervisor_bit) {
        from_machine_go_to_privilege(SUPERVISOR);
        check(armleocpu_csr->interrupt_pending_output == 1, "Interrupt should be pending if according stored bit is set");
    } else {
        // Nothing to check for non supervisot bit
    }
    */

}


void test_mret() {
    force_to_machine();
    // Set MTVEC
    uint32_t mepc = rand() & 0xFFFFFFFC;
    csr_write(0x341, mepc);
    next_cycle();

    // Set MPIE/MIE
    // Set MPP
    // Set SPP
    // Set MPRV
    csr_write(0x300, (1 << 7) | (MACHINE << 11) | (SUPERVISOR << 8) | (1 << 17));
    next_cycle();

    armleocpu_csr->csr_cmd = ARMLEOCPU_CSR_CMD_MRET;
    armleocpu_csr->eval();
    check_no_cmd_error();
    check(armleocpu_csr->csr_next_pc == mepc, "Next pc is expected to be MEPC");

    next_cycle();

    csr_read(0x300);
    csr_read_check((1 << 3) | (1 << 7) | (SUPERVISOR << 8) | (1 << 17));
    // TODO: Check MPRV to be zero
    // Turns out the MPRV is only set when it's not returning to MACHINE mode
}

void test_sret(uint32_t privilege) {
    // TODO: Add tests for Supervisor privilege mode
    force_to_machine();
    
    // Set MTVEC
    uint32_t sepc = rand() & 0xFFFFFFFC;
    csr_write(0x141, sepc);
    next_cycle();

    // Set SPIE/SIE
    // Set SPP
    csr_write(0x300, (1 << 5) | (SUPERVISOR << 8));
    next_cycle();

    armleocpu_csr->csr_cmd = ARMLEOCPU_CSR_CMD_SRET;
    armleocpu_csr->eval();
    check_no_cmd_error();
    check(armleocpu_csr->csr_next_pc == sepc, "Next pc is expected to be MEPC");

    next_cycle();

    csr_read(0x100);
    csr_read_check((1 << 1) | (1 << 5));
    // TODO: Check MPRV to be zero
    // Turns out the MPRV is only set when it's not returning to MACHINE mode
}


#include "verilator_template_main_start.cpp"
    
    cout << "Fetch Test started" << endl;

    TOP->clk = 0;
    TOP->rst_n = 0;
    TOP->csr_cmd = ARMLEOCPU_CSR_CMD_NONE;
    TOP->instret_incr = 0;
    TOP->csr_exc_cause = 100; // Just some random value

    TOP->irq_mtip_i = 0;
    TOP->irq_stip_i = 0;
    TOP->irq_meip_i = 0;
    TOP->irq_seip_i = 0;
    TOP->irq_msip_i = 0;
    TOP->irq_ssip_i = 0;

    // check(0, "Test error");
    for(int i = 0; i < 10; i++) {
        TOP->rst_n = 0;
        csr_none();
        next_cycle();

        TOP->rst_n = 1;
        //force_to_machine();
        next_cycle();
    }

    
    

    start_test("MSCRATCH");
    test_scratch(0x340);

    

    start_test("MVENDORID");
    test_mro(0xF11, 0x0A1AA1E0);

    start_test("MARCHID");
    test_mro(0xF12, 1);

    start_test("MIMPID");
    test_mro(0xF13, 1);

    start_test("MHARTID");
    test_mro(0xF14, 0);

    start_test("MCONFIGPTR");
    test_mro(0xF15, 0x100);

    start_test("MTVEC");
    test_masked(0x305, 0xFFFFFFFC);


    start_test("MSTATUS");
    csr_read(0x300);
    csr_read_check(0x0);
    next_cycle();

    uint32_t val;

    for(int i = 0; i < 10; i++) {
        val = 
            (1 << 22) |
            (1 << 21) |
            (1 << 20) |
            (1 << 19) |
            (1 << 18) |
            (1 << 17);
        csr_write(0x300, val);
        next_cycle();
        csr_read(0x300);
        csr_read_check(val);
        
        check(TOP->csr_mstatus_tsr == 1, "Unexpected tsr");
        check(TOP->csr_mstatus_tw == 1, "Unexpected tw");
        check(TOP->csr_mstatus_tvm == 1, "Unexpected tvm");
        
        check(TOP->csr_mstatus_mprv == 1, "Unexpected mprv");
        check(TOP->csr_mstatus_mxr == 1, "Unexpected mprv");
        check(TOP->csr_mstatus_sum == 1, "Unexpected mprv");
        next_cycle();


        csr_write(0x300, 0);
        next_cycle();
        csr_read(0x300);
        csr_read_check(0);
        check(TOP->csr_mstatus_tsr == 0, "Unexpected tsr");
        check(TOP->csr_mstatus_tw == 0, "Unexpected tw");
        check(TOP->csr_mstatus_tvm == 0, "Unexpected tvm");
        
        check(TOP->csr_mstatus_mprv == 0, "Unexpected mprv");
        check(TOP->csr_mstatus_mxr == 0, "Unexpected mprv");
        check(TOP->csr_mstatus_sum == 0, "Unexpected mprv");
        next_cycle();
    }

    start_test("MSTATUSH");
    test_const(0x310, 0);


    // Mstatus mpp == 2'b10 impossibility
    for(int i = 0; i < 9; i++) {
        csr_read(0x300);
        val = armleocpu_csr->csr_to_rd;
        uint32_t orig_value = val;
        val &= ~(0b11 << 11);
        val |= (0b10 << 11);
        next_cycle();

        csr_write(0x300, val);
        next_cycle();
        
        csr_read(0x300);
        check(orig_value == armleocpu_csr->csr_to_rd, "Writing MPP = 2'b10 should not cause register change, but it did");
        next_cycle();
    }


    

    start_test("MISA");
    csr_read(0x301);
    csr_read_check(0b01000000000101000001000100000001);
    next_cycle();
    
    start_test("MISA: all one write, should not change MISA's value");
    csr_write(0x301, 0xFFFFFFFF);
    
    next_cycle();
    csr_read(0x301);
    csr_read_check(0b01000000000101000001000100000001);
    next_cycle();

    start_test("MISA: all zero write, should not change MISA's value");
    csr_write(0x301, 0);
    next_cycle();

    csr_read(0x301);
    csr_read_check(0b01000000000101000001000100000001);
    next_cycle();


    start_test("SSCRATCH");
    test_scratch(0x140);

    start_test("SEPC");
    test_masked(0x141, 0xFFFFFFFC);

    start_test("MEPC");
    test_masked(0x341, 0xFFFFFFFC);
    

    start_test("STVEC");
    test_masked(0x105, 0xFFFFFFFC);

    start_test("SCAUSE");
    test_scratch(0x142);

    start_test("MCAUSE");
    test_scratch(0x342);

    start_test("MTVAL");
    test_const(0x343, 0);

    start_test("STVAL");
    test_scratch(0x143);

    
    csr_read(0xB00);
    uint32_t begin_value = TOP->csr_to_rd;
    start_test("MCYCLE: Start time = " + begin_value);
    next_cycle();
    
    csr_read(0xB00);
    csr_read_check(begin_value + 1);
    next_cycle();

    csr_write(0xB80, 1);
    next_cycle();


    csr_write(0xB00, -1);
    next_cycle();
    
    csr_none();
    next_cycle();

    csr_read(0xB00);
    csr_read_check(0);
    next_cycle();

    csr_read(0xB80);
    csr_read_check(2);
    next_cycle();


    start_test("CYCLE/CYCLEH Test");
    csr_none();
    next_cycle();

    csr_write(0xB00, -1-2);
    next_cycle();

    csr_write(0xB80, -1);
    next_cycle();
    

    csr_read(0xB00);
    csr_read_check(-1-1);
    next_cycle();
    
    csr_read(0xB80);
    csr_read_check(-1);
    next_cycle();


    start_test("INSTRET");
    
    TOP->instret_incr = 1;
    csr_read(0xB02);
    csr_read_check(0);
    next_cycle();


    csr_read(0xB02);
    csr_read_check(1);
    next_cycle();

    csr_write(0xB82, 1);
    next_cycle();

    csr_write(0xB02, -1);
    next_cycle();

    csr_none();
    next_cycle();

    csr_read(0xB82);
    csr_read_check(2);
    next_cycle();

    csr_read(0xB02);
    csr_read_check(1);
    next_cycle();

    TOP->instret_incr = 0;
    csr_none();
    next_cycle();

    start_test("SATP");
    csr_write(0x180, 0x803FFFFF);
    check(TOP->csr_satp_mode == 0, "unexpected satp mode");
    check(TOP->csr_satp_ppn == 0, "unexpected satp ppn");
    next_cycle();

    csr_read(0x180);
    csr_read_check(0x803FFFFF);
    check(TOP->csr_satp_mode == 1, "unexpected satp mode");
    check(TOP->csr_satp_ppn == 0x3FFFFF, "unexpected satp ppn");
    next_cycle();

    test_masked(0x180, 0x803FFFFF);
    test_masked(0x180, 0x803FFFFF);
    test_masked(0x180, 0x803FFFFF);

    start_test("MEDELEG");
    test_const(0x302, 0);
    

    start_test("MIDELEG");
    test_const(0x303, 0);
    
    start_test("MIE");
    csr_write(0x304, 0xFFFF);
    next_cycle();

    csr_read(0x304);
    csr_read_check(0xAAA);
    next_cycle();

    csr_write(0x304, 0x0);
    next_cycle();

    csr_read(0x304);
    csr_read_check(0x0);
    next_cycle();


    start_test("SIE");
    csr_write(0x104, 0xFFFF);
    next_cycle();

    csr_read(0x104);
    csr_read_check(0x222);
    next_cycle();

    csr_write(0x104, 0x0);
    next_cycle();

    csr_read(0x104);
    csr_read_check(0x0);
    next_cycle();
    
    start_test("SSTATUS");
    csr_write(0x100, 0xFFFFFFFF);
    next_cycle();


    csr_read(0x100);
    csr_read_check(0x000C0122);
    next_cycle();
    

    csr_write(0x100, 0x0);
    next_cycle();

    csr_read(0x100);
    csr_read_check(0x0);
    next_cycle();
    

    start_test("MIP");
    csr_write(0x300, 0b1000); // mstatus.mie
    next_cycle();


    start_test("HPM Counter");

    for(uint32_t csr_address = 0xB03; csr_address <= 0xB1F; csr_address++) {
        test_const(csr_address, 0);
    }

    start_test("MCOUNTEREN");
    test_const(0x306, 0);

    start_test("SCOUNTEREN");
    test_const(0x106, 0);

    start_test("HPM Counter high");

    for(uint32_t csr_address = 0xB83; csr_address <= 0xB9F; csr_address++) {
        test_const(csr_address, 0);
    }

    start_test("HPM event");

    for(uint32_t csr_address = 0x323; csr_address <= 0x33F; csr_address++) {
        test_const(csr_address, 0);
    }

    test_cmd_error(); // Tests logic for incorrect cmd
    
    start_test("csr mcurrent privilege");
    csr_read(0xBC0);
    csr_read_check(0b11);
    next_cycle();

    csr_write(0xBC0, 0b10);
    next_cycle();

    check(armleocpu_csr->csr_mcurrent_privilege == 0b11, "Writing the mcurrent_privilege had unexpected effect");
    csr_read(0xBC0);
    csr_read_check(0b11);
    next_cycle();

    csr_write(0xBC0, 0b01);
    next_cycle();
    check(armleocpu_csr->csr_mcurrent_privilege == 0b01, "Writing the mcurrent_privilege had no effect");

    force_to_machine();
    next_cycle();

    csr_write(0xBC0, 0b00);
    next_cycle();
    csr_none();

    check(armleocpu_csr->csr_mcurrent_privilege == 0b00, "Writing the mcurrent_privilege had no effect");

    force_to_machine();
    next_cycle();
    
    test_mret();
    test_mret();
    test_mret();
    test_mret();

    test_sret(SUPERVISOR);
    test_sret(SUPERVISOR);
    
    test_sret(MACHINE);
    test_sret(MACHINE);
    // Note: can't test MRET to lower privileges
    force_to_machine();
    
    check(armleocpu_csr->csr_mcurrent_privilege == 0b11, "Expected after csr_mcurrent_privilege test to be in machine mode");
    start_test("MIP SIP test");
    test_mip_sip(csr_mie_meie, IRQ_BITS_MEIP, 0);
    test_mip_sip(csr_mie_msie, IRQ_BITS_MSIP, 0);
    test_mip_sip(csr_mie_mtie, IRQ_BITS_MTIP, 0);
    test_mip_sip(csr_mie_seie, IRQ_BITS_SEIP, 1);
    test_mip_sip(csr_mie_ssie, IRQ_BITS_SSIP, 1);
    test_mip_sip(csr_mie_stie, IRQ_BITS_STIP, 1);

    
    std::vector<int> privlist = {MACHINE, SUPERVISOR, USER};
    for(auto & priv : privlist) {
            test_exception(priv, csr_mstatus_mie);
            test_exception(priv, 0);
    }
    
    for(auto & priv : privlist) {
        for(uint8_t irq_bits = 0; irq_bits != 0b1000000; irq_bits++) {
            for(uint32_t enable_combinations = 0; enable_combinations != 0b1000000; enable_combinations++) {
                uint32_t mie = 0;
                mie |= (enable_combinations & IRQ_BITS_MEIP) ? csr_mie_meie : 0;
                mie |= (enable_combinations & IRQ_BITS_MSIP) ? csr_mie_msie : 0;
                mie |= (enable_combinations & IRQ_BITS_MTIP) ? csr_mie_mtie : 0;
                mie |= (enable_combinations & IRQ_BITS_SEIP) ? csr_mie_seie : 0;
                mie |= (enable_combinations & IRQ_BITS_SSIP) ? csr_mie_ssie : 0;
                mie |= (enable_combinations & IRQ_BITS_STIP) ? csr_mie_stie : 0;
                // TODO: Test setting MIP values
                test_interrupt(priv, 0, mie,
                    irq_bits);
                test_interrupt(priv, csr_mstatus_mie, mie,
                    irq_bits);
            }
        }
    }


    
    // TODO: Test write to non writable non existent location x3

    // TODO: Test SIP clearing

    // TODO: Test accessing non existent CSR

    
    //TODO: test_sret();

    // TODO: Test machine registers for access from supervisor
    // TODO: Test user accessing supervisor registers

    

    csr_none();
    next_cycle();
    
    //throw runtime_error("CSR Tests are done but incomplete, TODO: Add tests for all CSRs");
    cout << "CSR Tests done" << endl;

#include <verilator_template_footer.cpp>