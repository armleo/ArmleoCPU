# L3 Cache

The L3 cache is an inclusive cache shared by the cores in a CCX. It sits between the coherent upstream core-facing buses and the downstream memory/interconnect buses.

The implementation is organized as a set of independent banks. Address bits at cache-line granularity select the bank, and the selected bank receives a narrowed address with the bank-select bits removed. The downstream side of the multibanker exposes one bus per bank; arbitration between those downstream buses is intentionally left to the integration point.

## Address Mapping

Cache lines are `ccx.cacheLineBytes` bytes. The bank selector is taken from the address bits immediately above the cache-line offset:

```text
bank = addr[ccx.cacheLineLog2 + bankBits - 1 : ccx.cacheLineLog2]
```

Inside a bank, the address is narrowed by removing those `bankBits` bits:

```text
bankAddr = { addr[high : ccx.cacheLineLog2 + bankBits],
             addr[ccx.cacheLineLog2 - 1 : 0] }
```

This makes each sequential cache line map to the next bank, wrapping at `bankCount`.

## Main Modules

### `Multibanker`

`Multibanker` is the bank wrapper. It instantiates `bankCount` `Bank` modules and routes each upstream request to exactly one bank based on the cache-line address bits.

Its upstream side is shared across all banks:

```scala
val up = Vec(ccx.coreCount, Flipped(new CoherentBus()(cbp)))
```

Its downstream side has one narrowed bus per bank:

```scala
val down = Vec(bankCount, new ReadWriteBus()(bankCbp))
```

The wrapper narrows addresses for `ar`, `aw`, and `creq` before forwarding to a bank. It records the selected bank for the write-data channel so `w` follows the previously accepted `aw`. Responses from banks back to each core are arbitrated per core for `r`, `b`, `cresp`, and `cdata`.

### `Bank`

`Bank` is one cache bank. It owns the cache data/tag array for its address shard and handles upstream coherent requests from all cores.

Current structure:

- `awArb`: round-robin arbiter for upstream write-address requests.
- `arArb`: round-robin arbiter for upstream read-address requests.
- `DataArray`: stores tags, flags, sharer state, and cache-line data.
- `Reseter`: invalidates all entries during initialization.
- `SnoopRequest`: sends snoop requests to selected upstream cores.
- `SnoopResponse`: collects snoop responses and optional returned data.
- `VictimAvailability`: finds clean or invalid ways that can be replaced without writeback.
- `VictimSelection`: tracks the fallback replacement way.

The bank state machine starts in `init`, runs reset, then moves to `idle`. In `idle`, write-address requests currently have priority over read-address requests. Accepted requests issue a data-array lookup and then move to response analysis. Miss, refill, snoop, victim selection, and writeback paths are partially scaffolded and still contain TODOs.

### `DataArray`

`DataArray` is the SRAM-backed set/way storage for one bank. Each entry contains:

- tag
- valid bit
- dirty bit
- unique bit
- sharer mask
- cache-line data

The array uses a read/write SRAM port. Stage 0 accepts a request and indexes the SRAM. Stage 1 compares tags, reports hit status, returns the matching way index, and exposes the full set contents for victim logic.

### `Reseter`

`Reseter` walks all cache entries at startup and writes invalid entries into every way. It also clears the victim selector. Its outputs are shaped as a data-array write request plus a victim command.

### `SnoopRequest`

`SnoopRequest` sends coherent snoop requests to selected cores. A command provides:

- target address
- target core mask
- whether the request invalidates

For each selected core, the module drives `creq` until the request fires. It tracks which targets have been sent and reports busy/done status.

### `SnoopResponse`

`SnoopResponse` waits for responses from a target core mask. It records:

- which cores responded
- which responses require data
- which data beats have arrived
- which responses reported dirty data
- the returned data, if any

Completion requires all selected cores to respond and all expected data transfers to arrive.

### `VictimAvailability`

`VictimAvailability` examines all ways in a looked-up set and reports ways that are either invalid or clean. These ways can be selected without first writing dirty data downstream.

### `VictimSelection`

`VictimSelection` is the fallback replacement pointer. It clears during reset and increments when commanded, wrapping around the number of ways.

### `addressUtils`

`addressUtils` contains cache address decode helpers:

- `getCacheEntryIdx(addr)`: extracts the set index.
- `getCacheTag(addr)`: extracts the cache tag.

These helpers operate on the already-visible address width of the module using them. For banked operation, `Multibanker` narrows addresses before they reach each `Bank`, so the bank-local tag and index are derived from the bank-local address.

## Request Flow

1. A core sends an upstream coherent read, write, or snoop request.
2. `Multibanker` selects a bank from cache-line address bits.
3. `Multibanker` removes the bank-select bits and forwards the narrowed request to the selected `Bank`.
4. The selected bank arbitrates between cores and starts a `DataArray` lookup.
5. On the next cycle, `DataArray` reports hit/miss, way metadata, sharer state, and dirty/unique state.
6. The bank state machine decides whether to respond, snoop upstream caches, choose a victim, write back dirty data, or refill from downstream memory.

## Current Status

The banked routing and most helper submodules are present. Several bank state-machine paths are still incomplete, especially full miss handling, refill, snoop-driven storage updates, and voluntary eviction. The downstream interface of `Multibanker` intentionally remains per-bank so a parent module can choose the desired arbitration or interconnect topology.


