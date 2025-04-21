_start:
        # 1. 算术运算测试：计算 1 + 2 = 3
        addi    t0, x0, 1      # t0 = 1
        addi    t1, x0, 2      # t1 = 2
        add     t2, t0, t1     # t2 = 3

        # 2. 访存指令测试：将 3 存入内存，再加载验证
        addi    t3, x0, 1000   # t3 作为内存基地址（假设地址 0x1000 可写）
        sw      t2, 0(t3)      # 将 t2 (3) 存入内存
        lw      t4, 0(t3)      # 从内存加载到 t4
        addi    t5, x0, 3      # t5 存入预期值 3
        bne     t4, t5, error  # 如果 t4 不等于 t5，则出错，跳转到 error

        # 3. 分支指令测试：利用循环指令检测 bne 的正确性
        addi    a0, x0, 5      # a0 = 5，作为循环结束值
        addi    t6, x0, 0      # t6 作为循环计数器，初始化为 0
loop:
        addi    t6, t6, 1      # 循环计数器加 1
        bne     t6, a0, loop   # 若 t6 不等于 5，则继续循环

        # 4. 跳转指令测试：使用 jal 跳转到结束段
        jal     x0, finish     # 无条件跳转到 finish
error:
        j       error          # 错误时陷入死循环
finish:
        nop                    # 正常运行时执行 nop，结束仿真
