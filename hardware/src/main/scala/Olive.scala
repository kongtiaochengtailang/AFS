import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import spinal.lib.misc.plugin.FiberPlugin

import scala.math._

object Olive {
  case class Config(
                    opWidth: Int, // operation width, such as E4M3 -> opWidth = 4
                   ) {
    // abfloat
    val mantissaWidth = opWidth / 2 - 1 // 1 for sign

    def genEWgtCtx = Bits(opWidth * 2 bits) // encoded wgt

    def genOpCtx = Bits(opWidth bits)

    def genFXCtx = ExpInt(opWidth, opWidth)

    def genDWgtCtx = Vec(ExpInt(opWidth, opWidth), 2) // decoded wgt

    def genRspCtx = SInt((2 * opWidth + (pow(2, opWidth + 1).toInt - 1)) bits)
    // (integer * integer) << (exponent + exponent)
    // (2 * opWidth) << (opWidth + 1)
  }

  case class ExpInt(numOfE: Int, numOfI: Int) extends Bundle {
    val exponent = Bits(numOfE bits)
    val integer = Bits(numOfI bits)
  }

  def apply(opWidth: Int) = new Olive(Olive.Config(opWidth))
}

class OVPDecoder(cfg: Olive.Config) extends Component {
  import Olive._
  val io = new Bundle {
    val req = slave Stream(cfg.genEWgtCtx)
    val rsp = master Stream(cfg.genDWgtCtx)
  }

  val pip = new StagePipeline

  pip(0).arbitrateFrom(io.req)

  val identify = new pip.Area(0) {
    val value1 = io.req.payload(cfg.opWidth - 1 downto 0)
    val value2 = io.req.payload(cfg.opWidth * 2 - 1 downto cfg.opWidth)

    val OL_IN_VALUE1 = insert(value1 === S(-pow(2, cfg.opWidth-1).toInt).asBits)
    val OL_IN_VALUE2 = insert(value2 === S(-pow(2, cfg.opWidth-1).toInt).asBits)
    val ENCODED_OUTLIER = insert(Mux(OL_IN_VALUE1, value2, value1)) // abfloat encoding
    val VALUE1 = insert(value1)
    val VALUE2 = insert(value2)
  }

  val zeroCheck = new pip.Area(1) {
    // outlier decoding
    val isZeroReq = identify.ENCODED_OUTLIER(cfg.opWidth - 2 downto 0).orR // exclude the sign bit
    val exp = identify.ENCODED_OUTLIER(cfg.opWidth - 2 downto (cfg.opWidth / 2 - 1)).asBits
    val integer = B(1 << cfg.mantissaWidth, cfg.opWidth - 1 bits) ## identify.ENCODED_OUTLIER(0)

    val INTEGER = insert(Mux(isZeroReq, B(0, cfg.opWidth bits), integer))
    val EXPONENT = insert(exp.asUInt +^ U(2))
    val SIGN = insert(identify.ENCODED_OUTLIER(3))

    // normal decoding
    val PAD_NORMAL1 = insert(identify.VALUE1.resize(8))
    val PAD_NORMAL2 = insert(identify.VALUE2.resize(8))
  }

  val decode = new pip.Area(2) {
    // outlier decoding selection
    val EXPONENT = insert(zeroCheck.EXPONENT.resize(cfg.opWidth))
    val INTEGER = insert(Mux(zeroCheck.SIGN, (~zeroCheck.INTEGER).asUInt + 1, zeroCheck.INTEGER.asUInt))

    val OUTLIER = Payload(cfg.genFXCtx)

    OUTLIER.exponent := EXPONENT.asBits
    OUTLIER.integer := INTEGER.asBits

    // normal decoding selection
    val constant = cfg.genFXCtx
    val normal1 = cfg.genFXCtx
    val normal2 = cfg.genFXCtx

    constant.integer := 0
    constant.exponent := 0

    normal1.integer := zeroCheck.PAD_NORMAL1(cfg.opWidth-1 downto 0) // exponent ## integer
    normal1.exponent := 0
    normal2.integer := zeroCheck.PAD_NORMAL2(cfg.opWidth-1 downto 0)
    normal2.exponent := 0

    val NORMAL_VALUE1 = insert(Mux(identify.OL_IN_VALUE1, constant, normal1))
    val NORMAL_VALUE2 = insert(Mux(identify.OL_IN_VALUE2, constant, normal2))
  }

  val output = new pip.Area(3) {
    val outlier = cfg.genFXCtx
    val normal1 = cfg.genFXCtx
    val normal2 = cfg.genFXCtx

    outlier.exponent := decode.OUTLIER.exponent
    outlier.integer := decode.OUTLIER.integer

    normal1.exponent := decode.NORMAL_VALUE1.exponent
    normal1.integer := decode.NORMAL_VALUE1.integer

    normal2.exponent := decode.NORMAL_VALUE2.exponent
    normal2.integer := decode.NORMAL_VALUE2.integer

    val RSP0 = insert(Mux(identify.OL_IN_VALUE2, outlier, normal1))
    val RSP1 = insert(Mux(identify.OL_IN_VALUE1, outlier, normal2))
  }

  output.driveTo(io.rsp) { (payload, self) =>
    payload(0) := self(output.RSP0)
    payload(1) := self(output.RSP1)
  }

  pip.build()
}

class FPComputeUnit(cfg: Olive.Config) extends Component {
  import Olive._
  val io = new Bundle {
    val req0 = slave Stream(cfg.genFXCtx)
    val req1 = slave Stream(cfg.genFXCtx)
    val rsp = master Stream(cfg.genRspCtx)
  }

  val inner = Stream(cfg.genRspCtx)
  val en = io.req0.valid && io.req1.valid && inner.ready
  val expR = RegNextWhen(io.req0.exponent.asUInt +^ io.req1.exponent.asUInt, en)
  val productR = RegNextWhen((io.req0.integer.asSInt * io.req1.integer.asSInt), en) << expR

  io.req0.ready := en
  io.req1.ready := en
  inner.valid := en

  io.rsp << inner.stage().translateWith(productR)
}

class Olive(cfg: Olive.Config) extends Component {
  val io = new Bundle {
    val act = slave Stream(cfg.genFXCtx) // Floating-point
    val wgt = slave Stream(cfg.genEWgtCtx) // A pair of wgt encoding. For example, outlier-victim pair
    val rsp = master Stream(cfg.genRspCtx) // Floating-point
  }

  val decoder = new OVPDecoder(cfg)
  decoder.io.req << io.wgt
  val wgt = Stream(cfg.genFXCtx)
  StreamWidthAdapter(decoder.io.rsp, wgt)

  val cu = new FPComputeUnit(cfg)
  cu.io.req0 << io.act
  cu.io.req1 << wgt
  io.rsp << cu.io.rsp
}
