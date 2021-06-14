
`define assert(expr) \
    if ((!(expr)) === 1) begin \
        $display("[%d] !ERROR! ASSERTION FAILED in %m: ", $time, expr); \
        $fatal; \
    end



`define assert_equal(signal, value) \
        if ((signal) !== (value)) begin \
            $display("[%d] !ERROR! ASSERTION FAILED in %m: signal(%d) != value(%d)", $time, signal, value); \
            $fatal; \
        end