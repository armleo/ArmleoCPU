# Hardware Task List

This document is the running implementation and verification checklist for the reusable hardware blocks in this repository. Keep it broad: every current L3 cache and BRAM module is listed, and placeholders are included for future top-level wrappers, testbenches, and reusable verification elements.

Status legend:

- `[x]`: present in source tree
- `[~]`: present but incomplete or integration is still evolving
- `[ ]`: planned or not yet implemented

## L3 Cache

### Top and Wrappers

- `[~]` `Bank`: single L3 bank controller.
- `[~]` `Multibanker`: address-based bank wrapper with one downstream bus per bank.
- `[ ]` `L3Cache`: final integrated top-level cache wrapper.
- `[ ]` Downstream arbitration wrapper for multi-bank memory ports.
- `[ ]` Flush/flush-remove wrapper and global maintenance control.

### Storage and Addressing

- `[x]` `DataArray`: bank-local set/way data, tag, and metadata SRAM.
- `[x]` `addressUtils`: cache index/tag address helpers.
- `[x]` `Entry`, `EntryFlags`, `Req`, `BankIO`: L3 cache bundles.
- `[x]` `L3CacheParams`: parameter container.
- `[ ]` Directory storage and directory address helpers.
- `[ ]` ECC/parity storage.

### Request, Refill, and Writeback

- `[~]` `DownstreamRequester`: downstream read request helper.
- `[~]` `RefillWriter`: writes successful downstream refill data into `DataArray`.
- `[x]` `DownstreamRespForwarder`: forwards refill errors upstream.
- `[~]` `Writebacker`: writes dirty victim entries downstream.
- `[ ]` Request replay/retry manager.
- `[ ]` Refill response merge/forward path to original requester.

### Snoop and Coherence Helpers

- `[~]` `SnoopRequest`: emits snoop requests to selected cores.
- `[~]` `SnoopResponse`: collects snoop responses and optional data.
- `[ ]` Coherence state update helper.
- `[ ]` Shared/unique ownership transition helper.
- `[ ]` Snoop interrupt/writeback interlock.

### Victim and Reset

- `[x]` `VictimAvailability`: selects invalid or clean ways.
- `[x]` `VictimSelection`: rotating victim selector.
- `[x]` `Reseter`: invalidates data array and clears victim selection.
- `[ ]` Dirty victim scheduling policy.
- `[ ]` Voluntary eviction engine.

### L3 Testbenches

- `[ ]` `DataArray` focused testbench.
- `[ ]` `VictimAvailability` and `VictimSelection` testbench.
- `[ ]` `SnoopRequest` and `SnoopResponse` testbench.
- `[ ]` `DownstreamRequester`, `RefillWriter`, and `Writebacker` testbench.
- `[ ]` Single-bank `Bank` directed testbench.
- `[ ]` Multi-bank `Multibanker` address-distribution testbench.
- `[ ]` Coherence scenario testbench.
- `[ ]` Randomized L3 stress testbench.

## BRAM

### Top and Wrappers

- `[~]` `AXBRAM`: ID-less BRAM core using one shared AX request channel.
- `[~]` `BRAM`: wrapper that accepts read/write address channels, prioritizes writes, and restores response IDs.
- `[x]` `BRAMBusParams`: BRAM-local bus parameters with `idWidth = 0`.
- `[x]` `AXBus`, `AXPayload`: ID-less shared request bus and payload.
- `[x]` `IdYanker`: strips IDs before the ID-less core and restores them on responses.

### Data and Burst Helpers

- `[x]` `DataArray`: masked byte-lane storage array.
- `[x]` `DataArrayReq`, `DataArrayResp`, `DataArrayIO`: data-array bundles.
- `[x]` `Reader`: aligned read path.
- `[x]` `Writer`: aligned write path.
- `[x]` `RequestKeeper`: tracks burst length and ID for an active transaction.
- `[x]` `BurstManager`: keeps current/incremented burst address.
- `[x]` `RequestMuxControl`: selects current active owner.
- `[x]` `DataArrayReqMux`: selects reader or writer data-array request.

### Misalignment and Errors

- `[x]` `ReadMisalignmentChecker`: zero-latency aligned AR pass-through; blocks misaligned AR.
- `[x]` `WriteMisalignmentChecker`: zero-latency aligned AW pass-through; blocks misaligned AW.
- `[x]` `MisalignmentChecker`: read/write checker wrapper.
- `[x]` `MisalignedAccessHandler`: consumes misaligned requests and returns DECERR.
- `[ ]` Optional configurable misalignment response policy.

### BRAM Testbench Elements

- `[x]` `Stimulator`: randomized request generator.
- `[x]` `Scoreboard`: Mem-backed expected-data model.
- `[x]` `Checker`: response monitor and scoreboard comparator.
- `[x]` `SynthesizableTestbench`: synthesizable BRAM testbench wrapper.
- `[ ]` Dedicated misalignment testbench.
- `[ ]` Dedicated ID restoration testbench for `IdYanker`.
- `[ ]` Read-only bandwidth testbench for `AXBRAM`.
- `[ ]` Write-only bandwidth testbench for `AXBRAM`.
- `[ ]` Read/write conflict and stall testbench.
- `[ ]` Memory-file initialization testbench.

Memory-file initialization is not covered by the current synthesizable BRAM testbench.

## General Future Modules

- `[ ]` Shared Decoupled monitor/checker library.
- `[ ]` AX/AR/AW protocol assertion modules.
- `[ ]` Stable-record assertions for all Decoupled payloads.
- `[ ]` Reusable randomized stall injectors for all bus channels.
- `[ ]` Reusable burst scoreboard.
- `[ ]` Formal covers/assertions for bus progress and response matching.
- `[ ]` Documentation generator that lists module status from source annotations.
