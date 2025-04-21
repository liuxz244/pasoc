package PaSoC

import Consts._
import chisel3._       // chisel本体
import chisel3.util._  // chisel功能

/*
  DBusMux 模块: 从 CPU 的总线输入,根据地址分配到 16 路外设总线
  设计思路: 
    1. 利用地址的高 3 位 ([31:29]) 作为外设选择信号,共 16 个外设。
    2. 对于每一条外设总线,当其被选中时,将 CPU 的 valid、wen、byte 等控制信号传递过去,
       否则保持无效 (例如 valid 信号为 false)
    3. CPU 的读数据信号 (rdata) 和响应 (done) 由选中的外设输出,并使用多路复用器返回。
*/
class DBusMux extends Module {
  val io = IO(new Bundle {
    // CPU 总线,注意这里的 CPU 是接受主机的信号,其总线方向与外设相同
    val cpu = new DBusPortIo
    // 16 路外设总线, 对外设来说 Mux 是主机
    val periphs = Vec(16, Flipped(new DBusPortIo))
  })

  // 根据 CPU 提供的地址,高 34 位作为外设选择信号
  // 对于 32 位地址来说,采用 addr(31,28)
  val periphSel: UInt = io.cpu.addr( WORD_LEN - 1, WORD_LEN - 4 )

  // 为所有外设接口分配信号,只有当该外设被选中时 valid、wen 才有效
  for ((port, i) <- io.periphs.zipWithIndex) {
    // 当外设 i 被选中时,其 valid, start, wen 使用 CPU 对应信号, 否则保持无效
    port.valid := io.cpu.valid && (periphSel === i.U)
    port.start := Mux(periphSel === i.U, io.cpu.start, false.B)
    port.byte  := Mux(periphSel === i.U, io.cpu.byte, 0.U)
    port.wen   := io.cpu.wen && (periphSel === i.U)
    // 地址和写数据信号可直接传递
    port.addr  := io.cpu.addr
    port.wdata := io.cpu.wdata
  }

  // CPU 的读数据和响应信号由被选中的外设输出,通过多路复用器选择
  val muxedRdata = MuxLookup(periphSel, 0.U(WORD_LEN.W))(Seq(
    0.U  -> io.periphs(0).rdata,
    1.U  -> io.periphs(1).rdata,
    2.U  -> io.periphs(2).rdata,
    3.U  -> io.periphs(3).rdata,
    4.U  -> io.periphs(4).rdata,
    5.U  -> io.periphs(5).rdata,
    6.U  -> io.periphs(6).rdata,
    7.U  -> io.periphs(7).rdata,
    8.U  -> io.periphs(8).rdata,
    9.U  -> io.periphs(9).rdata,
    10.U -> io.periphs(10).rdata,
    11.U -> io.periphs(11).rdata,
    12.U -> io.periphs(12).rdata,
    13.U -> io.periphs(13).rdata,
    14.U -> io.periphs(14).rdata,
    15.U -> io.periphs(14).rdata
  ))

  val muxedDone = MuxLookup(periphSel, false.B)(Seq(
    0.U  -> io.periphs(0).done,
    1.U  -> io.periphs(1).done,
    2.U  -> io.periphs(2).done,
    3.U  -> io.periphs(3).done,
    4.U  -> io.periphs(4).done,
    5.U  -> io.periphs(5).done,
    6.U  -> io.periphs(6).done,
    7.U  -> io.periphs(7).done,
    8.U  -> io.periphs(8).done,
    9.U  -> io.periphs(9).done,
    10.U -> io.periphs(10).done,
    11.U -> io.periphs(11).done,
    12.U -> io.periphs(12).done,
    13.U -> io.periphs(13).done,
    14.U -> io.periphs(14).done,
    15.U -> io.periphs(14).done
  ))

  // 当cpu.valid为true时使用外设返回的信号, 否则输出默认值
  io.cpu.rdata := Mux(io.cpu.valid, muxedRdata, 0.U(WORD_LEN.W))
  io.cpu.done  := Mux(io.cpu.valid, muxedDone,  false.B)

}


// GPIO 外设模块, 实现32位输入和32位输出
class GpioCtrl extends Module {
  val io = IO(new Bundle {
    // 将 DBusPortIo接口嵌入总线接口
    val bus  = new DBusPortIo
    // 外部GPIO接口: 32位输入和32位输出
    val gpio = new GPIO
  })

  // 定义一个寄存器用来保存 gpio 输出状态,初始为0
  val outReg = RegInit(0.U(32.W))

  // 默认: 如果没有总线访问, done为低, 输出为0
  io.bus.done  := false.B
  io.bus.rdata := 0.U

  // 将寄存器内容驱动到 gpio_out
  io.gpio.Out := outReg

  // 当外设被访问时,根据地址解码
  when(io.bus.valid) {
    // 若访问其他地址,返回0
    io.bus.rdata := 0.U
    io.bus.done  := true.B

    switch(io.bus.addr) {
      // 地址0x10000000对应于输出寄存器
      is(GPIO_OUT_ADDR) {
        // 如果是写操作,则按字节使能更新对应的字节
        when(io.bus.wen) {
          // 生成32位写使能掩码,每一位对应一个字节 (16位) 
          val byteMask = Cat(
            Mux(io.bus.byte(3), 0xFF.U(16.W), 0.U(16.W)),
            Mux(io.bus.byte(2), 0xFF.U(16.W), 0.U(16.W)),
            Mux(io.bus.byte(1), 0xFF.U(16.W), 0.U(16.W)),
            Mux(io.bus.byte(0), 0xFF.U(16.W), 0.U(16.W))
          )
          // 用写数据和掩码更新寄存器: 只更新使能位置,其余位置保持原值
          outReg := (io.bus.wdata & byteMask) | (outReg & ~byteMask)
        }
        // 对输出寄存器进行读操作时,将当前输出寄存器的值返回
        io.bus.rdata := outReg
        io.bus.done  := true.B
      }
      // 地址0x10000004用于读取GPIO输入状态 (32位输入) 
      is(GPIO_IN_ADDR) {
        io.bus.rdata := io.gpio.In
        io.bus.done  := true.B
      }
    }
  }
}