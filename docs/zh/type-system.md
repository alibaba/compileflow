# 流程数据类型与转换

流程定义通过 Java 类型声明变量和动作参数。CompileFlow 会在生成源码前校验类型声明，并且只执行一组
范围明确、结果确定的运行时转换。非法或含糊的值会直接失败，不会静默截断、猜测 locale 或回退默认值。

## 类型声明

类型声明由大小写敏感的 Java 类型名、可选泛型参数和数组后缀组成：

```text
java.lang.String
int[]
java.util.List<java.lang.String>
java.util.List<int[]>
java.util.Map.Entry<java.lang.String, java.lang.Integer>
java.util.Map<java.lang.String, java.util.List<java.lang.Integer[]>>[][]
```

`java.lang` 之外的类型应使用全限定名。内建简写只包括 `String`、Java primitive 和标准 `java.lang`
包装类型。每个泛型参数类型都必须对编译 ClassLoader 可见，泛型参数数量必须与目标类声明一致。后缀格式错误、类不存在、primitive
标量泛型参数（数组是合法引用类型）、大小写错误以及超过 JVM 上限的数组维度都会使 preflight 失败。成员类在流程定义和生成源码中使用
canonical name（例如
`java.util.Map.Entry`）；加载时才转换为 JVM 所需的 binary name。

Java 会在运行时擦除泛型参数。CompileFlow 在声明和生成源码层保留泛型，让 `javac` 检查静态可赋值路径；从
`Map<String, Object>`、脚本或子流程结果等动态边界进入时，只能按原始类检查。引擎不会隐式遍历、转换或校验集合元素，执行上下文提供的
`List<T>` 或 `Map<K,V>` 内容仍由应用负责。

这些 Java 类型声明规则不代表 Durable 持久化支持。Durable Run 状态还必须满足 Kernel 固定的可移植 value schema 与编码契约；
仅在 JVM 类加载器中可见并不足够，也没有应用载荷编解码 SPI。

## 运行时转换

`null` 保持为 `null`；已经可以赋给目标类型的值原样返回。其余支持的转换如下：

| 目标类型                                             | 可接受来源                           | 规则                                                                                  |
| ---------------------------------------------------- | ------------------------------------ | ------------------------------------------------------------------------------------- |
| 整数类型和 `BigInteger`                              | `Number` 或十进制文本                | 必须无损且不越界；小数、溢出、分隔符和非有限数值均失败                                |
| `float` / `double`                                   | `Number` 或十进制文本                | 结果必须是有限值                                                                      |
| `BigDecimal`                                         | `Number` 或十进制文本                | 只接受与 locale 无关的 Java 十进制语法                                                |
| `boolean`                                            | `Boolean` 或 `true` / `false` 文本   | 不猜测数字、yes/no 或 on/off                                                          |
| `char`                                               | `Character` 或一个 UTF-16 code unit  | 空文本和多字符文本失败                                                                |
| `String`                                             | 任意非空对象                         | 使用对象的字符串表示                                                                  |
| `LocalDate`、`LocalTime`、`LocalDateTime`、`Instant` | 适用的 JDBC 时间值或 ISO-8601 文本   | 严格 ISO 解析，非法日历值失败                                                         |
| JDBC `Date`、`Time`、`Timestamp`                     | 对应的 `java.time` 值或严格 ISO 文本 | `Time` 拒绝无法保留的小数秒；`Timestamp` 接受本地时间或绝对时间点                     |
| `java.util.Date`                                     | `Instant` 或 ISO instant 文本        | 必须提供时区或 offset，且只能使用毫秒精度；本地日期时间文本和会丢失的纳秒精度均被拒绝 |

转换不读取宿主 locale 或默认时区。转换异常只给出来源类型和目标类型，不回显可能包含凭据或个人信息的运行时值。

## 默认值

变量默认值在生成 Java 源码时完成校验：

- primitive、包装类型、字符串、`BigInteger`、`BigDecimal`、`java.time` 和 JDBC 时间字面量均严格解析。
- `DataTypes` 直接处理 primitive 时，未提供默认值使用 Java 零值；流程变量声明中的 primitive 别名会
  规范化为对应包装类型，因此流程状态保持可空语义，未提供默认值为 `null`。
- 输入 Map 缺少对应 key 时保留声明的默认值；key 存在时覆盖默认值。因此显式 `null` 与未传值语义不同。
- 空 `String` 是有效的空字符串默认值；其他类型的空白默认值视为未提供。
- 所有默认值都是数据字面量，`@` 没有特殊语义。例如 String 默认值 `@name` 会原样保留；同一文本用于不兼容的数值或时间
  类型时会校验失败。
- 不支持的对象默认值会使 preflight 失败；应改为通过输入 context 传值。

新流程优先使用 `java.time`。只有在集成边界确实要求旧语义时才使用 `java.util.Date` 和 `java.sql` 类型。
