package PaSoC

import Consts._
import chisel3._       // chisel本体
import chisel3.util._  // chisel功能
import chiseltest._    // chisel测试
import org.scalatest.flatspec.AnyFlatSpec // scala测试


class DBusMuxTest extends AnyFlatSpec with ChiselScalatestTester {
  "DBusMux" should "正确路由 CPU 信号到外设，并汇聚外设响应" in {
    test(new DBusMux).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      // --------------------------
      // 测试 1:针对外设 0 (addr[31:29]=0) 
      // 向 CPU 发出一个写操作请求，同时外设 0 返回数据
      dut.io.cpu.valid.poke(true.B)
      dut.io.cpu.wen.poke(true.B)
      dut.io.cpu.byte.poke("b1111".U)
      dut.io.cpu.addr.poke("h00000000".U)  // 高 3 位 = 0 选中外设 0
      dut.io.cpu.wdata.poke("hdeadbeef".U)

      // 模拟外设 0 响应:读数据和 done 信号
      dut.io.periphs(0).rdata.poke("h12345678".U)
      dut.io.periphs(0).done.poke(true.B)
      // 其他外设保持默认响应 (可明确置 0 或 false) 
      for(i <- 1 until 8) {
        dut.io.periphs(i).rdata.poke(0.U)
        dut.io.periphs(i).done.poke(false.B)
      }

      dut.clock.step(1)
      dut.io.cpu.rdata.expect("h12345678".U)
      dut.io.cpu.done.expect(true.B)

      // --------------------------
      // 测试 2:针对外设 3 (addr[31:29]=3) 
      // 发出一个读操作请求 (wen = false) ，目标外设 3返回数据
      dut.io.cpu.valid.poke(true.B)
      dut.io.cpu.wen.poke(false.B)  // 读操作
      dut.io.cpu.byte.poke("b1010".U) // 字节使能随便设置
      dut.io.cpu.addr.poke("h60000000".U)  // 0x60000000 = 3 << 29
      dut.io.cpu.wdata.poke(0.U)

      // 模拟外设 3 返回响应:注意其他外设响应保持默认
      dut.io.periphs(3).rdata.poke("habcdef01".U)
      dut.io.periphs(3).done.poke(true.B)
      for { i <- Seq(0, 1, 2, 4, 5, 6, 7) } {
        dut.io.periphs(i).rdata.poke(0.U)
        dut.io.periphs(i).done.poke(false.B)
      }
      
      dut.clock.step(1)
      dut.io.cpu.rdata.expect("habcdef01".U)
      dut.io.cpu.done.expect(true.B)

      // --------------------------
      // 测试 3:无效请求 (valid = false) ，输出应使用默认值
      dut.io.cpu.valid.poke(false.B)
      dut.clock.step(1)
      dut.io.cpu.rdata.expect(0.U)
      dut.io.cpu.done.expect(false.B)
    }
  }
}