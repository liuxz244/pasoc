package PaSoC

import chisel3._       // chisel本体
import Consts._

class ImemPortIo extends Bundle{  // 定义指令输入输出接口
  val addr  = Input( UInt(WORD_LEN.W))  // 存储器地址
  val inst  = Output(UInt(WORD_LEN.W))  // 输出指令
}  // WORD_LEN在上面的Const中定义为32

class DBusPortIo extends Bundle{  // 定义数据总线接口
  val valid = Input( Bool())  // 主机正在访问外设
  val start = Input( Bool())  // 主机开始读写 (用于有延迟外设)
  // 主机的start产生逻辑依赖done,为避免形成combinational loop, 无延迟外设由valid发起读写
  val done  = Output(Bool())  // 外设返回响应

  val byte  = Input( UInt(4.W))  // 字节使能 (针对32位数据)
  val wen   = Input( Bool())     // 主机要求写入
  val addr  = Input( UInt(WORD_LEN.W))  // 外设地址
  val wdata = Input( UInt(WORD_LEN.W))  // 写入数据
  val rdata = Output(UInt(WORD_LEN.W))  // 读出数据
}

class decodeBundle() extends Bundle {
  val exe_fnc = UInt(EXE_FUN_LEN.W) ; val op1_sel = UInt(OP1_LEN.W)
  val op2_sel = UInt(OP2_LEN.W)     ; val mem_wen = UInt(MEN_LEN.W)
  val rf_wen  = UInt(REN_LEN.W)     ; val wb_sel  = UInt(WB_SEL_LEN.W)
  val csr_cmd = UInt(CSR_LEN.W)
}

class immBundle() extends Bundle {
  val i_sext    = UInt(WORD_LEN.W)
  val s_sext    = UInt(WORD_LEN.W)
  val b_sext    = UInt(WORD_LEN.W)
  val j_sext    = UInt(WORD_LEN.W)
  val u_shifted = UInt(WORD_LEN.W)
  val z_uext    = UInt(WORD_LEN.W)
}

// IF/ID 阶段寄存器 Bundle
class idRegBUndle() extends Bundle {
  val pc   = UInt(WORD_LEN.W)
  val inst = UInt(WORD_LEN.W)
}

// Id/EX 阶段寄存器 Bundle
class exRegBUndle() extends Bundle {
  val pc            = UInt(WORD_LEN.W)
  val inst          = UInt(WORD_LEN.W)
  val rd_addr       = UInt(ADDR_LEN.W)
  val op1_data      = UInt(WORD_LEN.W)
  val op2_data      = UInt(WORD_LEN.W)
  val rs2_data      = UInt(WORD_LEN.W)
  val exe_fnc       = UInt(EXE_FUN_LEN.W)
  val mem_wen       = UInt(MEN_LEN.W)
  val rf_wen        = UInt(REN_LEN.W)
  val wb_sel        = UInt(WB_SEL_LEN.W)
  val csr_addr      = UInt(CSR_ADDR_LEN.W)
  val csr_cmd       = UInt(CSR_LEN.W )
  val imm_i_sext    = UInt(WORD_LEN.W)
  val imm_s_sext    = UInt(WORD_LEN.W)
  val imm_b_sext    = UInt(WORD_LEN.W)
  val imm_u_shifted = UInt(WORD_LEN.W)
  val imm_z_uext    = UInt(WORD_LEN.W)
}

// EX/MEM 阶段寄存器 Bundle
class memRegBUndle() extends Bundle {
  val pc         = UInt(WORD_LEN.W)
  val inst       = UInt(WORD_LEN.W)
  val rd_addr    = UInt(ADDR_LEN.W)
  val op1_data   = UInt(WORD_LEN.W)
  val rs2_data   = UInt(WORD_LEN.W)
  val mem_wen    = UInt(MEN_LEN.W )
  val rf_wen     = UInt(REN_LEN.W )
  val wb_sel     = UInt(WB_SEL_LEN.W)
  val csr_addr   = UInt(CSR_ADDR_LEN.W)
  val csr_cmd    = UInt(CSR_LEN.W )
  val imm_z_uext = UInt(WORD_LEN.W)
  val alu_out    = UInt(WORD_LEN.W)
}

// MEM/WB 阶段寄存器 Bundle
class wbRegBUndle() extends Bundle {
  val pc      = UInt(WORD_LEN.W)
  val inst    = UInt(WORD_LEN.W)
  val rd_addr = UInt(ADDR_LEN.W)
  val rf_wen  = UInt(REN_LEN.W )
  val rd_data = UInt(WORD_LEN.W)
}

class GPIO() extends Bundle {
  val In  = Input (UInt(32.W))
  val Out = Output(UInt(32.W))
}