`ifndef V_CONTROL_SELECT
`define V_CONTROL_SELECT


`include "../74138/hct74138.v"
`include "../74139/hct74139.v"
`include "../74245/hct74245.v"

// verilator lint_off ASSIGNDLY
// verilator lint_off STMTDLY

`timescale 1ns/100ps

module control_selector #(parameter LOG=0) 
(
    input [7:0] hi_rom,

    output _rom_out, _ram_out, _alu_out, _uart_out,

    output force_alu_op_to_passx,
    output force_x_val_to_zero,
    output _ram_zp,

    output [4:0] device_in
);

    // constants
    parameter [2:0] op_DEV_eq_ROM_sel = 0;
    parameter [2:0] op_DEV_eq_RAM_sel = 1;
    parameter [2:0] op_DEV_eq_RAMZP_sel = 2;
    parameter [2:0] op_DEV_eq_UART_sel = 3;
    parameter [2:0] op_NONREG_eq_OPREGY_sel = 4;
    parameter [2:0] op_REGX_eq_ALU_sel = 5;
    parameter [2:0] op_RAMZP_eq_REG_sel = 6;
    parameter [2:0] op_RAMZP_eq_UART_sel = 7;

    // wiring
    // dual and     5 = 1x7408 quad dual and and use one of the 7411s
    // triple and   2 =	1x7411 triple triple input, use one gate as dual
    // not          4 =	1x7404 inverter
    // or           1 =	1x7432 quad or
    // or 17 diodes and 3 not and the 74245 can be replaced by 10 diodes in an AND cfg
    // plus             1x74245 buf
    //                  1x74138 decoder


    wire [2:0] operation_sel = hi_rom[7:5];
    wire [7:0] _decodedOp;
    hct74138 opDecoder(.Enable3(1'b1), .Enable2_bar(1'b0), .Enable1_bar(1'b0), .A(operation_sel), .Y(_decodedOp));
    
    // BUS ACCESS - FIXME - short spikes of high current possible.
    // With logic gates this setup causes contention due to differences in timings on the _out's. 
    // A rom based approach might also be glitchy due to unpredicable values during transitions.
    // Diode logic would minimise timings diffs - very short periods of randomness during transitions.
    // Correct approach is not to use 74245's etc and instead to connect to the bus using open collector outputs.
    // 3 input AND = 11ns - https://assets.nexperia.com/documents/data-sheet/74HC_HCT11.pdf
    // 2 input AND = 11ns - https://assets.nexperia.com/documents/data-sheet/74HC_HCT08.pdf
    // inverter = 8ns - https://assets.nexperia.com/documents/data-sheet/74HC_HCT04.pdf
    assign  _rom_out = _decodedOp[op_DEV_eq_ROM_sel];
    assign  #11 _ram_out = _decodedOp[op_DEV_eq_RAM_sel] && _decodedOp[op_DEV_eq_RAMZP_sel];
    assign  #11 _uart_out = _decodedOp[op_DEV_eq_UART_sel] && _decodedOp[op_RAMZP_eq_UART_sel];
    assign  #11 _alu_out = _decodedOp[op_NONREG_eq_OPREGY_sel] && _decodedOp[op_REGX_eq_ALU_sel] && _decodedOp[op_RAMZP_eq_REG_sel];

    // _ram_zp will turn off the ram address buffers letting HiAddr pull down to 0 and will turn on ROM->MARLO for the lo addr
    assign #11 _ram_zp = _decodedOp[op_DEV_eq_RAMZP_sel] && _decodedOp[op_RAMZP_eq_REG_sel] && _decodedOp[op_RAMZP_eq_UART_sel];
    
    assign #8 force_x_val_to_zero = !_decodedOp[op_NONREG_eq_OPREGY_sel];  // +ve logic needed - EXTRA GATE
    assign #8 force_alu_op_to_passx = !_decodedOp[op_RAMZP_eq_REG_sel]; // +ve logic needed - EXTRA GATE
    
    // write device - sometimes bit 0 is a device bit and sometimes ALU op bit
    wire #11 _ram_write_override = _decodedOp[op_RAMZP_eq_REG_sel] && _decodedOp[op_RAMZP_eq_UART_sel];
    wire #11 _is_non_reg_override = _decodedOp[op_NONREG_eq_OPREGY_sel] && _ram_write_override;
    wire #11 _is_reg_override = _decodedOp[op_REGX_eq_ALU_sel];
    wire implied_dev_top_bit = hi_rom[0];

    wire #11 implied_reg_in_not_overridden = implied_dev_top_bit && _is_non_reg_override;
    wire #8 is_reg_override = ! _is_reg_override; // EXTRA GATE
    wire #11 reg_in = implied_reg_in_not_overridden || is_reg_override; // EXTRA GATE

    wire [4:0] device_sel_pre = {reg_in, hi_rom[4:1]}; // pull down top bit if this instruction applies to non-reg as that bit is used by ALU

    // apply ram write device override
    wire [7:0] device_sel_in = {3'b0, device_sel_pre};
    tri0 [7:0] device_sel_out; // !!! PULLED DOWN WIRES

    hct74245 #(.NAME("F_RAMWR")) bufDeviceSel(.A(device_sel_in), .B(device_sel_out), .dir(1'b1), .nOE( !_ram_write_override ));  // EXTRA GATE
    //pulldown (weak0, weak1) pullDeviceToZero[7:0](device_sel_out);

    // return
    assign #18 device_in = device_sel_out[4:0];

if (LOG)    always @ * 
         $display("%8d CTRL_SEL", $time,
          " hi=%08b", hi_rom, 
            " _rom_out=%1b, _ram_out=%1b, _alu_out=%1b, _uart_out=%1b", _rom_out, _ram_out, _alu_out, _uart_out,
            " device_in=%5b", device_in, 
            " force_x_0=%1b", force_x_val_to_zero, 
            " force_alu_passx=%1b", force_alu_op_to_passx, 
            " _ram_zp=%1b", _ram_zp
    ,implied_dev_top_bit , _is_non_reg_override, _is_reg_override, " %05b %05b %08b", device_sel_pre, device_in, device_sel_out
);

endmodule : control_selector
// verilator lint_on ASSIGNDLY

`endif
