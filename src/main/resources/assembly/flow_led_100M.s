    .text
    .globl _start

_start:
    # 将 GPIO 输出地址 0x10000000 装载到 t0
    # LUI 加载高20位 0x10000 得 0x10000<<12 = 0x10000000
    lui    t0, 0x10000

    # 初始化 LED 模式，设置 t2 为 0x01 (最低位亮)
    addi   t2, x0, 1

main_loop:
    # 将 t2 的低 8 位数据写入 GPIO (使用 SW 存储一个字节）
    sw     t2, 0(t0)

    # 延时循环：延时计数器设为 10,000,000
    # 10,000,000 的16进制表示为 0x989680
    # 用 LUI/ADDI 构造 0x989680:
    #   LUI t3, 0x989  得到 t3 = 0x989 << 12 = 0x989000
    #   addi t3, t3, 0x680 得到完整的 0x989680
    lui    t3, 0x989      # t3 = 0x989000
    addi   t3, t3, 0x680  # t3 = 0x989680 (即 10,000,000)

delay_loop:
    addi   t3, t3, -1     # 延时计数器减1
    bnez   t3, delay_loop # 未归零则继续延时

    # 更新 LED 模式：如果当前是 0x80 则归位到 0x01, 否则左移一位
    addi   t4, x0, 0x80   # t4 = 0x80
    beq    t2, t4, reset_led
    slli   t2, t2, 1      # 左移一位
    jal    x0, main_loop

reset_led:
    addi   t2, x0, 1      # 重置为 0x01
    jal    x0, main_loop
