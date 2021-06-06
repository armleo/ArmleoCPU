/*
`define assert(expr) \
    if (!(expr)) begin \
        $display("[%d] ASSERTION FAILED in %m: ", $time, expr); \
        $fatal; \
    end
*/


`define assert_equal(signal, value) \
        if ((signal) !== (value)) begin \
            $display("[%d] ASSERTION FAILED in %m: signal(%d) != value(%d)", $time, signal, value); \
            $fatal; \
        end