# 题库题单（本次新增 70 题）

生成时间：2026-10-01 14:21　新增题目数：70

所有判题期望输出均由 `tools/gen-problems/GenerateProblems.java` 真实编译并运行每题参考解的 stdout 推导得出，保证题面、示例、判题数据与参考解自洽；并抽样 6 道用参考解走真实判题链路验证为 AC。

| # | slug | 题目 | 难度 | 标签 |
|---|---|---|---|---|
| 1 | `remove-element` | 移除元素 | EASY | array/two-pointers |
| 2 | `remove-duplicates-from-sorted-array` | 删除有序数组中的重复项 | EASY | array/two-pointers |
| 3 | `squares-of-a-sorted-array` | 有序数组的平方 | EASY | array/two-pointers |
| 4 | `running-sum-of-1d-array` | 一维数组的动态和 | EASY | array/prefix-sum |
| 5 | `find-pivot-index` | 寻找数组的中心下标 | EASY | array/prefix-sum |
| 6 | `reverse-string` | 反转字符串 | EASY | string/two-pointers |
| 7 | `sort-array-by-parity` | 按奇偶排序数组 | EASY | array/two-pointers |
| 8 | `duplicate-zeros` | 复写零 | EASY | array/two-pointers |
| 9 | `rotate-array` | 轮转数组 | MEDIUM | array/two-pointers |
| 10 | `container-with-most-water` | 盛最多水的容器 | MEDIUM | array/two-pointers/greedy |
| 11 | `subarray-sum-equals-k` | 和为 K 的子数组 | MEDIUM | array/hash-table/prefix-sum |
| 12 | `sort-colors` | 颜色分类 | MEDIUM | array/two-pointers/sorting |
| 13 | `maximum-nesting-depth-of-the-parentheses` | 括号的最大嵌套深度 | EASY | string/stack |
| 14 | `valid-palindrome` | 验证回文串 | EASY | string/two-pointers |
| 15 | `remove-all-adjacent-duplicates-in-string` | 删除字符串中的所有相邻重复项 | EASY | string/stack |
| 16 | `baseball-game` | 棒球比赛 | EASY | stack/simulation |
| 17 | `number-of-recent-calls` | 最近的请求次数 | EASY | queue/simulation |
| 18 | `length-of-last-word` | 最后一个单词的长度 | EASY | string |
| 19 | `evaluate-reverse-polish-notation` | 逆波兰表达式求值 | MEDIUM | stack/math |
| 20 | `decode-string` | 字符串解码 | MEDIUM | stack/string |
| 21 | `simplify-path` | 简化路径 | MEDIUM | stack/string |
| 22 | `daily-temperatures` | 每日温度 | MEDIUM | stack/array |
| 23 | `remove-k-digits` | 移掉 K 位数字 | MEDIUM | stack/greedy/string |
| 24 | `sliding-window-maximum` | 滑动窗口最大值 | MEDIUM | queue/sliding-window/array |
| 25 | `search-insert-position` | 搜索插入位置 | EASY | binary-search/array |
| 26 | `integer-square-root` | 整数平方根 | EASY | binary-search/math |
| 27 | `array-partition` | 数组拆分 | EASY | greedy/sorting/array |
| 28 | `assign-cookies` | 分发饼干 | EASY | greedy/sorting/two-pointers |
| 29 | `can-place-flowers` | 种花问题 | EASY | greedy/array |
| 30 | `search-in-rotated-sorted-array` | 搜索旋转排序数组 | MEDIUM | binary-search/array |
| 31 | `find-minimum-in-rotated-sorted-array` | 寻找旋转排序数组中的最小值 | MEDIUM | binary-search/array |
| 32 | `find-first-and-last-position-in-sorted-array` | 在排序数组中查找元素的第一个和最后一个位置 | MEDIUM | binary-search/array |
| 33 | `non-overlapping-intervals` | 无重叠区间 | MEDIUM | greedy/sorting/array |
| 34 | `jump-game` | 跳跃游戏 | MEDIUM | greedy/array |
| 35 | `boats-to-save-people` | 救生艇 | MEDIUM | greedy/sorting/two-pointers |
| 36 | `split-array-largest-sum` | 分割数组的最大值 | HARD | binary-search/greedy/array |
| 37 | `pascal-triangle` | 杨辉三角 | EASY | array/dynamic-programming |
| 38 | `power-of-two` | 2 的幂 | EASY | bit-manipulation/math |
| 39 | `number-of-one-bits` | 位 1 的个数 | EASY | bit-manipulation |
| 40 | `single-number` | 只出现一次的数字 | EASY | bit-manipulation/array |
| 41 | `longest-increasing-subsequence` | 最长递增子序列 | MEDIUM | array/dynamic-programming |
| 42 | `perfect-squares` | 完全平方数 | MEDIUM | dynamic-programming/math |
| 43 | `partition-equal-subset-sum` | 分割等和子集 | MEDIUM | array/dynamic-programming |
| 44 | `decode-ways` | 解码方法 | MEDIUM | dynamic-programming/string |
| 45 | `letter-combinations-of-a-phone-number` | 电话号码的字母组合 | MEDIUM | string/recursion/hash-table |
| 46 | `subsets` | 子集 | MEDIUM | array/recursion/bit-manipulation |
| 47 | `bitwise-and-of-numbers-range` | 数字范围按位与 | MEDIUM | bit-manipulation |
| 48 | `edit-distance` | 编辑距离 | HARD | string/dynamic-programming |
| 49 | `maximum-depth-of-binary-tree` | 二叉树的最大深度 | EASY | tree/dfs/recursion |
| 50 | `middle-of-linked-list` | 链表的中间结点 | EASY | linked-list/two-pointers |
| 51 | `find-if-path-exists-in-graph` | 寻找图中是否存在路径 | EASY | graph/bfs/dfs |
| 52 | `binary-tree-level-order-traversal` | 二叉树的层序遍历 | MEDIUM | tree/bfs/queue |
| 53 | `binary-tree-right-side-view` | 二叉树的右视图 | MEDIUM | tree/bfs/dfs |
| 54 | `validate-binary-search-tree` | 验证二叉搜索树 | MEDIUM | tree/dfs/recursion |
| 55 | `add-two-numbers` | 两数相加 | MEDIUM | linked-list/math/recursion |
| 56 | `rotate-list` | 旋转链表 | MEDIUM | linked-list/two-pointers |
| 57 | `max-area-of-island` | 岛屿的最大面积 | MEDIUM | dfs/bfs/matrix |
| 58 | `rotting-oranges` | 腐烂的橘子 | MEDIUM | graph/bfs/matrix |
| 59 | `word-ladder` | 单词接龙 | HARD | graph/bfs/string |
| 60 | `matrix-diagonal-sum` | 矩阵对角线元素和 | EASY | matrix/array |
| 61 | `transpose-matrix` | 矩阵转置 | EASY | matrix/simulation |
| 62 | `count-elements-with-maximum-frequency` | 出现次数最多的元素计数 | EASY | counting/array/hash-table |
| 63 | `robot-return-to-origin` | 机器人是否回到原点 | EASY | simulation/string |
| 64 | `last-stone-weight` | 最后一块石头的重量 | EASY | heap/array |
| 65 | `spiral-matrix` | 螺旋顺序读取矩阵 | MEDIUM | matrix/simulation/array |
| 66 | `count-and-say` | 外观数列 | MEDIUM | string/simulation |
| 67 | `kth-largest-element-in-an-array` | 数组中第 k 大的元素 | MEDIUM | heap/sorting/array |
| 68 | `top-k-frequent-elements` | 出现频率最高的 k 个元素 | MEDIUM | counting/heap/hash-table |
| 69 | `reduce-array-size-to-the-half` | 将数组大小减半的最少集合 | MEDIUM | counting/heap/sorting |
| 70 | `merge-stones-minimum-total-cost` | 合并石子的最小总代价 | HARD | heap/greedy |

**难度分布**：EASY 31，HARD 4，MEDIUM 35

**标签分布**：array 32，string 14，two-pointers 14，greedy 10，stack 8，sorting 7，dynamic-programming 6，binary-search 6，simulation 6，bfs 6，dfs 5，bit-manipulation 5，matrix 5，recursion 5，math 5，heap 5，hash-table 4，tree 4，counting 3，prefix-sum 3，queue 3，linked-list 3，graph 3，sliding-window 1
