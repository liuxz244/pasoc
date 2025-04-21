package PaSoC

import chisel3._       // chisel本体
import chisel3.util._  // chisel功能
import Instructions._
import Consts._

object Decode {
  // 包含所有立即数处理的函数
  def getImmBundle(inst:UInt): immBundle = {
    def extractAndExtendImmediate(inst: UInt, format: String): UInt = {
      val imm = format match {
          case "I" => inst(31, 20)
          case "S" => Cat(inst(31, 25), inst(11, 7))
          case "J" => Cat(inst(31), inst(19, 12), inst(20), inst(30, 21))
          case "U" => inst(31, 12)
          case "Z" => inst(19, 15)
          case "B" => Cat(inst(31),inst(7),inst(30, 25),inst(11, 8))
          case _ => 0.U
      } // 提取不同格式的立即数
    
      val immExt = format match {
        case "I" => Cat(Fill(20, imm(11)), imm) // 将12位有符号立即数拓展为32位有符号数
        // Fill(20,imm(11)) 是将imm的最高位重复20次, 通过这种方式支持对补码(负数)的拓展
        case "S" => Cat(Fill(20, imm(11)), imm)
        case "J" => Cat(Fill(11, imm(19)), imm, 0.U(1.W))
        case "U" => Cat(imm, Fill(12, 0.U)) //U格式指令的立即数只有高20位, 剩下低12位补0
        case "Z" => Cat(Fill(27, 0.U), imm)
        case "B" => Cat(Fill(19, imm(11)), imm, 0.U(1.W))
        // J型和B型指令立即数最低位始终为0,因为默认存储器为8位, 而riscv只有16位、32位、64位, 最后一位始终为0
        case _ => 0.U
      }
      immExt
    }	
    val imm = Wire(new immBundle)
    imm.i_sext    := extractAndExtendImmediate(inst, "I")
    imm.s_sext    := extractAndExtendImmediate(inst, "S")
    imm.j_sext    := extractAndExtendImmediate(inst, "J")
    imm.u_shifted := extractAndExtendImmediate(inst, "U")
    imm.z_uext    := extractAndExtendImmediate(inst, "Z")
    imm.b_sext    := extractAndExtendImmediate(inst, "B")

    imm
  }

  def decodeOP(inst:UInt): List[UInt] = {
    ListLookup(inst, List(ALU_X,OP1_RS1,OP2_RS2,MEN_X,REN_X,WB_X,CSR_X),
      // 对每条指令的运算内容和操作数类型进行译码, 使相似的指令复用相同的运算器
      // 同时判断是否需要访存、写回等步骤
      Array(
        LW    -> List(ALU_ADD , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_MEM, CSR_X),
        // LW指令使用ALU的加法功能, 使用rs1寄存器, 不使用rs2而使用I型立即数, 
        // 不需要向存储器写入(不访存), 向rs寄存器写回, 从存储器获取写回数据
        SW    -> List(ALU_ADD , OP1_RS1, OP2_IMS, MEN_S, REN_X, WB_X  , CSR_X),
        // SW指令使用ALU的加法功能, 使用rs1寄存器, 不使用rs2而使用S型立即数, 
        // 向存储器写入(访存), 不需写回
        ADD   -> List(ALU_ADD , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
        ADDI  -> List(ALU_ADD , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
        SUB   -> List(ALU_SUB , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
        AND   -> List(ALU_AND , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
        ANDI  -> List(ALU_AND , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
        OR    -> List(ALU_OR  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
        ORI   -> List(ALU_OR  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
        XOR   -> List(ALU_XOR , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
        XORI  -> List(ALU_XOR , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
        SLL   -> List(ALU_SLL , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
        SRL   -> List(ALU_SRL , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
        SRA   -> List(ALU_SRA , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
        SLLI  -> List(ALU_SLL , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
        SRLI  -> List(ALU_SRL , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
        SRAI  -> List(ALU_SRA , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
        SLT   -> List(ALU_SLT , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
        SLTU  -> List(ALU_SLTU, OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
        SLTI  -> List(ALU_SLT , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
        SLTIU -> List(ALU_SLTU, OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
        BEQ   -> List(BR_BEQ  , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , CSR_X),
        BNE   -> List(BR_BNE  , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , CSR_X),
        BGE   -> List(BR_BGE  , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , CSR_X),
        BGEU  -> List(BR_BGEU , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , CSR_X),
        BLT   -> List(BR_BLT  , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , CSR_X),
        BLTU  -> List(BR_BLTU , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , CSR_X),
        JAL   -> List(ALU_ADD , OP1_PC , OP2_IMJ, MEN_X, REN_S, WB_PC , CSR_X),
        JALR  -> List(ALU_JALR, OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_PC , CSR_X),
        LUI   -> List(ALU_ADD , OP1_X  , OP2_IMU, MEN_X, REN_S, WB_ALU, CSR_X),
        AUIPC -> List(ALU_ADD , OP1_PC , OP2_IMU, MEN_X, REN_S, WB_ALU, CSR_X),
        CSRRW -> List(ALU_CP1 , OP1_RS1, OP2_X  , MEN_X, REN_S, WB_CSR, CSR_W),
        CSRRWI-> List(ALU_CP1 , OP1_IMZ, OP2_X  , MEN_X, REN_S, WB_CSR, CSR_W),
        CSRRS -> List(ALU_CP1 , OP1_RS1, OP2_X  , MEN_X, REN_S, WB_CSR, CSR_S),
        CSRRSI-> List(ALU_CP1 , OP1_IMZ, OP2_X  , MEN_X, REN_S, WB_CSR, CSR_S),
        CSRRC -> List(ALU_CP1 , OP1_RS1, OP2_X  , MEN_X, REN_S, WB_CSR, CSR_C),
        CSRRCI-> List(ALU_CP1 , OP1_IMZ, OP2_X  , MEN_X, REN_S, WB_CSR, CSR_C),
        ECALL -> List(ALU_X   , OP1_X  , OP2_X  , MEN_X, REN_X, WB_X  , CSR_E),
      )
    )/*
    LW(Load Word, 加载字): 从存储器读取32位数据到寄存器
      汇编: lw rd, offset(rs1) rd是目标寄存器, 用于存储从内存加载的数据
      offset(rs1)是源操作数, rs1寄存器中的值加上立即数offset构成内存地址
      使用offset, 可以方便遍历数组中的元素, 无需反复修改rs1寄存器的值
    SW(Store Word, 存储字): 将寄存器数据写入存储器
      汇编: sw rs2,offset(rs1) rs2存放要写入的数据, offset(rs1)构成写入地址
    ADD(加法): 将两个寄存器的值相加, 保存到另一个寄存器中 汇编: add rd,rs1,rs2
    SUB(减法): 将两个寄存器的值相减, 保存到另一个寄存器中 汇编: sub rd,rs1,rs2
    ADDI(立即数加法): 将一个寄存器的值和指令中的立即数相加, 保存到另一个寄存器中
      汇编: addi rd,rs1,imm_i
    AND(与): 将两个寄存器的值进行逻辑与, 保存到另一个寄存器中 汇编: and rd,rs1,rs2
    OR(或)、XOR(异或)及其对应的立即数版本: 同上
    SLL(左移): 将rs1的值左移(左移不区分逻辑和算数), 左移位数由rs2值的低5位指定 
      汇编: sll rd,rs1,rs2
    SRL(逻辑右移): 指令同上, 逻辑右移使用0填充最高位
    SRA(算数右移): 指令同上, 算数右移使用符号位(输入数的最高位)填充右移后的最高位, 
                 确保右移后有符号数的符号不变
    SLLI(立即数左移): 左移位数由I型立即数的低5位(称为shamt)指定  汇编: slli rd, rs1,shamt
    SRLI、SRAI: 同上
    SLT(Set if Less Than,如果小则设置, 即比较大小): 若rs1中的有符号数比rs2中的小, 
        则向rd写入1,否则写入0   汇编: slt rd,rs1,rs2
    SLTU:比较两个无符号数;  SLTI、SLTIU: 前两个指令的立即数版本
    JAL(Jump And Link): 跳转至当前PC加上立即数的地址,向rd写入本来下一条指令的地址
          汇编: jal rd,offset
    JALR(Jump And Link Register): 跳转至rs1值加上立即数的地址,向rd写入本来下一指令的地址
          汇编: jalr rd,offset(rs1)
    LUI(Load Upper Immediate,加载高位立即数): 将只有高20位的立即数存入rd中  汇编: lui rd,imm_u
    AUIPC(Add Upper Immediate to PC): 将PC值加上高位立即数, 与使用低位立即数的JALR指令组合可以在PC的32位范围内跳转, 同样可以使LW、SW访问PC周围32位范围的存储器  汇编: auipc rd,imm_u
    ECALL: 发生异常时调用操作系统  汇编: ecall
    BEQ : rs1和rs2的值相等时, 将pc寄存器加减立即数offset的值  汇编: beq rs1,rs2,offset
    BNE : rs1和rs2的值不等时;     BLT: rs1的有符号数小于rs2时;   BGE: rs1的有符号数大于等于rs2时
    BLTU: rs1的无符号数小于rs2时; BGEU: rs1的无符号数大于等于rs2时; 
    */
  }

  def getOP1_data(op1_sel:UInt, rs1_data:UInt, imm:immBundle, pc_reg:UInt): UInt = {
    val op1_data = MuxCase(0.U(WORD_LEN.W),Seq(
      (op1_sel === OP1_RS1) -> rs1_data,   // 如果选择使用rs1寄存器, 那么操作数1就是rs1寄存器的值
      (op1_sel === OP1_PC ) -> pc_reg,     // 获取当前指令在存储器中的地址
      (op1_sel === OP1_IMZ) -> imm.z_uext,
    ))
    op1_data
  }

  def getOP2_data(op2_sel:UInt, rs2_data:UInt, imm:immBundle): UInt = {
    val op2_data = MuxCase(0.U(WORD_LEN.W),Seq(
      (op2_sel === OP2_RS2) -> rs2_data,
      (op2_sel === OP2_IMI) -> imm.i_sext,
      (op2_sel === OP2_IMS) -> imm.s_sext,
      (op2_sel === OP2_IMJ) -> imm.j_sext,
      (op2_sel === OP2_IMU) -> imm.u_shifted,
    ))  // 根据不同的指令, 选择操作数2的值
    op2_data
  }
  
  def whetherRShazard(inst: UInt, rf_wen: UInt, rd_addr: UInt): Bool = {
    // 提取 rs1 和 rs2 的寄存器地址字段
    val rs1_addr_b = inst(19, 15)
    val rs2_addr_b = inst(24, 20)
    
    // 当 rf_wen 是 REN_S 且 rs1 寄存器不为 0, 同时地址与 rd_addr 相等时产生 hazard
    val rs1_hazard = (rf_wen === REN_S) & (rs1_addr_b =/= 0.U) & (rs1_addr_b === rd_addr)
    val rs2_hazard = (rf_wen === REN_S) & (rs2_addr_b =/= 0.U) & (rs2_addr_b === rd_addr)
    
    // 如果任一 hazard 信号为真, 则置 stall 标志
    rs1_hazard | rs2_hazard
  }

  def idRegex(pc: UInt, inst: UInt, op1_data: UInt, op2_data: UInt, rs2_data: UInt, rd_addr: UInt, exe_fnc: UInt,
      mem_wen: UInt, rf_wen: UInt, wb_sel: UInt, csr_addr: UInt, csr_cmd: UInt, imm: immBundle, ex_reg: exRegBUndle
  ): Unit = {
    ex_reg.pc            := pc
    ex_reg.inst          := inst
    ex_reg.op1_data      := op1_data
    ex_reg.op2_data      := op2_data
    ex_reg.rs2_data      := rs2_data
    ex_reg.rd_addr       := rd_addr
    ex_reg.exe_fnc       := exe_fnc
    ex_reg.mem_wen       := mem_wen
    ex_reg.rf_wen        := rf_wen
    ex_reg.wb_sel        := wb_sel
    ex_reg.csr_addr      := csr_addr
    ex_reg.csr_cmd       := csr_cmd
    ex_reg.imm_i_sext    := imm.i_sext
    ex_reg.imm_s_sext    := imm.s_sext
    ex_reg.imm_b_sext    := imm.b_sext
    ex_reg.imm_z_uext    := imm.z_uext
    ex_reg.imm_u_shifted := imm.u_shifted
  }

  def getRSdata(rs1_addr:UInt, regfile_rs1_data:UInt, rs2_addr:UInt, regfile_rs2_data:UInt, mem_rd_addr:UInt, mem_wr:UInt, mem_rd_data:UInt, wb_rd_addr:UInt, wb_wr:UInt, wb_rd_data:UInt): (UInt, UInt) = {
    // rs1/rs2对应指令执行的数据源, rd对应数据要写入的寄存器
    val rs1_data = MuxCase(regfile_rs1_data, Seq(
      ( rs1_addr === 0.U ) -> 0.U(WORD_LEN.W),
      ((rs1_addr === mem_rd_addr) && (mem_wr === REN_S)) -> mem_rd_data,  // MEM阶段直通读取
      ((rs1_addr === wb_rd_addr ) && ( wb_wr === REN_S)) -> wb_rd_data    // WB 阶段直通读取
    ))
    // 根据寄存器地址的值, 选择要读取的寄存器数据
    val rs2_data = MuxCase(regfile_rs2_data, Seq(
      ( rs2_addr === 0.U ) -> 0.U(WORD_LEN.W),
      ((rs2_addr === mem_rd_addr) && (mem_wr === REN_S)) -> mem_rd_data,  // MEM阶段直通读取
      ((rs2_addr === wb_rd_addr ) && ( wb_wr === REN_S)) -> wb_rd_data    // WB 阶段直通读取
    ))
    // 如果地址不为0, 则从寄存器堆中读取数据, 如果地址为0, 则返回0,因为Risc-V规定0号寄存器的值始终为0
    (rs1_data, rs2_data)  // 一次返回两个值
  }

  def getRegAddr(inst:UInt): (UInt,UInt,UInt) = {
    val rd_addr  = inst(11,7 )  // rs1寄存器编号是指令的第15-19位
    val rs1_addr = inst(19,15)  // rs2寄存器编号是指令的第20-24位
    val rs2_addr = inst(24,20)  //  rd寄存器编号是指令的第 7-11位
    (rd_addr,rs1_addr,rs2_addr)
  }

}