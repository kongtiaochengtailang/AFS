import spinal.core._
import spinal.lib._
import spinal.lib.pipeline._

object Olive {
  case class Config(
                    bitWidth: Int // operation width
                   ) {
    def genSDCtx = Bits(bitWidth bits)

    def genPairCtx = Bits(bitWidth * 2 bits) // outlier-victim pair

    def genOpCtx = Bits(bitWidth * 2 bits) // e.g. E4M3

    def genRspCtx = Bits(bitWidth * 4 bits) // e.g. E8M7
  }
}

/*
* Transforms outliers of bitWidth width to E(BW)M(BW-1)
* */
class OutlierDecoder(cfg: Olive.Config) extends Component {
  val io = new Bundle {
    val req = slave Stream(cfg.genSDCtx)
    val rsp = master Stream(cfg.genPairCtx) // Exp-Int
  }


}

class OVPDecoder(cfg: Olive.Config) extends Component {
  val io = new Bundle {
    val req = slave Stream(cfg.genPairCtx)
    val rsp = master Stream(Vec(cfg.genPairCtx, 2)) // value 1 ## value 0
  }

  val value0 = io.req.payload(3 downto 0)
  val value1 = io.req.payload(7 downto 4)
  val isOutlier = Vec(value0 === B"1000", value1 === B"1000")



}

class Olive(cfg: Olive.Config) extends Component {
  val io = new Bundle {
    val act = slave Stream(cfg.genOpCtx) // FIX
    val wgt = slave Stream(cfg.genPairCtx) // pair
    val rsp = master Stream(cfg.genRspCtx) // FIX
  }

  val decoder = new OVPDecoder(cfg)

}
