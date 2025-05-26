import sys

def binary_to_verilog_hex(input_file, output_file, width=1):
    with open(input_file, "rb") as f_in, open(output_file, "w") as f_out:
        while True:
            chunk = f_in.read(width)
            if not chunk:
                break
            # Pad chunk if not full width at end of file
            chunk += b'\x00' * (width - len(chunk))
            # Convert to hex string (little-endian)
            hex_str = ''.join(f"{b:02X}" for b in reversed(chunk))
            f_out.write(f"{hex_str}\n")

if __name__ == "__main__":
    if len(sys.argv) not in (3, 4):
        print("Usage: python convert_binary_to_verilog_hmem.py <input.bin> <output.hex> [width_bytes]")
        sys.exit(1)
    width = int(sys.argv[3]) if len(sys.argv) == 4 else 1
    if width not in (1, 2, 4):
        print("Width must be 1, 2, or 4 bytes (8/16/32 bits per line)")
        sys.exit(2)
    binary_to_verilog_hex(sys.argv[1], sys.argv[2], width)