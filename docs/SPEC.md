# MiniC 语言与执行规格

源码为 UTF-8，标识符为 `[A-Za-z_][A-Za-z_0-9]*`，大小写敏感。支持十进制 32 位有符号整数及 `true/false`，`//` 和非嵌套 `/* */` 注释。

## EBNF
```text
program     = function, { function }, EOF ;
function    = type, identifier, "(", [ parameters ], ")", block ;
type        = "int" | "bool" | "void" ;
parameters  = valueType, identifier, { ",", valueType, identifier } ;
valueType   = "int" | "bool" ;
block       = "{", { statement }, "}" ;
statement   = block | declaration | assignment | if | while
            | "break", ";" | "continue", ";" | return | call, ";" ;
declaration = valueType, identifier, [ "=", expression ], ";" ;
assignment  = identifier, "=", expression, ";" ;
if          = "if", "(", expression, ")", block, [ "else", block ] ;
while       = "while", "(", expression, ")", block ;
return      = "return", [ expression ], ";" ;
expression  = or ;
or          = and, { "||", and } ;
and         = equality, { "&&", equality } ;
equality    = comparison, { ("==" | "!="), comparison } ;
comparison  = sum, { ("<" | "<=" | ">" | ">="), sum } ;
sum         = product, { ("+" | "-"), product } ;
product     = unary, { ("*" | "/" | "%"), unary } ;
unary       = ("!" | "-" | "+"), unary | primary ;
primary     = integer | "true" | "false" | identifier | call
            | "(", expression, ")" ;
call        = identifier, "(", [ expression, { ",", expression } ], ")" ;
```

## 静态与动态语义
- 入口必须为 `int main()`；仅顶层函数，允许前向调用及相互递归。`read`、`write` 为保留内置函数名。
- 参数、局部变量不可为 void。参数与函数最外层局部变量共享作用域；嵌套块可遮蔽外层变量，同一作用域不可重复声明。
- 赋值、参数、返回值类型严格一致。条件必须 bool；算术/大小比较使用 int，逻辑使用 bool，相等比较允许同类型 int 或 bool。
- 局部变量可无初始化声明，但每次读取前必须确定赋值。分支按仍能正常到达后续语句的路径取交集；循环保守视为可能执行零次。不使用常量条件证明必定返回或必定赋值。
- 非 void 函数不能正常落到函数末尾；void 函数允许隐式返回。调用参数从左到右求值，函数返回值允许作为调用语句丢弃。
- int 运算按 Java int 回绕，除法向零截断；除零/余零在运行时报告。`-2147483648` 合法，正的 `2147483648` 非法。
- `&&`/`||`短路；read() 返回 int，write(int/bool) 返回 void，布尔输出 true/false，每次 write 换行。
- 编译错误不产生可执行目标。诊断包含阶段、行、列、源码偏移；语法恢复点为分号、右大括号和下一函数起点。

## 中间表示与目标机
四元式 `(op, arg1, arg2, result)` 使用独立的 `v编号`、`t编号`、`L编号`；每个函数独立存储。优化仅在基本块内部进行，调用和 I/O 为屏障。

目标机使用 PUSH、LOAD、STORE、POP、NEG、NOT、算术/比较指令、JMP、JZ、CALL、RET、READ、WRITE。每个函数有独立局部槽与操作数栈；调用传值并创建调用帧，RET 把返回值压到调用者栈。指令保存源码范围。字节码导出为可读文本；本版通过编译结果对象加载执行，不提供外部字节码文本加载器。

默认累计指令上限 1,000,000、调用深度 1024；用户可暂停、单步或停止，暂停不重置预算。输入不足与资源超限为可定位的运行错误。停止后需重新装载程序才能重跑；GUI 再次点击运行会自动重新装载，无需重新打开文件。

## 实验输入
正则式支持连接（隐式或 `·`）、`|`、`*`、括号、ε、∅、反斜杠转义。空正则视为 ε；未转义空白忽略；字面空白需转义。不支持字符类和 +/? 运算符。

文法逐行 `A -> α | β`，所有符号以空格分隔，ε 单独表示空产生式。首行左部为开始符，所有左部符号为非终结符，其余为终结符；# 保留为 EOF。输入串同样以空格分隔，留空表示空串，不填写 #。文法变换只自动消除直接左递归与提取左公因子；间接左递归单独检测。

构造上限：正则 NFA 2000 状态，DFA/LR 各 1000 状态，文法 200 产生式；分析轨迹 10000 步。达到限制或取消后明确结束，不返回伪造的完整结果。
