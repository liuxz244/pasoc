package PaSoC

import scala.FilePrepender._
// 导入scala.sacla中的prependLine方法, 往生成的.sv添加启用mem初始化的定义

import chisel3._       // chisel本体
import chisel3.util._  // chisel功能
import _root_.circt.stage.ChiselStage  // 生成systemverilog
import sys.process._   // 使用linux命令


class PaSoC extends Module {  // 连接CPU和存储器的封装
  val io = IO(new Bundle {
    val exit = Output(Bool())  // 仿真退出信号
    val gpio = new GPIO
  })

  // 例化PasoRV模块和Memory模块
  val PasoRV     = Module(new PasoRV()    )
  val InstMemory = Module(new InstCache() )
  val DataMemory = Module(new DataMemory())
  val DBusMux    = Module(new DBusMux()   )
  val GpioCtrl   = Module(new GpioCtrl()  )

  // PasoRV的io和Memory的IO是输入输出翻转的关系, 直接用<>连接
  PasoRV.io.imem <> InstMemory.io
  PasoRV.io.dbus <> DBusMux.io.cpu
  // 将数据存储器作为外设 0 连接到 DBusMux 的 periphs(0)
  DBusMux.io.periphs(0) <> DataMemory.io
  DBusMux.io.periphs(1) <> GpioCtrl.io.bus

  io.gpio <> GpioCtrl.io.gpio

  // 对于未使用的外设 2 至 15, 将输入信号赋默认值防止综合问题, 例如保证输入无效
  for (i <- 2 until 16) {
    DBusMux.io.periphs(i).done  := false.B
    DBusMux.io.periphs(i).rdata := 0.U
  }
  
  io.exit := PasoRV.io.exit
}

object Main extends App {  // 生成.sv和.v的主函数
  ChiselStage.emitSystemVerilogFile(
    new PaSoC,
    Array( ),   // 不知道有什么用，但没了这个下面一行就要报错
    Array("-strip-debug-info","-disable-all-randomization"),
    // 禁用不可读的RANDOM和没用的注释
  )
  val filename = "PaSoC"
  val lineToAdd = "`define ENABLE_INITIAL_MEM_"
  prependLine(s"${filename}.sv", lineToAdd)
  val cmd = s"sv2v ${filename}.sv -w ${filename}.v"  // 要执行的linux命令
  val output = cmd.!!  // 执行命令并获取返回结果
}
