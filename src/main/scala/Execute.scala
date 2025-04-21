package PaSoC

import chisel3._       // chisel本体
import chisel3.util._  // chisel功能
import Consts._

object Execute {
	def ALU(exe_fnc:UInt, op1_data:UInt, op2_data:UInt): UInt = {
		val alu_out = MuxCase(0.U(WORD_LEN.W),Seq(
      (exe_fnc === ALU_ADD )  -> (op1_data +  op2_data),
      (exe_fnc === ALU_SUB )  -> (op1_data -  op2_data),
      (exe_fnc === ALU_AND )  -> (op1_data &  op2_data),
      (exe_fnc === ALU_OR  )  -> (op1_data |  op2_data),
      (exe_fnc === ALU_XOR )  -> (op1_data ^  op2_data),
      (exe_fnc === ALU_SLL )  -> (op1_data << op2_data(4,0))(31,0),
      // Chisel的左移运算符会改变数据位宽，要丢弃新增的最高位；
      (exe_fnc === ALU_SRL )  -> (op1_data >> op2_data(4,0)).asUInt,
      (exe_fnc === ALU_SRA )  -> (op1_data.asSInt >> op2_data(4,0)).asUInt,
      // 右移运算符会丢失数据类型，要用asUInt/asSInt恢复数据类型
      (exe_fnc === ALU_SLT )  -> (op1_data.asSInt < op2_data.asSInt).asUInt,
      (exe_fnc === ALU_SLTU)  -> (op1_data < op2_data).asUInt,
      (exe_fnc === ALU_JALR)  -> ((op1_data + op2_data) & ~1.U(WORD_LEN.W)),
      // Risc-V规定跳转地址最后一位必须为0(指令长度是偶数个字节)，& ～1舍弃最后一位的数值
      (exe_fnc === ALU_CP1 )  ->  op1_data,  // 将op1原样输出
    ))// 根据译码出的exe_fnc选择ALU功能，对于ADD、ADDI、LW、SW等指令，可以复用加法电路
		alu_out
	}
	
	def branch(exe_fnc:UInt, op1_data:UInt, op2_data:UInt): Bool = {
		val br_flg = MuxCase(false.B,Seq(
      (exe_fnc === BR_BEQ ) -> (op1_data === op2_data),
      (exe_fnc === BR_BNE ) -> (op1_data =/= op2_data),
      (exe_fnc === BR_BLT ) -> (op1_data.asSInt <  op2_data.asSInt),
      (exe_fnc === BR_BGE ) -> (op1_data.asSInt >= op2_data.asSInt),
      (exe_fnc === BR_BLTU) -> (op1_data <  op2_data),
      (exe_fnc === BR_BGEU) -> (op1_data >= op2_data),
    ))
		br_flg
	}

  def exRegmem(pc:UInt, inst: UInt, op1_data:UInt, rs2_data:UInt, rd_addr:UInt, alu_out:UInt, mem_wen:UInt, rf_wen:UInt, wb_sel:UInt, csr_addr:UInt, csr_cmd:UInt, imm_z_uext:UInt, mem_reg:memRegBUndle) = {
    mem_reg.pc         := pc
    mem_reg.inst       := inst
    mem_reg.op1_data   := op1_data
    mem_reg.rs2_data   := rs2_data
    mem_reg.rd_addr    := rd_addr
    mem_reg.alu_out    := alu_out
    mem_reg.mem_wen    := mem_wen
    mem_reg.rf_wen     := rf_wen
    mem_reg.wb_sel     := wb_sel
    mem_reg.csr_addr   := csr_addr
    mem_reg.csr_cmd    := csr_cmd
    mem_reg.imm_z_uext := imm_z_uext
  }
  
}