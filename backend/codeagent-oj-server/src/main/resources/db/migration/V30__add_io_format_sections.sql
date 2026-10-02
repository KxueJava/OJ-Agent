-- 给最早的 31 道题补「输入格式 / 输出格式」章节。
--
-- 背景：题库里 101 道题中有 70 道（V22 那批）自带这两节，最早的 31 道只有一句题面 + 示例，
-- 使用者和 Agent 都无法判断 stdin 的确切格式（例如多参数用分号还是逗号、数组怎么写、空结果怎么输出），
-- 这直接导致"照着题面写却 WA"（用户已在二分查找等题上踩到）。
--
-- 写法：严格以**判题用例的真实输入/输出**为准（`test_cases` 里 PUBLIC 的第一条），
-- 而不是照抄题面里可能过时的描述 —— 例如 merge-sorted-array 题面写"输入为两行数组"，
-- 实际判题输入是一行 `[1,2,3,0,0],[2,5,6]`，本次一并订正题面。
--
-- 幂等：每条都带 `statement_md NOT LIKE '%输入格式%'`，重复执行不会重复追加。

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，形如 `[2,7,11,15];9`，分号前是一个整数数组（写成 `[2,7,11,15]`，元素之间无空格），分号后是目标值 `target`；数组长度不超过 10^4。\n输出格式：一行，两个下标，写成 `[0,1]` 的形式（元素之间无空格），按从小到大排列。')
 WHERE p.slug = 'two-sum' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，一个只由 `(`、`)`、`{`、`}`、`[`、`]` 组成的字符串，不含空格。\n输出格式：一行，`true` 或 `false`。')
 WHERE p.slug = 'valid-parentheses' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，一个小写字母字符串，不含空格。\n输出格式：一行，一个整数，表示不含重复字符的最长子串长度。')
 WHERE p.slug = 'longest-substring-without-repeating-characters' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，一个整数数组，写成 `[-1,0,1,2,-1,-4]` 的形式，元素之间无空格。\n输出格式：一行，所有和为 0 且不重复的三元组，写成 `[[-1,-1,2],[-1,0,1]]` 的形式（元素之间无空格）；三元组之间的顺序不限。')
 WHERE p.slug = 'three-sum' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，形如 `[-1,0,3,5,9,12];9`，分号前是升序排列的整数数组（元素之间无空格），分号后是目标值 `target`。\n输出格式：一行，目标值所在下标；不存在时输出 `-1`。')
 WHERE p.slug = 'binary-search' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，链表各节点的值，写成 `[1,2,3,4,5]` 的形式（元素之间无空格，按从头到尾的顺序）。\n输出格式：一行，反转后的链表，写成 `[5,4,3,2,1]` 的形式；空链表输出 `[]`。')
 WHERE p.slug = 'reverse-linked-list' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，每天的价格数组，写成 `[7,1,5,3,6,4]` 的形式（元素之间无空格）。\n输出格式：一行，一个整数，表示能获得的最大利润；无法获利时输出 `0`。')
 WHERE p.slug = 'best-time-to-buy-and-sell-stock' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，一个整数 `n`，表示台阶数。\n输出格式：一行，一个整数，表示到达第 `n` 阶的方法数。')
 WHERE p.slug = 'climbing-stairs' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，一个整数数组，写成 `[-2,1,-3,4,-1,2,1,-5,4]` 的形式（元素之间无空格）。\n输出格式：一行，一个整数，表示具有最大和的连续子数组之和。')
 WHERE p.slug = 'maximum-subarray' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，一个整数数组，写成 `[1,2,3,4]` 的形式（元素之间无空格）。\n输出格式：一行，结果数组，写成 `[24,12,8,6]` 的形式（元素之间无空格），第 i 个数是除 `nums[i]` 外其余所有元素的乘积。')
 WHERE p.slug = 'product-of-array-except-self' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，一个整数 `x`。\n输出格式：一行，`true` 或 `false`。')
 WHERE p.slug = 'palindrome-number' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，一个罗马数字字符串，只包含 `I`、`V`、`X`、`L`、`C`、`D`、`M` 七个字符（大写、不含空格）。\n输出格式：一行，一个整数，表示对应的数值。')
 WHERE p.slug = 'roman-to-integer' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，若干个小写字母字符串，用英文逗号分隔且不含空格（如 `dog,racecar,car`）。\n输出格式：一行，最长公共前缀；若不存在公共前缀则输出**空行**。')
 WHERE p.slug = 'longest-common-prefix' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，一个整数数组，写成 `[0,1,0,3,12]` 的形式（元素之间无空格）。\n输出格式：一行，移动后的数组，写成 `[1,3,12,0,0]` 的形式（元素之间无空格）。')
 WHERE p.slug = 'move-zeroes' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，一个整数数组，写成 `[1,2,3,1]` 的形式（元素之间无空格）。\n输出格式：一行，`true` 或 `false`。')
 WHERE p.slug = 'contains-duplicate' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，一个整数数组，写成 `[3,0,1]` 的形式（元素之间无空格），其中包含 `[0,n]` 范围内 n 个互不相同的数。\n输出格式：一行，一个整数，表示缺失的那个数。')
 WHERE p.slug = 'missing-number' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，一个整数数组，写成 `[3,2,3]` 的形式（元素之间无空格）。\n输出格式：一行，一个整数，表示出现次数超过 `n/2` 的多数元素。')
 WHERE p.slug = 'majority-element' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，一个小写字母字符串，不含空格。\n输出格式：一行，第一个不重复字符的下标；不存在时输出 `-1`。')
 WHERE p.slug = 'first-unique-character' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，一个正整数 `n`。\n输出格式：一行，`true` 或 `false`。')
 WHERE p.slug = 'happy-number' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

-- 同时订正题面里与实现不符的一句："输入为两行数组" → 实际是一行。
UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(
    REPLACE(v.statement_md, '输入为两行数组。', ''),
'\n\n输入格式：**一行**，两个数组用英文逗号分隔（如 `[1,2,3,0,0],[2,5,6]`），元素之间无空格；第一个是 `nums1`（末尾的 0 是占位，合并时会被覆盖），第二个是 `nums2`。\n输出格式：一行，合并后的数组，写成 `[1,2,2,3,5,6]` 的形式（元素之间无空格）。')
 WHERE p.slug = 'merge-sorted-array' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，若干个小写字母字符串，用英文逗号分隔且不含空格（如 `eat,tea,tan,ate,nat,bat`）。\n输出格式：一行，分组结果，写成 `[[eat,tea,ate],[tan,nat],[bat]]` 的形式（元素之间无空格）；组与组的顺序、组内元素的顺序均不限。')
 WHERE p.slug = 'group-anagrams' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，二维数组，写成 `[[1,4],[4,5]]` 的形式（元素之间无空格），每个 `[l,r]` 表示一个区间。\n输出格式：一行，合并后的区间集合，写成 `[[1,5]]` 的形式（元素之间无空格），按区间起点升序排列。')
 WHERE p.slug = 'merge-intervals' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，一个整数 `n`，表示括号对数。\n输出格式：一行，所有有效括号组合，写成 `[()]` 的形式（多个组合之间用英文逗号分隔、无空格）；顺序不限。')
 WHERE p.slug = 'generate-parentheses' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，若干操作，用英文逗号分隔（如 `push 2,push 1,getMin,pop,top`）；操作包括 `push x`、`pop`、`top`、`getMin`，不含空格。\n输出格式：一行，把每个 `pop`、`top`、`getMin` 的结果按出现顺序用英文逗号分隔输出（无空格）。')
 WHERE p.slug = 'min-stack' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，每间房屋金额组成的整数数组，写成 `[2,7,9,3,1]` 的形式（元素之间无空格）。\n输出格式：一行，一个整数，表示不触发警报时能偷到的最高金额。')
 WHERE p.slug = 'house-robber' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，形如 `[1,2,5],11`，逗号前是硬币面额数组（元素之间无空格），逗号后是总金额。\n输出格式：一行，凑成总金额所需的最少硬币数；无法凑出时输出 `-1`。')
 WHERE p.slug = 'coin-change' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，两个整数 `m` 和 `n`，用英文逗号分隔（如 `3,2`），表示 m 行 n 列的网格。\n输出格式：一行，一个整数，表示从左上角到右下角的路径数量。')
 WHERE p.slug = 'unique-paths' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，二维网格，写成 `[[1,1,0,0,0],[1,1,0,0,0],[0,0,1,0,0],[0,0,0,1,1]]` 的形式（元素之间无空格），`1` 表示陆地、`0` 表示水。\n输出格式：一行，一个整数，表示岛屿数量。')
 WHERE p.slug = 'number-of-islands' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，形如 `2,[[1,0]]`，逗号前是课程数 `numCourses`，逗号后是先修关系二维数组（每项 `[a,b]` 表示学 a 之前必须先学 b；元素之间无空格）。\n输出格式：一行，`true` 或 `false`，表示能否修完所有课程。')
 WHERE p.slug = 'course-schedule' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';

UPDATE problem_versions v JOIN problems p ON p.id = v.problem_id SET v.statement_md = CONCAT(v.statement_md,
'\n\n输入格式：一行，不含重复数字的整数数组，写成 `[1,2,3]` 的形式（元素之间无空格）。\n输出格式：一行，所有全排列，写成 `[[1,2,3],[1,3,2],[2,1,3],[2,3,1],[3,1,2],[3,2,1]]` 的形式（元素之间无空格）；顺序不限。')
 WHERE p.slug = 'permutations' AND v.status = 'PUBLISHED' AND v.statement_md NOT LIKE '%输入格式%';
