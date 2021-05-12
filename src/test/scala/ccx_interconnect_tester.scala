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

  def expect_mbus_noop() {
    expect(c.io.mbus.aw.valid, 0)
    expect(c.io.mbus.ar.valid, 0)
    expect(c.io.mbus.w.valid, 0)
  }

  def poke_core_aw_req_WriteNoSnoop(i: Int, addr: BigInt) {
    poke(c.io.corebus(i).aw.bits.addr, addr)
    poke(c.io.corebus(i).aw.bits.size, 2) // (log2 of 4 == 2)
    poke(c.io.corebus(i).aw.bits.len, 1) // Interconnect does not care
    poke(c.io.corebus(i).aw.bits.burst, 1) // Interconnect does not care
    poke(c.io.corebus(i).aw.bits.id, 1) // Interconnect does not care
    poke(c.io.corebus(i).aw.bits.lock, 0) // Not atomic. Set to zero
    poke(c.io.corebus(i).aw.bits.cache, 15)
    poke(c.io.corebus(i).aw.bits.prot, 1) // Interconnect does not care
    poke(c.io.corebus(i).aw.bits.qos, 1) // Interconnect does not care
    
    poke(c.io.corebus(i).aw.bits.bar, 0) // Not barrier
    poke(c.io.corebus(i).aw.bits.domain, 0) // Should be either b00 or b11 for no snoop
    poke(c.io.corebus(i).aw.bits.snoop, 0) // WriteNoSnoop

    poke(c.io.corebus(i).aw.valid, 1)
  }

  def expect_core_aw_accept(i: Int) {
    expect(c.io.corebus(i).aw.ready, 1)
  }
  def expect_core_aw_not_accept(i: Int) {
    expect(c.io.corebus(i).aw.ready, 0)
  }

  def expect_core_ac_req_noop(i: Int) {
    expect(c.io.corebus(i).ac.valid, 0)
  }

  def expect_core_ac_req_CleanInvalid(i: Int, addr: BigInt) {
    expect(c.io.corebus(i).ac.valid, 1)
    expect(c.io.corebus(i).ac.bits.addr, addr)
    expect(c.io.corebus(i).ac.bits.snoop, BigInt("1001", 2))
    expect(c.io.corebus(i).ac.bits.prot, 1)
  }

  def poke_mbus_aw_not_accept() {
    poke(c.io.mbus.aw.ready, 0)
  }

  def expect_mbus_aw_write(i: Int, addr: BigInt) {
    expect(c.io.mbus.aw.bits.addr, addr)
    expect(c.io.mbus.aw.bits.size, 2) // (log2 of 4 == 2)
    expect(c.io.mbus.aw.bits.len, 1) // Interconnect does not care
    expect(c.io.mbus.aw.bits.burst, 1) // Interconnect does not care
    expect(c.io.mbus.aw.bits.id, (i << 1) | 1) // Interconnect does not care
    expect(c.io.mbus.aw.bits.lock, 0) // Not atomic. Set to zero
    expect(c.io.mbus.aw.bits.cache, 15)
    expect(c.io.mbus.aw.bits.prot, 1) // Interconnect does not care
    expect(c.io.mbus.aw.bits.qos, 1) // Interconnect does not care

    expect(c.io.mbus.aw.valid, 1)
    
  }

  def test_write_no_snoop() {
    poke_core_aw_req_WriteNoSnoop(0, 100)
    expect_core_aw_not_accept(0)
    expect_mbus_noop()
    step(1)

    expect_mbus_aw_write(0, 100)
    poke_mbus_aw_not_accept()
    expect_core_aw_not_accept(0)
    step(1)
  }

  def test_write_WriteUnique() {
  /*
    expect_core_ac_req_noop(0)
    expect_core_ac_req_CleanInvalid(1, 100)
    expect_core_ac_req_CleanInvalid(2, 100)
    step(1)*/
  }

  println("Testing WriteNoSnoop")
  test_write_no_snoop()

  /*
  poke_core_aw_req()
  expect_core_aw_accept()
  expect_mbus_noop()
  step(1)

  // For all cores:
  expect_core_ac_req()
  poke_core_ac_stall()
  expect_mbus_noop()
  step(1)

  // For one core
  expect_core_ac_req()
  poke_core_ac_resp()
  expect_mbus_noop()
  step(1)


  // For two cores
  expect_core_ac_req()
  poke_core_ac_resp()
  expect_mbus_noop()
  step(1)

  // For one core return response for snoop
  poke_core_cr_resp()
  expect_core_cr_accept()
  // For all cores stall SNOOP ADDRESS
  poke_core_ac_stall()
  expect_core_ac_invalid()
  expect_mbus_noop()
  step(1)

  // For 
  */
  


  /*
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
  */
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