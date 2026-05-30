package armleocpu.utils

import chisel3._


class threeStateStageIO extends activeDone {
  val start = Input(Bool())
}

class activeDone extends Bundle {
  val active = Output(Bool())
  val done = Output(Bool())
}