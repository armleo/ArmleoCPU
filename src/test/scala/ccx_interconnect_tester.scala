package armleocpu

import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}



import Consts._
import CacheConsts._


class CCXInterconnectUnitTester(c: CCXInterconnect, n: Int) extends PeekPokeTester(c) {
  //Init:
  for(i <- 0 until n) {
    poke(c.io.corebus(i).wack, 0)
    poke(c.io.corebus(i).rack, 0)

    poke(c.io.corebus(i).aw.valid, 0)
    poke(c.io.corebus(i).ar.valid, 0)
    poke(c.io.corebus(i).w.valid, 0)
    poke(c.io.corebus(i).cr.valid, 0)
    poke(c.io.corebus(i).cd.valid, 0)

    poke(c.io.corebus(i).b.ready, 0)
    poke(c.io.corebus(i).r.ready, 0)
    poke(c.io.corebus(i).ac.ready, 0)


    poke(c.io.mbus.w.ready, 0)
    poke(c.io.mbus.aw.ready, 0)
    poke(c.io.mbus.ar.ready, 0)

    poke(c.io.mbus.b.valid, 0)
    poke(c.io.mbus.r.valid, 0)
  }
  
  // Send no snoop request
  poke(c.io.corebus(0).aw.bits.addr, 100)
  poke(c.io.corebus(0).aw.bits.bar, 0)
  poke(c.io.corebus(0).aw.bits.domain, 0)
  poke(c.io.corebus(0).aw.bits.snoop, 0)
  poke(c.io.corebus(0).aw.bits.id, 1)
  poke(c.io.corebus(0).aw.valid, 1)

  expect(c.io.corebus(0).aw.ready, 1)
  expect(c.io.mbus.w.valid, 0)
  step(1)
  
  // AW request sent, expect it to NOT apear at mbus because it is only registered in memory
  poke(c.io.corebus(0).aw.valid, 0)
  expect(c.io.mbus.aw.valid, 1)
  expect(c.io.mbus.aw.bits.addr, 100)
  expect(c.io.mbus.aw.bits.id, 1)
  expect(c.io.mbus.w.valid, 0)
  step(1)


  //  AW request accepted by MBUS, expect 
  poke(c.io.mbus.aw.ready, 1)
  expect(c.io.mbus.aw.bits.addr, 100)
  expect(c.io.mbus.aw.bits.id, 1)
  expect(c.io.mbus.w.valid, 0)
  step(1)

  // AW is not ready to accept new requests
  poke(c.io.mbus.aw.ready, 0)

  // Send write data
  poke(c.io.corebus(0).w.valid, 1)
  poke(c.io.corebus(0).w.bits.data, BigInt("FFFFFFFF", 16))
  poke(c.io.corebus(0).w.bits.strb, BigInt("F", 16))
  poke(c.io.corebus(0).w.bits.last, 0)
  // Stall MBUS W channel
  poke(c.io.mbus.w.ready, 0)
  // Expect corebus to be stalled but request is available at mbus
  expect(c.io.corebus(0).w.ready, 0)
  expect(c.io.mbus.w.valid, 1)
  // mbus request passed thru
  expect(c.io.mbus.w.valid, 1)
  expect(c.io.mbus.w.bits.data, BigInt("FFFFFFFF", 16))
  expect(c.io.mbus.w.bits.strb, BigInt("F", 16))
  expect(c.io.mbus.w.bits.last, 0)
  step(1)
  
  // mbus request passed thru and ready passed thru towards corebus, not last
  expect(c.io.mbus.w.valid, 1)
  expect(c.io.mbus.w.bits.data, BigInt("FFFFFFFF", 16))
  expect(c.io.mbus.w.bits.strb, BigInt("F", 16))
  expect(c.io.mbus.w.bits.last, 0)
  
  // mbus transaction is processed
  poke(c.io.mbus.w.ready, 1)
  expect(c.io.corebus(0).w.ready, 1) // Expect w done to be signaled to cache
  step(1)


  // Start next write request
  // corebus write request last, ready
  poke(c.io.corebus(0).w.valid, 1)
  poke(c.io.corebus(0).w.bits.data, BigInt("FFFFFFFE", 16))
  poke(c.io.corebus(0).w.bits.strb, BigInt("F", 16))
  poke(c.io.corebus(0).w.bits.last, 1)
  poke(c.io.mbus.w.ready, 1)

  // Expect cache to be signaled to process to next
  expect(c.io.corebus(0).w.ready, 1)
  // Expect data to be transfered
  expect(c.io.mbus.w.valid, 1)
  expect(c.io.mbus.w.bits.data, BigInt("FFFFFFFE", 16))
  expect(c.io.mbus.w.bits.strb, BigInt("F", 16))
  expect(c.io.mbus.w.bits.last, 1)
  step(1)


  // corebus invalid, mbus invalid, no data actions
  poke(c.io.corebus(0).w.valid, 0)
  poke(c.io.corebus(0).w.bits.data, BigInt("FFFFFFFF", 16))
  poke(c.io.corebus(0).w.bits.strb, BigInt("F", 16))
  poke(c.io.corebus(0).w.bits.last, 0)


  expect(c.io.mbus.w.valid, 0)

  //expect(c.io.corebus(0).ac.valid, 0)
  
  // TODO: Add write response (b channel) test

  step(5)
}

class CCXInterconnectTester extends ChiselFlatSpec {
  "CCXInterconnect" should s"work very good (with firrtl)" in {
    Driver.execute(Array("--full-stacktrace", "--generate-vcd-output", "on", "--backend-name", "firrtl", "--target-dir", "test_run_dir/ccx_interconnect_test", "--top-name", "armleocpu_ccx_interconnect"),
        () => new CCXInterconnect(n = 3)) {
      c => new CCXInterconnectUnitTester(c, 3)
    } should be (true)
  }
}


class RoundRobinUnitTester(c: RoundRobin, n: Int) extends PeekPokeTester(c) {
    poke(c.io.req(0), 0)
    poke(c.io.req(1), 0)
    poke(c.io.req(2), 1)
    poke(c.io.next, 1)
    expect(c.io.grant(0), 0)
    expect(c.io.grant(1), 0)
    expect(c.io.grant(2), 1)
    expect(c.io.choice, 2)
    step(1)

    poke(c.io.req(0), 0)
    poke(c.io.req(1), 1)
    poke(c.io.req(2), 1)
    poke(c.io.next, 1)
    expect(c.io.grant(0), 0)
    expect(c.io.grant(1), 1)
    expect(c.io.grant(2), 0)
    expect(c.io.choice, 1)
    step(1)

    poke(c.io.req(0), 0)
    poke(c.io.req(1), 1)
    poke(c.io.req(2), 1)
    poke(c.io.next, 1)
    expect(c.io.grant(0), 0)
    expect(c.io.grant(1), 0)
    expect(c.io.grant(2), 1)
    expect(c.io.choice, 2)
    step(1)

    poke(c.io.req(0), 1)
    poke(c.io.req(1), 1)
    poke(c.io.req(2), 1)
    poke(c.io.next, 1)
    expect(c.io.grant(0), 1)
    expect(c.io.grant(1), 0)
    expect(c.io.grant(2), 0)
    expect(c.io.choice, 0)
    step(1)

    poke(c.io.req(0), 1)
    poke(c.io.req(1), 1)
    poke(c.io.req(2), 1)
    poke(c.io.next, 0)
    expect(c.io.grant(0), 0)
    expect(c.io.grant(1), 1)
    expect(c.io.grant(2), 0)
    expect(c.io.choice, 1)
    step(1)

    poke(c.io.req(0), 1)
    poke(c.io.req(1), 1)
    poke(c.io.req(2), 1)
    poke(c.io.next, 0)
    expect(c.io.grant(0), 0)
    expect(c.io.grant(1), 1)
    expect(c.io.grant(2), 0)
    expect(c.io.choice, 1)
    step(1)

}

class RoundRobinTester extends ChiselFlatSpec {
  "RoundRobin" should s"work very good (with firrtl)" in {
    Driver.execute(Array("--full-stacktrace", "--generate-vcd-output", "on", "--backend-name", "verilator", "--target-dir", "test_run_dir/ccx_interconnect_test", "--top-name", "armleocpu_ccx_interconnect"),
        () => new RoundRobin(n = 3)) {
      c => new RoundRobinUnitTester(c, 3)
    } should be (true)
  }
}