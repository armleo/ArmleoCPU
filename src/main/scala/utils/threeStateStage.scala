package armleocpu.utils

import chisel3._


class threeStateStageIO extends Bundle {
  val start = Input(Bool())
  val active = Output(Bool())
  val done = Output(Bool())
}