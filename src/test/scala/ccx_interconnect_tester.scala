package armleocpu

import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}



import Consts._
import CacheConsts._


class CCXInterconnectUnitTester(c: CCXInterconnect, n: Int) extends PeekPokeTester(c) {
  poke(c.io.corebus(0).aw.bits.addr, 100)
  poke(c.io.corebus(0).aw.valid, 1)
  expect(c.io.corebus(0).aw.ready, 1)
  step(1)
  poke(c.io.corebus(0).aw.bits.addr, 101)
  poke(c.io.corebus(0).aw.valid, 1)
  expect(c.io.corebus(0).aw.ready, 0)

  step(1)
  expect(c.io.corebus(0).ac.valid, 0)

  step(1)
  expect(c.io.corebus(0).ac.valid, 0)
  

  step(5)
}

class CCXInterconnectTester extends ChiselFlatSpec {
  "CCXInterconnect" should s"work very good (with firrtl)" in {
    Driver.execute(Array("--full-stacktrace", "--generate-vcd-output", "on", "--backend-name", "verilator", "--target-dir", "test_run_dir/ccx_interconnect_test", "--top-name", "armleocpu_ccx_interconnect"),
        () => new CCXInterconnect(n = 3)) {
      c => new CCXInterconnectUnitTester(c, 2)
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