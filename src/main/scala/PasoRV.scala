package PaSoC

import Instructions._  // 导入common.scala中的Instructions定义和Consts定义
import Consts._
import Decode._        // 使用Decode.scala中定义的功能
import Execute._
import MemAccess._

import chisel3._       // chisel本体
import chisel3.util._  // chisel功能


class PasoRV extends Module{  // CPU内核模块
  val io = IO(new Bundle {
    // 翻转ItemPortIo,即addr输出, inst输入
    val imem = Flipped(new ImemPortIo())
    val dbus = Flipped(new DBusPortIo())
    val exit = Output(Bool())
    // 输出端口exit在程序处理结束时变为true.B
  })

  val regfile = RegInit(VecInit(Seq.fill(32)(0.U(WORD_LEN.W))))  // 使用Vec创建32个32位宽的寄存器并初始化为0
  //val csr_reg = Mem(4096,UInt(WORD_LEN.W))  // 4096个 CSR (Control & Status Register, 控制与状态寄存器)
  // 用于中断/异常管理、虚拟存储器设定、显示CPU状态等

  // -----------------流水线寄存器定义-----------------

  val id_reg  = RegInit(0.U.asTypeOf(new idRegBUndle))
  val ex_reg  = RegInit(0.U.asTypeOf(new exRegBUndle))
  val mem_reg = RegInit(0.U.asTypeOf(new memRegBUndle))
  val wb_reg  = RegInit(0.U.asTypeOf(new wbRegBUndle))

  // ----------IF(Instruction Fetch)取指阶段----------

  val stall_hazard = Wire(Bool())  // 出现流水线数据冒险, 需要暂停流水线
  val stall_bus    = Wire(Bool())  // 从机未准备好响应,   需要暂停流水线
  val stall_flg    = Wire(Bool())  // 流水线停顿信号
  val ex_br_flg    = Wire(Bool())  // 分支标志, 有条件的改变PC寄存器值
  val ex_br_tag    = Wire(UInt(WORD_LEN.W))  // 分支目标
  val ex_jmp_flg   = Wire(Bool())  // 跳转标志, 无条件的改变PC寄存器值
  val ex_alu_out   = Wire(UInt(WORD_LEN.W))

  // 生成初始值为0的PC寄存器, 每个循环加4
  val if_reg_pc = RegInit(START_ADDR)  // START_ADDR在Const中定义为0
  io.imem.addr := if_reg_pc   // 读取指令
  val if_inst = io.imem.inst  // 取得指令

  val if_pc_plus4 = if_reg_pc + 4.U(WORD_LEN.W)  // 这里的+4是指四字节, 对应到存储器地址实际只增加1
  val if_pc_next  = MuxCase(if_pc_plus4, Seq(
    // 优先级很重要！如果跳转的同时发生流水线停顿, 则跳转优先
    ex_br_flg  -> ex_br_tag ,
    ex_jmp_flg -> ex_alu_out, // 跳转地址由ALU给出
    //(if_inst === ECALL) -> csr_reg(0x305), // 发生异常时跳转到CSR中mtvec保存的trap_vector地址
    stall_flg  -> if_reg_pc  // 流水线停顿时保持原来的pc
  ))

  if_reg_pc := if_pc_next  // 更新pc
  id_reg.pc := Mux(stall_flg, id_reg.pc, if_reg_pc)  // 流水线停顿时保持原来的pc

  id_reg.inst := MuxCase(if_inst, Seq(
    (ex_br_flg || ex_jmp_flg) -> BUBBLE,  // 分支、跳转时冲刷流水线
    stall_flg -> id_reg.inst,
  ))

  // ----------ID(Instruction Decode)译码阶段----------
  
  // 产生流水线停顿信号, 避免数据冒险
  stall_hazard := whetherRShazard(id_reg.inst, ex_reg.rf_wen, ex_reg.rd_addr)
  // 分支、跳转、停顿时冲刷流水线
  val id_inst = Mux((ex_br_flg || ex_jmp_flg || stall_hazard), BUBBLE, id_reg.inst)  

  // 寄存器读取
  val mem_rd_data = Wire(UInt(WORD_LEN.W))
  val (id_rd_addr, id_rs1_addr, id_rs2_addr) = getRegAddr(id_inst)
  val (id_rs1_data, id_rs2_data) = getRSdata(
    id_rs1_addr, regfile(id_rs1_addr), id_rs2_addr, regfile(id_rs2_addr),
    mem_reg.rd_addr, mem_reg.rf_wen, mem_rd_data, wb_reg.rd_addr, wb_reg.rf_wen, wb_reg.rd_data
  )

  val id_imm = getImmBundle(id_inst)  // 使用在Decode.scala中定义的函数获取位宽拓展后的立即数, imm是一个包

  val csignals = decodeOP(id_inst)  // 使用自定义函数对指令使用的功能进行解码
  val id_exe_fnc :: id_op1_sel :: id_op2_sel :: id_mem_wen :: id_rf_wen :: id_wb_sel :: id_csr_cmd :: Nil = csignals  
  // 将csignals列表的每一项分别赋给exe_fnc等变量, 多出的项被抛弃(Nil)

  val id_op1_data = getOP1_data(id_op1_sel, id_rs1_data, id_imm, id_reg.pc)
  val id_op2_data = getOP2_data(id_op2_sel, id_rs2_data, id_imm)

  // 指令高12位表示CSR地址,当发生异常时将csr_addr设为mcause寄存器, 保存当前的CPU模式
  val id_csr_addr = Mux(id_csr_cmd === CSR_E, 0x342.U(CSR_ADDR_LEN.W), id_inst(31,20))

  // 将译码阶段信息传递到执行阶段
  when(!stall_bus) {  // 从机未准备好响应时暂停流水线传输
    idRegex(
      id_reg.pc, id_inst, id_op1_data, id_op2_data, id_rs2_data, id_rd_addr, id_exe_fnc, 
      id_mem_wen, id_rf_wen, id_wb_sel, id_csr_addr, id_csr_cmd, id_imm, ex_reg
    )
  }
  // ---------------EX(Execute)执行阶段----------------

  // ALU 运算
  ex_alu_out := ALU(ex_reg.exe_fnc, ex_reg.op1_data, ex_reg.op2_data)
  
  // 分支处理：计算分支目标地址与判断条件
  ex_br_tag  := ex_reg.pc + ex_reg.imm_b_sext
  ex_br_flg  := branch(ex_reg.exe_fnc, ex_reg.op1_data, ex_reg.op2_data)
  ex_jmp_flg := (ex_reg.wb_sel === WB_PC)

  // 将执行阶段结果传递到访存阶段
  when(!stall_bus) {
    exRegmem(
      ex_reg.pc, ex_reg.inst, ex_reg.op1_data, ex_reg.rs2_data, ex_reg.rd_addr, ex_alu_out, ex_reg.mem_wen,
      ex_reg.rf_wen, ex_reg.wb_sel, ex_reg.csr_addr, ex_reg.csr_cmd, ex_reg.imm_z_uext, mem_reg
    )
  }
  // -----------MEM(MEMorary access)访存阶段-----------

  // 判断当前指令是否为访存操作, 真的要访存时才使能外设
  val mem_access = (mem_reg.wb_sel === WB_MEM || mem_reg.mem_wen === MEN_S)
  io.dbus.valid := mem_access
  
  // 在刚开始一笔访存事务时输出一个时钟周期的 start 脉冲
  val bus_transaction = RegInit(false.B)
  when (!mem_access) {
    bus_transaction := false.B  // 当当前没有访存操作时，清除事务状态
  } .elsewhen (bus_transaction && io.dbus.done) {
    bus_transaction := false.B  // 事务进行中，等待外设响应完成
  } .elsewhen (!bus_transaction && mem_access) {
    // 当无访存停顿且当前没有正在进行的事务时，若检测到一个访存操作，
    // 则认为这是一个新事务，置位 bus_transaction
    bus_transaction := true.B
  }
  // 输出 start 信号：仅在 mem_access 为 true 且当前没有事务进行（即刚启动访存）且无停顿时输出 1
  io.dbus.start := (!bus_transaction && mem_access)

  stall_bus := mem_access && !io.dbus.done  // 若为访存操作且DBus尚未返回响应, 则产生总线等待
  stall_flg := stall_hazard || stall_bus    // 全局暂停信号由数据冒险及访存等待联合产生
  
  io.dbus.addr  := mem_reg.alu_out  // 访问地址
  io.dbus.wen   := mem_reg.mem_wen === MEN_S  // 是否写入
  io.dbus.wdata := mem_reg.rs2_data // 写入数据
  io.dbus.byte  := "b1111".U  // 未实现单字节读写, 目前全部都是32位读写
  /*
  val csr_rdata = csr_reg(mem_reg.csr_addr)  // 读取CSR
  val csr_wdata = getCSR_wdata(mem_reg.csr_cmd, csr_rdata, mem_reg.op1_data)
  when(mem_reg.csr_cmd > 0.U){  // 取指到CSR指令时
    csr_reg(mem_reg.csr_addr) := csr_wdata
  }
  */
  // 写回数据选择： 根据 wb_sel 选择总线、PC、CSR或 ALU 运算结果
  mem_rd_data := MuxCase(mem_reg.alu_out, Seq(  // 默认输出alu_out
    (mem_reg.wb_sel === WB_MEM) -> io.dbus.rdata,  // 从Dbus总线读取数据
    (mem_reg.wb_sel === WB_PC ) -> (mem_reg.pc + 4.U(WORD_LEN.W)),  // 本来的下一条指令地址
    //(mem_reg.wb_sel === WB_CSR) -> csr_rdata  // 读取CSR
  ))
  
  // 更新写回阶段流水线寄存器
  when(!stall_bus) {
    memRegwb(mem_reg.pc, mem_reg.inst, mem_reg.rd_addr, mem_reg.rf_wen, mem_rd_data, wb_reg)
  }
  // --------------WB(Write Back)写回阶段--------------

  when(wb_reg.rf_wen === REN_S) {
    regfile(wb_reg.rd_addr) := wb_reg.rd_data  // 将数据写回寄存器
  }

  // ------------------ IO & Debug -------------------
  
  io.exit := (wb_reg.inst === UNIMP)  // 未实现此指令, 把它当成退出指令

  // 仿真时输出信息
  printf(p"if_reg_pc       : 0x${Hexadecimal(if_reg_pc)}\n"      )
  printf(p"id_reg.pc       : 0x${Hexadecimal(id_reg.pc)}\n"      )
  printf(p"id_reg.inst     : 0x${Hexadecimal(id_reg.inst)}\n"    )
//printf(p"stall_flg       : 0x${Hexadecimal(stall_flg)}\n"      )
  printf(p"id_inst         : 0x${Hexadecimal(id_inst)}\n"        )
  printf(p"id_rs1_data     : 0x${Hexadecimal(id_rs1_data)}\n"    )
  printf(p"id_rs2_data     : 0x${Hexadecimal(id_rs2_data)}\n"    )
  printf(p"ex_reg.pc       : 0x${Hexadecimal(ex_reg.pc)}\n"      )
  printf(p"ex_reg.op1_data : 0x${Hexadecimal(ex_reg.op1_data)}\n")
  printf(p"ex_reg.op2_data : 0x${Hexadecimal(ex_reg.op2_data)}\n")
  printf(p"ex_alu_out      : 0x${Hexadecimal(ex_alu_out)}\n"     )
  printf(p"mem_reg.pc      : 0x${Hexadecimal(mem_reg.pc)}\n"     )
  printf(p"mem_rd_data     : 0x${Hexadecimal(mem_rd_data)}\n"    )
  printf(p"wb_reg.pc       : 0x${Hexadecimal(wb_reg.pc)}\n"      )
  printf(p"wb_reg.rd_data  : 0x${Hexadecimal(wb_reg.rd_data)}\n" )
  printf("----------------------\n")

}
