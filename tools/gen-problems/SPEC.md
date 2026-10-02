# 题库批量生成规范（v1）

目标：为 CodeAgent OJ 生成 **70 道新题**，与现有 30 道风格一致，且**每道题带一份参考解**——
测试用例的期望输出由脚本真实编译运行参考解得出，因此题面、示例、判题数据必须彼此自洽。

## 0. 先读这两个文件，摸清现状（必做）

- `backend/codeagent-oj-server/src/main/resources/db/migration/V3__create_problem_catalog.sql`
- `backend/codeagent-oj-server/src/main/resources/db/migration/V10__seed_more_problems.sql`

从中提取**现有全部 slug**（约 30 个），新题不得与它们重复；同时观察题面措辞、约束写法、Java 模板风格。

## 1. 输出

写一个 JSON 文件（用 write 工具），路径由任务指定，例如 `tools/gen-problems/batch-1.json`：

```json
{
  "batch": 1,
  "problems": [
    {
      "slug": "reverse-integer",
      "title": "整数反转",
      "difficulty": "EASY",
      "tags": ["math"],
      "statement": "输入格式：一行，一个 32 位有符号整数 `x`。\n输出格式：一行，`x` 数字部分反转后的整数；若反转结果超出 32 位有符号整数范围，输出 `0`。",
      "constraints": "- `-2^31 <= x <= 2^31 - 1`",
      "examples": [{ "input": "123", "output": "321" }],
      "tests": [
        { "input": "123", "visibility": "PUBLIC" },
        { "input": "-2147483648", "visibility": "HIDDEN" }
      ],
      "solution": "import java.io.*;\n\npublic class Main {\n    public static void main(String[] args) throws Exception {\n        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));\n        String line = br.readLine();\n        if (line == null) return;\n        int x = Integer.parseInt(line.trim());\n        long reversed = 0;\n        long t = Math.abs((long) x);\n        while (t != 0) { reversed = reversed * 10 + t % 10; t /= 10; }\n        if (x < 0) reversed = -reversed;\n        if (reversed > Integer.MAX_VALUE || reversed < Integer.MIN_VALUE) System.out.println(0);\n        else System.out.println((int) reversed);\n    }\n}\n"
    }
  ]
}
```

## 2. 字段规则

| 字段 | 规则 |
|---|---|
| `slug` | kebab-case，只含小写字母/数字/连字符，全局唯一，不与现有 30 个重复 |
| `title` | 中文题目名，简短 |
| `difficulty` | 只能 `EASY` / `MEDIUM` / `HARD`，按任务指定的配比 |
| `tags` | 从下面的**标签词表**里选 1–3 个（用 slug，如 `array`）；不得自创 |
| `statement` | 中文。**必须显式包含「输入格式：」与「输出格式：」两行**，逐字说明怎么读、怎么打印 |
| `constraints` | 中文 Markdown 列表，形如 `- \`1 <= n <= 10^5\``，写清数据范围 |
| `examples` | 1–2 条，`input` 必须是真实的 stdin 内容（可含 `\n`），`output` 按你的预期填写（脚本会核对，不一致会被标记） |
| `tests` | 2–3 条。**第一条必须是 `PUBLIC`**，其余 `HIDDEN`。hidden 要覆盖边界（负数/空/极值/重复） |
| `solution` | 完整可编译的 Java 21 参考解，见第 3 节 |

## 3. 参考解硬性要求

1. 类名必须是 `Main`，默认包，含 `public static void main(String[] args) throws Exception`。
2. 从 **stdin** 读全部输入，只往 **stdout** 打印答案；**不得**打印提示语、调试信息、多余换行。
3. 只用 JDK 标准库（`java.io.*`、`java.util.*` 等）。
4. 必须在 2 秒 / 256MB 内跑完（数据规模自己控制在 10^5 量级以内）。
5. 该解法会被脚本编译并喂入**每一条 tests 与 examples 的 input**，其 stdout 即成为判题用的期望输出。

## 4. 输入格式约定（题面与用例必须一致）

- 单参数：直接一行。整数/布尔/字符串/数组字面量，例如 `121`、`[2,7,11,15]`、`s = "abcabcbb"`。
- 多参数：**一行内用单个空格分隔**，例如 `[2,7,11,15] 9`、`3 7`（历史数据已在 V33 统一为空格）。
- 数组一律写成 `[1,2,3]`（元素间无空格）；字符串写成 `abc`（不带引号）或 `s = "abc"`，但**同一个题内必须统一**。
- 这些格式**必须逐字写进 `statement` 的「输入格式」行**，让做题的人能照着解析。

## 5. 输出格式约定

- 布尔：`true` / `false`（小写）。
- 数组：`[1,2,3]`；嵌套数组 `[[1,2],[3]]`——**元素之间不留空格**。
- 字符串：直接打印内容，不加引号。
- 数字：十进制整数。
- **禁止浮点数输出**（判题按字符串比对，格式歧义太大）。
- 输出本身也要在 `statement` 的「输出格式」行里写清楚。

## 6. 标签词表

`array` 数组、`hash-table` 哈希表、`string` 字符串、`stack` 栈、`binary-search` 二分查找、`linked-list` 链表、
`two-pointers` 双指针、`dynamic-programming` 动态规划、`math` 数学、`greedy` 贪心、`sorting` 排序、
`bit-manipulation` 位运算、`sliding-window` 滑动窗口、`prefix-sum` 前缀和、`recursion` 递归、`tree` 树、
`graph` 图、`bfs` 广度优先搜索、`dfs` 深度优先搜索、`simulation` 模拟、`matrix` 矩阵、`heap` 堆、
`queue` 队列、`counting` 计数

## 7. 质量红线

- 不得出现:题面说不清、只能猜输入的题；输出格式有歧义的题（如"任意顺序输出"）。
- 不得出现"多解且无法唯一判定"的题；期望输出必须唯一。
- 不得与现有 30 道重题（同题换名也算重复）。
- 参考解必须先读判断 `null`，避免空输入直接 NPE。
- 题面不要出现"提示代码"或直接给算法名（保留难度）。
- 写得像正式题目，不要出现"这道题很简单""参考解如下"之类的话。
