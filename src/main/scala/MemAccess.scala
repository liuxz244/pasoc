package PaSoC

import chisel3._       // chisel本体
import chisel3.util._  // chisel功能
import Consts._

object MemAccess {
	def getCSR_wdata(csr_cmd:UInt, csr_rdata:UInt, op1_data:UInt): UInt = {
		val csr_wdata = MuxCase(0.U(WORD_LEN.W),Seq(
      (csr_cmd === CSR_W) -> op1_data,
      (csr_cmd === CSR_S) -> (csr_rdata | op1_data),
      (csr_cmd === CSR_C) -> (csr_rdata & ~op1_data),
      (csr_cmd === CSR_E) -> 11.U(WORD_LEN.W)  // 机器模式下的ECALl
    ))
		csr_wdata
	}

  def memRegwb(pc:UInt, inst: UInt, rd_addr:UInt, rf_wen:UInt, rd_data:UInt, wb_reg:wbRegBUndle) = {
    wb_reg.pc      := pc
    wb_reg.inst    := inst
    wb_reg.rd_addr := rd_addr
    wb_reg.rf_wen  := rf_wen
    wb_reg.rd_data := rd_data
  }
}