import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import scala.math._


object OutlierZeroizer {
  case class Config(
                    opWidth: Int
                   ) {
    def genReqCtx = SInt(opWidth bits)

    def genRspCtx = FlaggedRsp(opWidth)

    def genOLEncoding = -pow(2, opWidth-1).toInt
  }

  def apply(opWidth: Int) = new OutlierZeroizer(OutlierZeroizer.Config(8))

  case class FlaggedRsp(opWidth: Int) extends Bundle {
    val product = SInt(opWidth * 2 bits)
    val flag = Bool()
  }
}

class OutlierZeroizer(cfg: OutlierZeroizer.Config) extends Component {
  val io = new Bundle {
    val req = slave Stream(cfg.genReqCtx)
    val wgt = slave Stream(cfg.genReqCtx)
    val rsp = master Stream(cfg.genRspCtx)
  }

  val inner = Stream(cfg.genRspCtx)
  val en = io.req.valid && io.wgt.valid && inner.ready
  val flag = RegNextWhen(io.wgt.payload === cfg.genOLEncoding, en)
  val product = RegNextWhen((io.req.payload * io.wgt.payload), en)

  inner.valid := en
  io.req.ready := en
  io.wgt.ready := en
  io.rsp << inner.stage().translateInto(Stream(cfg.genRspCtx)){ (to, _) =>
    to.product := product
    to.flag := flag
  }

}
