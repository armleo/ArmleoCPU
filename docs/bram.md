# BRAM

The BRAM peripheral is split into a top module and BRAM-local submodules.

`BRAM` lives in `armleocpu.peripheral`. Its helper modules live in `armleocpu.peripheral.bram`.

## Structure

- `BRAM`: top-level read/write bus wrapper. It accepts ID-bearing AR/AW channels, prioritizes writes, rejects misaligned accesses through the error handler, strips IDs before the core, and restores IDs on responses.
- `AXBRAM`: ID-less BRAM core with one shared AX request channel. Reads and writes share the address path; read-while-write is stalled by the single shared request channel.
- `BRAMBusParams`: BRAM-local bus parameters with no ID field.
- `AXBus`: internal ID-less shared-address bus.
- `IdYanker`: saves IDs from AR/AW and restores them onto R/B.
- `DataArray`: owns the masked byte-addressed memory array.
- `Reader`: accepts aligned AR requests and streams R beats after the first memory read latency.
- `Writer`: accepts aligned AW/W requests and returns B after the write burst.
- `ReadMisalignmentChecker`: zero-latency aligned read pass-through; misaligned reads are blocked.
- `WriteMisalignmentChecker`: zero-latency aligned write pass-through; misaligned writes are blocked.
- `MisalignmentChecker`: wrapper around the read/write misalignment checkers.
- `MisalignedAccessHandler`: handles misaligned AR/AW requests without touching the data array.
- `RequestKeeper`: keeps request ID and burst remaining count for the active read or write.
- `BurstManager`: keeps the active burst address and computes the next beat address.
- `RequestMuxControl`: selects the active owner for shared request state.
- `DataArrayReqMux`: selects reader or writer data-array requests.

`BRAM` derives its memory depth from `bp.addrWidth` and `bp.busBytes`; it does not take a separate `sizeInWords` parameter. The memory file is implicit.

## Synthesizable Testbench

`SynthesizableTestbench` instantiates:

- `Stimulator`: generates randomized reads/writes, aligned and misaligned accesses, and burst/non-burst requests.
- `Scoreboard`: mirrors accepted aligned writes using `Mem`.
- `Checker`: monitors responses and compares valid written bytes against the scoreboard.

The randomized stimulus targets approximately 10 percent misaligned requests, 90 percent aligned requests, 50 percent writes, 50 percent reads, 50 percent bursts, and 50 percent non-bursts.

Memory-file initialization is not covered by this testbench yet.
