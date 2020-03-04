package armleocpu


import chisel3._
import chisel3.util._

trait CatUtil {
    def Cat(l: Seq[Bits]): UInt = (l.tail foldLeft l.head.asUInt){(x, y) =>
        assert(x.isLit() && y.isLit())
        (x.litValue() << y.getWidth | y.litValue()).U((x.getWidth + y.getWidth).W)
    }
    def Cat(x: Bits, l: Bits*): UInt = Cat(x :: l.toList)
}