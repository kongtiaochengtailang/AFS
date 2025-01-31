import spinal.core._

object RTLGen extends App {
  val genCfg = SpinalConfig(targetDirectory = "rtl/", removePruned = true)

  genCfg.generateVerilog(Olive(4))

  genCfg.generateVerilog(OutlierZeroizer(8))
}
