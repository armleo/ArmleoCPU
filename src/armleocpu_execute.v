module armleocpu_execute (
    input clk,
    input rst_n,

    input [31:0]            rs1_rdata,
    input [31:0]            rs2_rdata,

    
    output reg [3:0]        csr_cmd,
    output     [11:0]       csr_address,
    input                   csr_invalid,
    input      [31:0]       csr_readdata,
    output reg [31:0]       csr_writedata,


    output     [4:0]        rd_addr,
    output reg [31:0]       rd_wdata,
    output reg              rd_write


    input [31:0]            d2e_pc,
    input                   d2e_interrupt_pending,
    input                   d2e_instr_valid,
    input [31:0]            d2e_instr,
    input                   d2e_instr_illegal,

    input [`ARMLEOCPU_ALU_SELECT_WIDTH-1:0]
        d2e_instr_decode_alu_output_sel,

    input [`ARMLEOCPU_MULDIV_SELECT_WIDTH-1:0]
        d2e_instr_decode_muldiv_sel,

    input [`ARMLEOCPU_DECODE_INSTRUCTION_WIDTH-1:0]
        d2e_instr_decode_type,
    
    input [`ARMLEOCPU_DECODE_IN0_MUX_SEL_WIDTH-1:0]
        d2e_instr_decode_alu_in0_mux_sel,

    input [`ARMLEOCPU_DECODE_IN1_MUX_SEL_WIDTH-1:0]
        d2e_instr_decode_alu_in1_mux_sel,
    
    input d2e_instr_decode_shamt_sel,
    input [`ARMLEOCPU_DECODE_RD_SEL_WIDTH-1:0]
        d2e_instr_decode_rd_sel,
    input                   d2e_instr_fetch_exception,
    input [31:0]            d2e_instr_fetch_exception_cause,


    output                  e2d_ready,
    output [1:0]            e2d_cmd,
    output [31:0]           e2d_jump_target,
    
    output                  e2d_rd_write,
    output [4:0]            e2d_rd_waddr,

    output reg              e2debug_machine_ebreak




    // Memory <-> Cache / AXI4 Bus in no cache configuration
    // AW Bus
    output	reg  				        M_AXI_AWVALID,
    input	wire				        M_AXI_AWREADY,
    output	reg 	[31:0]	            M_AXI_AWADDR,
    output	wire	[7:0]			    M_AXI_AWLEN,
    output	wire	[2:0]			    M_AXI_AWSIZE,
    output	wire	[1:0]			    M_AXI_AWBURST,
    output	wire				        M_AXI_AWLOCK,
    output	wire	[2:0]			    M_AXI_AWPROT, // Read documentation about this signal value represantation
    // W Bus
    output	wire				        M_AXI_WVALID,
    input	wire				        M_AXI_WREADY,
    output	wire	[31:0]	            M_AXI_WDATA,
    output	wire	[3:0]               M_AXI_WSTRB,
    output	wire				        M_AXI_WLAST
    // B-bus
    input	wire				        M_AXI_BVALID,
    output	wire				        M_AXI_BREADY,
    input	wire	[1:0]			    M_AXI_BRESP,
    input   wire                        M_AXI_BUSER, // [0] shows pagefault, BRESP should be 2'b11

    // AR-Bus
    output	reg				            M_AXI_ARVALID,
    input	wire				        M_AXI_ARREADY,
    output	reg	[31:0]	                M_AXI_ARADDR,
    output	reg	[7:0]			        M_AXI_ARLEN,
    output	reg	[2:0]			        M_AXI_ARSIZE,
    output	reg	[1:0]			        M_AXI_ARBURST,
    output	reg				            M_AXI_ARLOCK,
    output	reg	[2:0]			        M_AXI_ARPROT, // Read documentation about this signal value represantation
    // R-Bus
    input	wire				        M_AXI_RVALID,
    output	reg				            M_AXI_RREADY,
    input	wire	[31:0]	            M_AXI_RDATA,
    input	wire				        M_AXI_RLAST,
    input	wire	[1:0]			    M_AXI_RRESP,
    input   wire                        M_AXI_RUSER // [0] shows pagefault RRESP should be 2'b11

    
);

assign e2d_rd_write = rd_write;
assign e2d_rd_waddr = rd_wdata;

assign M_AXI_AWSIZE = 3'b010;
assign M_AXI_ARSIZE = 3'b010;
assign M_AXI_ARLEN = 0;
assign M_AXI_AWLEN = 0;
assign M_AXI_WLAST = 1; // Only one word is possible
assign M_AXI_AWBURST = 2'b01; // INCR
assign M_AXI_ARBURST = 2'b01; // INCR


assign M_AXI_ARADDR = alu_result;
assign M_AXI_AWADDR = alu_result;

// TODO: Calculate PROT based on mcurrent_privilege
wire [2:0] prot = {1'b0, 1'b0, 1'b0};

assign M_AXI_AWPROT = prot;
assign M_AXI_ARPROT = prot;


reg address_done_nxt, address_done;
reg data_done_nxt, data_done;


wire loadgen_missaligned;
wire loadgen_unknowntype;
wire [31:0] loadgen_dataout;
armleocpu_loadgen loadgen(
    .inword_offset(alu_result[1:0]),
    .load_type(funct3),
    .loadgen_datain(M_AXI_RDATA),
    .loadgen_dataout(loadgen_dataout),
    .loadgen_missaligned(loadgen_missaligned),
    .loadgen_unknowntype(loadgen_unknowntype)
);

wire storegen_missaligned;
wire storegen_unknowntype;

armleocpu_storegen storegen(
    .inword_offset(alu_result[1:0]),
    .store_type(funct3),

    .storegen_datain(rs2),

    .storegen_dataout(M_AXI_WDATA),
    .storegen_datamask(M_AXI_WSTRB),
    .storegen_missaligned(storegen_missaligned),
    .storegen_unknowntype(storegen_unknowntype)
);


always @* begin
    case(d2e_instr_decode_alu_in0_mux_sel)
        `ARMLEOCPU_DECODE_IN0_MUX_SEL_RS1: alu_op1 = rs1_rdata;
        `ARMLEOCPU_DECODE_IN0_MUX_SEL_PC: alu_op1 = pc;
    endcase
end

always @* begin
    case(d2e_instr_decode_alu_in1_mux_sel)
        `ARMLEOCPU_DECODE_IN1_MUX_SEL_RS2: alu_op2 = rs2_rdata;
        `ARMLEOCPU_DECODE_IN1_MUX_SEL_SIMM12: alu_op2 = ;
        `ARMLEOCPU_DECODE_IN1_MUX_SEL_CONST4: alu_op2 = 4;
        `ARMLEOCPU_DECODE_IN1_MUX_SEL_IMM_JAL_OFFSET: alu_op2 = ;
        `ARMLEOCPU_DECODE_IN1_MUX_SEL_IMM_BRANCH_OFFSET: alu_op2 = ;
        `ARMLEOCPU_DECODE_IN1_MUX_SEL_IMM_STORE: alu_op2 = ;
        `ARMLEOCPU_DECODE_IN1_MUX_SEL_ZERO: alu_op2 = 0;
    endcase
end

reg [31:0] alu_op1;
reg [31:0] alu_op2;

wire [31:0] alu_result;

armleocpu_alu alu(
    .select_result(d2e_instr_decode_alu_output_sel),
    .shamt_sel(d2e_instr_decode_shamt_sel),
    
    .shamt(d2e_instr[24:20]),
    .op1(alu_op1),
    .op2(alu_op2),

    .result(alu_result)
);


wire [31:0] lui_imm = ;
wire [31:0] muldiv_result;
// TODO: MULDIV

// TODO: CSR
wire [31:0] csr_readdata;


reg store_condtional_success; // TODO:

always @* begin
    case(d2e_instr_decode_rd_sel)
        `ARMLEOCPU_DECODE_RD_SEL_ALU: rd_wdata = alu_result;
        `ARMLEOCPU_DECODE_RD_SEL_MEMORY: rd_wdata = loadgen_dataout;
        `ARMLEOCPU_DECODE_RD_SEL_LUI: rd_wdata = lui_imm;
        //`ARMLEOCPU_DECODE_RD_SEL_MULDIV: rd_wdata = muldiv_result;
        `ARMLEOCPU_DECODE_RD_SEL_PC_PLUS_4: rd_wdata = d2e_instr_pc_plus_4;
        //`ARMLEOCPU_DECODE_RD_SEL_STORE_CONDITIONAL_RESULT: rd_wdata = store_condtional_success;
        //`ARMLEOCPU_DECODE_RD_SEL_CSR: rd_wdata = csr_readdata;
        default: rd_wdata = alu_result;
    endcase
end

always @(posedge clk) begin
    if(!rst_n) begin
        address_done <= 0;
        data_done <= 0;
    end else begin
        address_done <= address_done_nxt;
        data_done <= data_done_nxt;
        
    end
end




always @* begin
    M_AXI_ARVALID = 0;
    M_AXI_RREADY = 0;
    M_AXI_AWVALID = 0;
    M_AXI_WREADY = 0;
    M_AXI_BREADY = 0;

    e2d_jump_target = alu_result;

    address_done_nxt = address_done;
    data_done_nxt = data_done;
    e2d_ready = 1;
    rd_write = 0;

    if(d2e_instr_valid) begin
        if(d2e_interrupt_pending) begin
            // TODO: Decide wich interrupt
            exception_start(`EXCEPTION_CODE_EXTERNAL_INTERRUPT);
        end else if(d2e_instr_fetch_exception) begin
            exception_start(d2e_instr_fetch_exception_cause);
        end else if(d2e_instr_illegal) begin
            exception_start(`EXCEPTION_CODE_ILLEGAL_INSTRUCTION);
        end else begin
            case(d2e_instr_decode_type)
                `ARMLEOCPU_DECODE_INSTRUCTION_ALU: begin
                    rd_write = (rd_addr != 0);
                    e2d_ready = 1;
                end
                /*
                `ARMLEOCPU_DECODE_INSTRUCTION_MULDIV: begin
                    rd_write = (rd_addr != 0);
                    e2d_ready = 1;
                end*/
                `ARMLEOCPU_DECODE_INSTRUCTION_JUMP: begin
                    rd_write = (rd_addr != 0);
                    e2d_ready = 1;
                    e2d_cmd = `ARMLEOCPU_PIPELINE_CMD_BRANCH;
                end
                `ARMLEOCPU_DECODE_INSTRUCTION_BRANCH: begin
                    e2d_ready = 1;
                    if(brcond_branchtaken)
                        e2d_cmd = `ARMLEOCPU_PIPELINE_CMD_BRANCH;
                end
                /*`ARMLEOCPU_DECODE_INSTRUCTION_LOAD: begin

                end
                */
                default: begin
                    exception_start(`EXCEPTION_CODE_ILLEGAL_INSTRUCTION);
                end
            endcase
        end
    end
end


task decoupled_check;
input valid;
input ready;
begin
    if(!ready && !$past(ready) && $past(valid))
        assert(valid);
    if(ready && past(valid))
        assert(valid);
end
endtask


always @(posedge clk) begin
    if(rst_n) begin
        decoupled_check(M_AXI_AWVALID, M_AXI_AWREADY);
        decoupled_check(M_AXI_WVALID, M_AXI_WREADY);
        decoupled_check(M_AXI_BVALID, M_AXI_BREADY);

        decoupled_check(M_AXI_ARVALID, M_AXI_ARREADY);
        decoupled_check(M_AXI_RVALID, M_AXI_RREADY);

        // New transactions should not start until done
        if(M_AXI_ARVALID && M_AXI_ARREADY) begin
            assert(!addressed);
            addressed <= 1;
        end
        if(M_AXI_RVALID && M_AXI_RREADY) begin
            assert(addressed || M_AXI_ARVALID && M_AXI_ARREADY);
            addressed <= 0;
        end

        
        if($past(M_AXI_AWVALID) && M_AXI_AWVALID && !$past(M_AXI_AWREADY)) begin
            assert($stable(M_AXI_AWADDR));
            assert($stable(M_AXI_AWLOCK));
            assert($stable(M_AXI_AWPROT));
        end

        if($past(M_AXI_WVALID) && M_AXI_WVALID && !$past(M_AXI_WREADY)) begin
            assert($stable(M_AXI_WDATA));
            assert($stable(M_AXI_WSTRB));
            assert($stable(M_AXI_WLAST));
        end

        if($past(M_AXI_BVALID) && M_AXI_BVALID && !$past(M_AXI_BREADY)) begin
            `ifdef FORMAL
            assume($stable(M_AXI_BRESP));
            assume($stable(M_AXI_BUSER));
            `endif
            assert($stable(M_AXI_BRESP));
            assert($stable(M_AXI_BUSER));
        end

        if($past(M_AXI_ARVALID) && M_AXI_ARVALID && !$past(M_AXI_ARREADY)) begin
            assert($stable(M_AXI_ARADDR));
            assert($stable(M_AXI_ARLOCK));
            assert($stable(M_AXI_ARPROT));
        end
        
        if($past(M_AXI_RVALID) && M_AXI_RVALID && !$past(M_AXI_RREADY)) begin
            `ifdef FORMAL
            assume($stable(M_AXI_RDATA));
            assume($stable(M_AXI_RLAST));
            assume($stable(M_AXI_RRESP));
            assume($stable(M_AXI_RUSER));
            `endif
            assert($stable(M_AXI_RDATA));
            assert($stable(M_AXI_RLAST));
            assert($stable(M_AXI_RRESP));
            assert($stable(M_AXI_RUSER));
        end

        if(rd_write)
            assert(e2d_ready);
        assert(csr_mcurrent_privilege != 10);
    end
end


/*
reg reseted = 0;
always @(posedge clk) begin
    if(!rst_n) begin
        reseted <= 0;
    end if(rst_n && reseted) begin
        if(d2e_instr_valid && !e2d_ready) begin
            fixed_valid <= 1
            fixed_d2e_pc
            fixed_d2e_interrupt_pending
            fixed_d2e_instr
            fixed_d2e_instr_illegal
            fixed_d2e_instr_decode_alu_output_sel
            fixed_d2e_instr_decode_muldiv_sel
            fixed_d2e_instr_decode_type
            fixed_d2e_instr_decode_alu_in0_mux_sel
            fixed_d2e_instr_decode_alu_in1_mux_sel
            fixed_d2e_instr_decode_shamt_sel
            fixed_d2e_instr_decode_rd_sel
            fixed_d2e_instr_fetch_exception
            fixed_d2e_instr_fetch_exception_cause
        end
        // Makesure rdata does not change
        // Make sure d2e_instr and other bits are not changing
    end
end*/

endmodule