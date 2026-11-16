# 内置示例

| 文件 | 输入 | 预期输出（逐行） |
|---|---|---|
| factorial.mc | 5 | 120 |
| fibonacci.mc | 10 | 55 |
| gcd.mc | 48 18 | 6 |
| sum.mc | 100 | 5050 |
| scope-short-circuit.mc | 空 | 9 / 5 / false / true / 14 |
| nested-loops.mc | 空 | 6 |
| mutual-recursion.mc | 空 | true / false |
| errors.mc | 空 | 编译失败，6 条语义诊断 |

所有正确示例的 main 返回 0。标准整数运算会回绕；本系统用于算法演示，请给递归示例合理的非负小整数。
