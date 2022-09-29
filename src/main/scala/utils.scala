package armleocpu


import scala.math._

object utils {
    def clog2(x: Int): Int = { require(x > 0); ceil(log(x)/log(2)).toInt }
}
