package armleocpu

import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}



import Consts._
import CacheConsts._


class CCXInterconnectUnitTester(c: CCXInterconnect, n: Int) extends PeekPokeTester(c) {
  //Init:
  def poke_all(
    i: Int,
    aw:Boolean = true,
    w:Boolean = true,
    b:Boolean = true,
    ar:Boolean = true,
    r:Boolean = true,
    ac:Boolean = true,
    cr:Boolean = true,
    cd:Boolean = true,
    mbus_aw:Boolean = true,
    mbus_w:Boolean = true,
    mbus_b:Boolean = true,
    mbus_ar:Boolean = true,
    mbus_r:Boolean = true,
    rack:Boolean = true,
    wack:Boolean = true
  ) {
    if(rack) {poke_core_rack(i, 0)}
    if(wack) {poke_core_wack(i, 0)}

    if(aw)   {poke_core_aw(i, 0)}
    if(w)    {poke_core_w(i, 0)}
    if(b)    {poke_core_b(i, 0)}

    if(ar)   {poke_core_ar(i, 0)}
    if(r)    {poke_core_r(i, 0)}

    if(ac)   {poke_core_ac(i, 0)}
    if(cd)   {poke_core_cd(i, 0)}
    if(cr)   {poke_core_cr(i, 0)}
    
    if(mbus_aw)  {poke_mbus_aw()}
    if(mbus_w)   {poke_mbus_w()}
    if(mbus_b)   {poke_mbus_b()}

    if(mbus_ar)  {poke_mbus_ar()}
    if(mbus_r)   {poke_mbus_r()}
  }
  for(i <- 0 until n) {
    poke_all(i)
    expect_all(i)
  }

  // ------ MBUS ADDRESS WRITE ----------
  def expect_mbus_aw(
    valid: Int,
    addr: BigInt = 0,
    size: BigInt = BigInt("011", 2),
    len: Int = 0,
    burst: Int = 0,
    id: Int = 0,
    lock: Int = 0,
    cache: Int = 15,
    prot: Int = 1,
    qos: Int = 1
    ) {
      if(valid > 0) {
        expect(c.io.mbus.aw.bits.addr, addr)
        expect(c.io.mbus.aw.bits.size, size)
        expect(c.io.mbus.aw.bits.len, len) // Interconnect does not care
        expect(c.io.mbus.aw.bits.burst, burst) // Interconnect does not care
        expect(c.io.mbus.aw.bits.id, id) // Interconnect does not care
        expect(c.io.mbus.aw.bits.lock, lock)
        expect(c.io.mbus.aw.bits.cache, cache) // Interconnect does not care
        expect(c.io.mbus.aw.bits.prot, prot) // Interconnect does not care
        expect(c.io.mbus.aw.bits.qos, qos) // Interconnect does not care

        expect(c.io.mbus.aw.valid, 1)
      } else {
        expect(c.io.mbus.aw.valid, 0)
      }
  }


  def poke_mbus_aw(ready: Int = 0) {
    poke(c.io.mbus.aw.ready, ready)
  }

  // ------ MBUS ADDRESS READ ----------
  def expect_mbus_ar(
    valid: Int,
    addr: BigInt = 0,
    size: BigInt = BigInt("011", 2),
    len: Int = 0,
    burst: Int = 0,
    id: Int = 0,
    lock: Int = 0,
    cache: Int = 15,
    prot: Int = 1,
    qos: Int = 1
    ) {
      if(valid > 0) {
        expect(c.io.mbus.ar.bits.addr, addr)
        expect(c.io.mbus.ar.bits.size, size)
        expect(c.io.mbus.ar.bits.len, len) // Interconnect does not care
        expect(c.io.mbus.ar.bits.burst, burst) // Interconnect does not care
        expect(c.io.mbus.ar.bits.id, id) // Interconnect does not care
        expect(c.io.mbus.ar.bits.lock, lock)
        expect(c.io.mbus.ar.bits.cache, cache) // Interconnect does not care
        expect(c.io.mbus.ar.bits.prot, prot) // Interconnect does not care
        expect(c.io.mbus.ar.bits.qos, qos) // Interconnect does not care

        expect(c.io.mbus.ar.valid, 1)
      } else {
        expect(c.io.mbus.ar.valid, 0)
      }
  }


  def poke_mbus_ar(ready: Int = 0) {
    poke(c.io.mbus.ar.ready, ready)
  }

  // ------ MBUS W ----------
  def poke_mbus_w(ready: Int = 0) {
    poke(c.io.mbus.w.ready, ready)
  }

  def expect_mbus_w(valid: Int,
  data: BigInt = 0,
  strb: BigInt = BigInt("FF", 16),
  last: Int = 0) {
    // I is ignored
    if(valid > 0) {
      expect(c.io.mbus.w.bits.data, data)
      expect(c.io.mbus.w.bits.strb, strb)
      expect(c.io.mbus.w.bits.last, last)
    }
    
    expect(c.io.mbus.w.valid, valid)
  }

  // ------ MBUS B ----------
  def poke_mbus_b(valid: Int = 0, id: Int = 0, resp: Int = 0) {
    if(valid > 0) {
      poke(c.io.mbus.b.bits.id, id)
      poke(c.io.mbus.b.bits.resp, resp)
    }
    poke(c.io.mbus.b.valid, valid)
  }

  def expect_mbus_b(ready: Int) {
    expect(c.io.mbus.b.ready, ready)
  }

  // ------ MBUS R ----------
  def poke_mbus_r(valid: Int = 0, data: BigInt = 0, id: Int = 0, resp: Int = 0, last: Int = 0) {
    if(valid > 0) {
      poke(c.io.mbus.r.bits.id, id)
      poke(c.io.mbus.r.bits.last, resp)
      poke(c.io.mbus.r.bits.resp, resp)
      poke(c.io.mbus.r.bits.data, data)
    }
    poke(c.io.mbus.r.valid, valid)
  }

  def expect_mbus_r(ready: Int) {
    expect(c.io.mbus.r.ready, ready)
  }
  


  // ------ CORE AW ----------
  def poke_core_aw(
    i: Int,
    valid: Int,
    addr: BigInt = 0,
    size: BigInt = BigInt("011", 2),
    len: Int = 0,
    burst: Int = 0,
    id: Int = 0,
    lock: Int = 0,
    cache: Int = 15,
    prot: Int = 1,
    qos: Int = 1,
    bar: Int = 0,
    domain: BigInt = 0,
    snoop: BigInt = 0
    ) {
    poke(c.io.corebus(i).aw.bits.addr, addr)
    poke(c.io.corebus(i).aw.bits.size, size) // (log2 of 4 == 2)
    poke(c.io.corebus(i).aw.bits.len, len) // Interconnect does not care
    poke(c.io.corebus(i).aw.bits.burst, burst) // Interconnect does not care
    poke(c.io.corebus(i).aw.bits.id, id) // Interconnect does not care
    poke(c.io.corebus(i).aw.bits.lock, lock) // Not atomic. Set to zero
    poke(c.io.corebus(i).aw.bits.cache, cache)
    poke(c.io.corebus(i).aw.bits.prot, prot) // Interconnect does not care
    poke(c.io.corebus(i).aw.bits.qos, qos) // Interconnect does not care
    
    poke(c.io.corebus(i).aw.bits.bar, bar) // Not barrier
    poke(c.io.corebus(i).aw.bits.domain, domain) // Should be either b00 or b11 for no snoop
    poke(c.io.corebus(i).aw.bits.snoop, snoop) // WriteNoSnoop

    poke(c.io.corebus(i).aw.valid, valid)
  }
  def expect_core_aw(i: Int, ready: Int) {
    expect(c.io.corebus(i).aw.ready, ready)
  }

  // ------ CORE AR ----------
  def poke_core_ar(
    i: Int,
    valid: Int,
    addr: BigInt = 0,
    size: BigInt = BigInt("011", 2),
    len: Int = 0,
    burst: Int = 0,
    id: Int = 0,
    lock: Int = 0,
    cache: Int = 15,
    prot: Int = 1,
    qos: Int = 1,
    bar: Int = 0,
    domain: BigInt = 0,
    snoop: BigInt = 0
    ) {
    poke(c.io.corebus(i).ar.bits.addr, addr)
    poke(c.io.corebus(i).ar.bits.size, size) // (log2 of 4 == 2)
    poke(c.io.corebus(i).ar.bits.len, len) // Interconnect does not care
    poke(c.io.corebus(i).ar.bits.burst, burst) // Interconnect does not care
    poke(c.io.corebus(i).ar.bits.id, id) // Interconnect does not care
    poke(c.io.corebus(i).ar.bits.lock, lock) // Not atomic. Set to zero
    poke(c.io.corebus(i).ar.bits.cache, cache)
    poke(c.io.corebus(i).ar.bits.prot, prot) // Interconnect does not care
    poke(c.io.corebus(i).ar.bits.qos, qos) // Interconnect does not care
    
    poke(c.io.corebus(i).ar.bits.bar, bar) // Not barrier
    poke(c.io.corebus(i).ar.bits.domain, domain) // Should be either b00 or b11 for no snoop
    poke(c.io.corebus(i).ar.bits.snoop, snoop) // WriteNoSnoop

    poke(c.io.corebus(i).ar.valid, valid)
  }
  
  def expect_core_ar(i: Int, ready: Int) {
    expect(c.io.corebus(i).ar.ready, ready)
  }

  // ------ CORE W ----------
  def poke_core_w(i:Int, valid: Int, data: BigInt = 0, strb: BigInt = BigInt("FF", 16), last: Int = 0) {
    poke(c.io.corebus(i).w.bits.data, data)
    poke(c.io.corebus(i).w.bits.strb, strb)
    poke(c.io.corebus(i).w.bits.last, last)

    poke(c.io.corebus(i).w.valid, valid)
  }

  def expect_core_w(i:Int, ready: Int) {
    expect(c.io.corebus(i).w.ready, ready)
  }

  
  // ------ CORE B ----------
  def poke_core_b(i: Int, ready: Int) {
    poke(c.io.corebus(i).b.ready, ready)
  }

  def expect_core_b(
    i: Int,
    valid: Int,
    id: Int = 0,
    resp: Int = 0,
  ) {
    if(valid > 0) {
      expect(c.io.corebus(i).b.bits.id, id)
      expect(c.io.corebus(i).b.bits.resp, resp)
    }
    expect(c.io.corebus(i).b.valid, valid)
  }

  // ------ CORE R ----------
  def poke_core_r(i: Int, ready: Int) {
    poke(c.io.corebus(i).r.ready, ready)
  }

  def expect_core_r(
    i: Int,
    valid: Int = 0,
    id: Int = 0,
    resp: Int = 0,
    data: BigInt = 0,
    last: Int = 0
  ) {
    if(valid > 0) {
      expect(c.io.corebus(i).r.bits.id, id)
      expect(c.io.corebus(i).r.bits.resp, resp)
      expect(c.io.corebus(i).r.bits.data, data)
      expect(c.io.corebus(i).r.bits.last, last)
    }
    expect(c.io.corebus(i).r.valid, valid)
  }
  // ------ CORE AC ----------
  def expect_core_ac(
    i: Int,
    valid: Int,
    addr: BigInt = 0,
    snoop: BigInt = 0,
    prot: Int = 0) {
    expect(c.io.corebus(i).ac.valid, valid)
    if(valid > 0) {
      expect(c.io.corebus(i).ac.bits.addr, addr)
      expect(c.io.corebus(i).ac.bits.snoop, snoop)
      expect(c.io.corebus(i).ac.bits.prot, prot)
    }
  }

  def poke_core_ac(i: Int, ready: Int) {
    poke(c.io.corebus(i).ac.ready, ready)
  }
  // ------ CORE CR ----------
  def poke_core_cr(i: Int,
    valid: Int,
    resp: BigInt = 0
    ) {
    poke(c.io.corebus(i).cr.valid, valid)
    poke(c.io.corebus(i).cr.bits.resp, resp)
  }
  def expect_core_cr(i: Int, ready: Int = 0) {
    expect(c.io.corebus(i).cr.ready, ready)
  }
  // ------ CORE CD ----------
  def poke_core_cd(i: Int,
    valid: Int,
    data: BigInt = 0,
    last: Int = 0
    ) {
    poke(c.io.corebus(i).cd.valid, valid)
    poke(c.io.corebus(i).cd.bits.data, data)
    poke(c.io.corebus(i).cd.bits.last, last)
  }

  def expect_core_cd(
    i: Int,
    ready: Int
  ) {
    expect(c.io.corebus(i).cd.ready, ready)
  }

  // ------ CORE WACK/RACK ----------
  def poke_core_wack(i: Int, wack: Int) {
    poke(c.io.corebus(i).wack, wack)
  }

  def poke_core_rack(i: Int, rack: Int) {
    poke(c.io.corebus(i).rack, rack)
  }
  

  def expect_all(
    i: Int,
    aw:Boolean = true,
    w:Boolean = true,
    b:Boolean = true,
    ar:Boolean = true,
    r:Boolean = true,
    ac:Boolean = true,
    cr:Boolean = true,
    cd:Boolean = true,
    mbus_aw:Boolean = true,
    mbus_w:Boolean = true,
    mbus_b:Boolean = true,
    mbus_ar:Boolean = true,
    mbus_r:Boolean = true
  ) {
    if(aw) {expect_core_aw(i, 0)}
    if(w)  {expect_core_w(i, 0)}
    if(b)  {expect_core_b(i, 0)}

    if(ar) {expect_core_ar(i, 0)}
    if(r)  {expect_core_r(i, 0)}

    if(ac)  {expect_core_ac(i, 0)}
    if(cr)  {expect_core_cr(i, 0)}
    if(cd)  {expect_core_cd(i, 0)}


    if(mbus_aw) {expect_mbus_aw(0)}


    if(mbus_w)  {expect_mbus_w(0)}
    if(mbus_b)  {expect_mbus_b(0)}

    if(mbus_ar) {expect_mbus_ar(0)}
    if(mbus_r)  {expect_mbus_r(0)}
  }


  def test_WriteNoSnoop(c: Int) {
    
    // Start write transaction
    for(i <- 0 until n) {
      if(c == i)
        poke_all(i, aw = false)
      else
        poke_all(i)
    }
    poke_core_aw(c, 1, 
      addr = 100,
    )
    for(i <- 0 until n) {
      expect_all(i)
      
    }
    step(1)

    // Arbitrated
    expect_mbus_aw(
      valid = 1,
      addr = 100,
      id = c << 1
      )
    for(i <- 0 until n) {
      expect_all(i, mbus_aw = false)
    }
    step(1)

    // AW stall cycle
    for(i <- 0 until n) {
      if(c == i)
        poke_all(i, aw = false)
      else
        poke_all(i)
    }
    poke_mbus_aw(0)
    expect_mbus_aw(
      valid = 1,
      addr = 100,
      id = c << 1
      )
    expect_core_aw(c, 0)
    for(i <- 0 until n) {
      if(c == i)
        expect_all(i, mbus_aw = false, aw = false)
      else
        expect_all(i, mbus_aw = false)
    }
    step(1)

    // AW accepted
    poke_mbus_aw(1)
    expect_mbus_aw(
      valid = 1,
      addr = 100,
      id = c << 1
      )
    expect_core_aw(c, 1)
    for(i <- 0 until n) {
      if(c == i)
        expect_all(i, mbus_aw = false, aw = false)
      else
        expect_all(i, mbus_aw = false)
    }
    step(1)

    // W stalled
    poke_core_w(c,
      valid = 1, 
      data = BigInt("1234123412341234", 16),
      strb = BigInt("FF", 16),
      last = 0)
    poke_mbus_w(ready = 0)
    expect_core_w(c, ready = 0)
    for(i <- 0 until n) {
      if(c == i)
        expect_all(i, mbus_w = false, w = false)
      else
        expect_all(i, mbus_w = false)
    }
    expect_mbus_w(
      valid = 1,
      data = BigInt("1234123412341234", 16),
      strb = BigInt("FF", 16),
      last = 0
      )
    step(1)

    // W accepted
    poke_core_w(c,
      valid = 1, 
      data = BigInt("1234123412341234", 16),
      strb = BigInt("FF", 16),
      last = 0)
    poke_mbus_w(ready = 1)
    expect_core_w(c, ready = 1)
    for(i <- 0 until n) {
      if(c == i)
        expect_all(i, mbus_w = false, w = false)
      else
        expect_all(i, mbus_w = false)
    }
    expect_mbus_w(
      valid = 1,
      data = BigInt("1234123412341234", 16),
      strb = BigInt("FF", 16),
      last = 0
      )
    step(1)

    // B accepted last
    poke_core_w(c,
      valid = 1, 
      data = BigInt("1234123412341234", 16),
      strb = BigInt("FF", 16),
      last = 1)
    poke_mbus_w(ready = 1)
    expect_core_w(c, ready = 1)
    for(i <- 0 until n) {
      if(c == i)
        expect_all(i, mbus_w = false, w = false)
      else
        expect_all(i, mbus_w = false)
    }
    expect_mbus_w(
      valid = 1,
      data = BigInt("1234123412341234", 16),
      strb = BigInt("FF", 16),
      last = 1
      )
    step(1)

    // B stalled
    poke_mbus_b(
      valid = 1,
      id = c << 1,
      resp = 0)
    poke_core_b(c, ready = 0)
    expect_mbus_b(ready = 0)
    for(i <- 0 until n) {
      if(c == i)
        expect_all(i, mbus_b = false, b = false)
      else
        expect_all(i, mbus_b = false)
    }
    expect_core_b(c,
      valid = 1,
      id = 0,
      resp = 0)
    step(1)

    // B accepted
    poke_mbus_b(
      valid = 1,
      id = c << 1,
      resp = 0)
    poke_core_b(c, ready = 1)
    expect_mbus_b(ready = 1)
    for(i <- 0 until n) {
      if(c == i)
        expect_all(i, mbus_b = false, b = false)
      else
        expect_all(i, mbus_b = false)
    }
    expect_core_b(c,
      valid = 1,
      id = 0,
      resp = 0)
    step(1)

    // WACK
    poke_core_wack(c, 1)
    for(i <- 0 until n) {
      expect_all(i)
    }
    step(1)
  }


  test_WriteNoSnoop(1)
  for(i <- 0 until n) {
    poke_all(i)
  }
  test_WriteNoSnoop(0)
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