package PaSoC

import Consts._

import chisel3._       // chisel本体
import chisel3.util._  // chisel功能
import chisel3.util.experimental.loadMemoryFromFileInline  // 向存储器写入初始值


// 定义指令存储器模块, 读取无延迟
class InstCache extends Module {
  val io = IO(new ImemPortIo())

  // 实例化存储器, 存储 512 个 32 位字
  val mem = Mem(512, UInt(32.W))
  loadMemoryFromFileInline(mem, "src/main/resources/hex/flow_led.hex")
  
  // 按字地址获取指令
  val iaddr = io.addr >> 2.U
  val memData = mem.read(iaddr)
  
  io.inst := memData
}

// 定义数据存储器模块, 读取无延迟
class DataCache extends Module {
  val io = IO(new DBusPortIo())

  // 实例化存储器, 存储 4096 个 32 位字
  val mem = Mem(4096, UInt(32.W))
  // 总线接口给出的地址为字节地址, 右移2位得到字地址
  val daddr = io.addr >> 2.U

  // 这个数据存储器总能一拍就完成读写
  io.done := io.valid

  // 写操作: 只有在总线周期有效且 wen 为真时进行写入
  when(io.valid && io.wen) {
    val oldValue = mem(daddr)
    // 如果所有字节均使能, 则一次性写入整个 32 位字
    when(io.byte === "b1111".U) {
      mem(daddr) := io.wdata
    } .otherwise {
      // 分字节写入, 按小端优先顺序组合
      val byte0 = Mux(io.byte(0), io.wdata(7, 0),   oldValue(7, 0))
      val byte1 = Mux(io.byte(1), io.wdata(15, 8),  oldValue(15, 8))
      val byte2 = Mux(io.byte(2), io.wdata(23, 16), oldValue(23, 16))
      val byte3 = Mux(io.byte(3), io.wdata(31, 24), oldValue(31, 24))
      mem(daddr) := Cat(byte3, byte2, byte1, byte0)
    }
  }
  // 读操作: 当 valid 信号有效且不是写操作时, 输出对应地址的数据；否则输出 0
  io.rdata := Mux(io.valid && !io.wen, mem(daddr), 0.U)
}

// 读写有一周期延迟的数据存储器, 用于在FPGA上综合为Block RAM
class DataMemory extends Module {
  val io = IO(new DBusPortIo)

  // 将存储器拆分为 4 个 8 位 bank, 每个 bank 容量为 4096 个字节
  val bank0 = SyncReadMem(4096, UInt(8.W))  // 存储低 8 位
  val bank1 = SyncReadMem(4096, UInt(8.W))  // 存储次低 8 位
  val bank2 = SyncReadMem(4096, UInt(8.W))  // 存储次高 8 位
  val bank3 = SyncReadMem(4096, UInt(8.W))  // 存储高 8 位

  // 由于总线地址为字节地址, 右移2位得到字地址 (4096 个 32 位字）
  val daddr = io.addr >> 2

  // 总线完成信号由 start 信号产生, 一拍后输出
  io.done := RegNext(io.start, init = false.B)

  // 区分读写操作: 写操作时 io.wen 为 true, 读操作时 io.wen 为 false
  val doRead = io.start && !io.wen

  // 发起同步读操作。注意SyncReadMem 的 read 在下一拍返回数据
  val rdata0 = bank0.read(daddr, doRead)
  val rdata1 = bank1.read(daddr, doRead)
  val rdata2 = bank2.read(daddr, doRead)
  val rdata3 = bank3.read(daddr, doRead)

  // 用寄存器保存上一拍是否有有效的读操作
  val readValid = RegNext(doRead, init = false.B)
  // 读操作时, 依据上拍发起的读请求, 将各 bank 数据按小端组合成 32 位数据；否则输出 0
  io.rdata := Mux(readValid, Cat(rdata3, rdata2, rdata1, rdata0), 0.U)

  // 写操作: 当 start 有效且 wen 为 true 时进行写入
  when(io.start && io.wen) {
    // 如果所有字节均使能, 则各 bank 同时写入各自的数据
    when(io.byte === "b1111".U) {
      bank0.write(daddr, io.wdata(7,  0))
      bank1.write(daddr, io.wdata(15, 8))
      bank2.write(daddr, io.wdata(23, 16))
      bank3.write(daddr, io.wdata(31, 24))
    } .otherwise {
      // 部分字节写入, 各 bank 根据对应的字节使能分别写入数据
      when(io.byte(0)) {
        bank0.write(daddr, io.wdata(7, 0))
      }
      when(io.byte(1)) {
        bank1.write(daddr, io.wdata(15, 8))
      }
      when(io.byte(2)) {
        bank2.write(daddr, io.wdata(23, 16))
      }
      when(io.byte(3)) {
        bank3.write(daddr, io.wdata(31, 24))
      }
    }
  }
}



// 存储器模块, 已经弃用!!!
class Memory extends Module{  
  val io = IO(new Bundle {
    val imem = new ImemPortIo()  // 例化上面定义的指令接口模板
    val dmem = new DBusPortIo()  // 例化上面定义的数据接口模板
  })

  val mem = Mem(4096,UInt(32.W))  // 生成32位宽16KB寄存器作为存储器, 4096×32b=16KB

  loadMemoryFromFileInline(mem,"src/main/resources/hex/ctest.hex")  // 向存储器写入初始程序
  /*  教程里用的8位存储器, 取指时要一次取4个地址的数据, 所以pc寄存器每次+4
  io.imem.inst := Cat(
    mem(io.imem.addr + 3.U(WORD_LEN.W)),
    mem(io.imem.addr + 2.U(WORD_LEN.W)),
    mem(io.imem.addr + 1.U(WORD_LEN.W)),
    mem(io.imem.addr),
  )   // 拼接4个地址存储的32位指令,使用小端排序 
  */

  val iaddr = Wire(UInt(WORD_LEN.W))
  val daddr = Wire(UInt(WORD_LEN.W))
  iaddr := (io.imem.addr >> 2.U(WORD_LEN.W))
  daddr := (io.dmem.addr >> 2.U(WORD_LEN.W))

  io.imem.inst  := mem(iaddr)
  io.dmem.rdata := mem(daddr)
    
  when(io.dmem.wen) {
    mem(daddr) := io.dmem.wdata
  }
}
