package PaSoC
// 要和CPU在同一包下才能找到SoC模块

import chisel3._       // chisel本体
import chiseltest._    // chisel测试
import org.scalatest.flatspec.AnyFlatSpec // scala测试
// CPU测试
class HexTest extends AnyFlatSpec with ChiselScalatestTester {
    "Hex Test" should "pass" in {
        test(new PaSoC).withAnnotations(Seq(WriteVcdAnnotation))
        { dut =>  // dut是PaSoC的例化
            // 当exit不为1时循环
            while(!dut.io.exit.peek().litToBoolean) {
                // 信号名.peek()获取信号值
                // litToBoolean()将chisel的Bool型转为scala的Boolean型
                dut.clock.step(1)  // 给一个时钟脉冲
            }
        }
    }
}