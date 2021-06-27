`ifndef MAXIMUM_ERRORS
    `define MAXIMUM_ERRORS 1
`endif
integer assert_errors = 0;


`define assert(expr) \
    if ((!(expr)) === 1) begin \
        $display("[%d] !ERROR! ASSERTION FAILED in %m: ", $time, expr); \
        assert_errors = assert_errors + 1; \
        if(assert_errors == `MAXIMUM_ERRORS) \
            $fatal; \
    end


`define assert_equal(signal, value) \
        if ((signal) !== (value)) begin \
            $display("[%d] !ERROR! ASSERTION FAILED in %m: signal(%d) != value(%d)", $time, signal, value); \
            assert_errors = assert_errors + 1; \
            if(assert_errors == `MAXIMUM_ERRORS) \
                $fatal; \
        end