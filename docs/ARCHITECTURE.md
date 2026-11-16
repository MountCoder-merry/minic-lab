# 架构

```text
Swing / CLI / JUnit
       ↓
CompilerService → Lexer → Parser → Semantic → Ir → Bytecode
                                                   ↓
                                              VirtualMachine

Automata（Thompson / 子集 / 最小化）
Grammar（Nullable / FIRST / FOLLOW / LL(1) / 变换）
Lr（LR(0) / SLR(1) / LR(1) / 轨迹）
```

所有核心模块不依赖 Swing。CompilationResult 保留各阶段产物与诊断；编译失败即停止后续阶段。节点采用 record，语义属性用 IdentityHashMap，避免结构相同节点错误共享属性。

GUI 在 EDT 更新，编译、算法构造和虚拟机连续运行在 SwingWorker 中执行。编译结果关联编辑修订号；旧任务的结果不覆盖新文本。VM 运行标志跨线程可见，状态快照为不可变数据。

界面以浅灰白 #F4F7FB、白 #FFFFFF、深蓝 #17324D、蓝 #2864DC、青 #078477、错误红 #BE3455 构成。正文使用 Java Dialog 逻辑字体，代码使用带中文回退的 Java Monospaced 逻辑字体，标题使用 Segoe UI。具有实际顺序意义的编译阶段条是视觉主线；实验图采用 Java2D 绘制，支持拖动与缩放。

测试分为编译端到端、诊断与控制流、VM 资源/优化等价、算法已知结果、Swing 真实窗口交互。没有网络服务或数据库；文件读写使用 UTF-8。
