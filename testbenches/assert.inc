
`define assert(signal, value) \
        if ((signal) !== (value)) begin \
            $display("[%d] ASSERTION FAILED in %m: signal(%d) != value(%d)", $time, signal, value); \
            `ifdef __ICARUS__ $finish_and_return(1); `endif \
			$finish(1); \
        end