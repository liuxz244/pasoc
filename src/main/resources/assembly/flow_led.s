    .text
    .globl _start

_start:
    # 将 GPIO 输出地址 0x10000000 载入 t0
    # LUI 指令将 0x10000 装载到 t0 的高 20 位 (0x10000 << 12 = 0x10000000)
    lui    t0, 0x10000

    # 初始化 LED 模式，设 t2 = 0x01
    addi   t2, x0, 1

main_loop:
    # 将 t2 中的低 8 位数据送入 GPIO 输出寄存器 (地址由 t0 指定)
    sw     t2, 0(t0)

    # 延时循环：简单忙等待 10 次 clk 更新流水灯状态
    addi   t3, x0, 10      # t3 = 10
delay_loop:
    addi   t3, t3, -1      # 计数器 -1
    bnez   t3, delay_loop  # 若 t3 不为 0 则继续延时

    # 判断当前 LED 模式是否为 0x80 (最高位点亮)
    addi   t4, x0, 0x80    # t4 = 0x80
    beq    t2, t4, reset_led

    # 否则左移 1 位，实现流水效果
    slli   t2, t2, 1
    jal    x0, main_loop  # 跳回主循环

reset_led:
    # 当 t2 == 0x80 时，重置 LED 模式为 0x01
    addi   t2, x0, 1
    jal    x0, main_loop  # 跳回主循环
