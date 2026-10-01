INSERT INTO problems (id, slug, title, difficulty, status) VALUES
 (1101,'palindrome-number','回文数','EASY','PUBLISHED'),
 (1102,'roman-to-integer','罗马数字转整数','EASY','PUBLISHED'),
 (1103,'longest-common-prefix','最长公共前缀','EASY','PUBLISHED'),
 (1104,'move-zeroes','移动零','EASY','PUBLISHED'),
 (1105,'contains-duplicate','存在重复元素','EASY','PUBLISHED'),
 (1106,'missing-number','缺失数字','EASY','PUBLISHED'),
 (1107,'majority-element','多数元素','EASY','PUBLISHED'),
 (1108,'first-unique-character','字符串中的第一个唯一字符','EASY','PUBLISHED'),
 (1109,'happy-number','快乐数','EASY','PUBLISHED'),
 (1110,'merge-sorted-array','合并两个有序数组','EASY','PUBLISHED'),
 (1111,'group-anagrams','字母异位词分组','MEDIUM','PUBLISHED'),
 (1112,'merge-intervals','合并区间','MEDIUM','PUBLISHED'),
 (1113,'generate-parentheses','括号生成','MEDIUM','PUBLISHED'),
 (1114,'min-stack','最小栈','MEDIUM','PUBLISHED'),
 (1115,'house-robber','打家劫舍','MEDIUM','PUBLISHED'),
 (1116,'coin-change','零钱兑换','MEDIUM','PUBLISHED'),
 (1117,'unique-paths','不同路径','MEDIUM','PUBLISHED'),
 (1118,'number-of-islands','岛屿数量','MEDIUM','PUBLISHED'),
 (1119,'course-schedule','课程表','MEDIUM','PUBLISHED'),
 (1120,'permutations','全排列','MEDIUM','PUBLISHED');

INSERT INTO problem_versions (id, problem_id, version_no, status, statement_md, constraints_md, java_template, published_at) VALUES
 (2101,1101,1,'PUBLISHED','给定一个整数 `x`，如果它是从左到右和从右到左读都相同的回文数，返回 true，否则返回 false。输入一行整数，输出 true 或 false。','- `-2^31 <= x <= 2^31 - 1`','import java.io.*;\npublic class Main { public static void main(String[] args) throws Exception { } }',CURRENT_TIMESTAMP),
 (2102,1102,1,'PUBLISHED','给定一个罗马数字字符串，返回它对应的整数。输入只包含 I、V、X、L、C、D、M。','- `1 <= s.length <= 15`','import java.io.*;\npublic class Main { public static void main(String[] args) throws Exception { } }',CURRENT_TIMESTAMP),
 (2103,1103,1,'PUBLISHED','给定一个字符串数组，返回其中最长的公共前缀；如果不存在公共前缀，返回空字符串。输入使用逗号分隔字符串。','- `1 <= strs.length <= 200`','import java.io.*;\npublic class Main { public static void main(String[] args) throws Exception { } }',CURRENT_TIMESTAMP),
 (2104,1104,1,'PUBLISHED','给定整数数组，将所有 0 移到末尾，同时保持非零元素的相对顺序。输出移动后的数组。','- 原数组长度不超过 `10^5`','import java.io.*;\npublic class Main { public static void main(String[] args) throws Exception { } }',CURRENT_TIMESTAMP),
 (2105,1105,1,'PUBLISHED','给定整数数组，如果任意值在数组中至少出现两次，返回 true，否则返回 false。','- `1 <= nums.length <= 10^5`','import java.io.*;\npublic class Main { public static void main(String[] args) throws Exception { } }',CURRENT_TIMESTAMP),
 (2106,1106,1,'PUBLISHED','给定包含 `[0,n]` 中 n 个数的数组，找出数组中没有出现的那个数。','- 数组中的数字互不相同','import java.io.*;\npublic class Main { public static void main(String[] args) throws Exception { } }',CURRENT_TIMESTAMP),
 (2107,1107,1,'PUBLISHED','给定一个大小为 n 的数组，返回其中出现次数大于 `n/2` 的多数元素。','- 多数元素一定存在','import java.io.*;\npublic class Main { public static void main(String[] args) throws Exception { } }',CURRENT_TIMESTAMP),
 (2108,1108,1,'PUBLISHED','给定字符串，找出其中第一个不重复字符并返回它的下标；不存在则返回 -1。','- 字符串只包含小写英文字母','import java.io.*;\npublic class Main { public static void main(String[] args) throws Exception { } }',CURRENT_TIMESTAMP),
 (2109,1109,1,'PUBLISHED','编写算法判断一个正整数是不是快乐数。快乐数会不断替换为各位数字平方和，最终变成 1。','- `1 <= n <= 2^31 - 1`','import java.io.*;\npublic class Main { public static void main(String[] args) throws Exception { } }',CURRENT_TIMESTAMP),
 (2110,1110,1,'PUBLISHED','给定两个非递减整数数组 nums1 和 nums2，将 nums2 合并到 nums1 中并保持非递减顺序。输入为两行数组。','- 合并后长度不超过 `200`','import java.io.*;\npublic class Main { public static void main(String[] args) throws Exception { } }',CURRENT_TIMESTAMP),
 (2111,1111,1,'PUBLISHED','给定字符串数组，将字母异位词组合在一起。输出分组结果的任意顺序均可。','- 字符串只含小写字母','import java.io.*;\npublic class Main { public static void main(String[] args) throws Exception { } }',CURRENT_TIMESTAMP),
 (2112,1112,1,'PUBLISHED','以二维数组表示若干区间，合并所有重叠区间并返回不重叠的区间集合。','- `1 <= intervals.length <= 10^4`','import java.io.*;\npublic class Main { public static void main(String[] args) throws Exception { } }',CURRENT_TIMESTAMP),
 (2113,1113,1,'PUBLISHED','给定整数 n，生成所有由 n 对括号组成的有效括号组合。输出顺序不限。','- `1 <= n <= 8`','import java.io.*;\npublic class Main { public static void main(String[] args) throws Exception { } }',CURRENT_TIMESTAMP),
 (2114,1114,1,'PUBLISHED','设计一个栈，支持 push、pop、top 和在常数时间内检索最小元素。输入为操作序列，输出每次查询结果。','- 所有操作均合法','import java.io.*;\npublic class Main { public static void main(String[] args) throws Exception { } }',CURRENT_TIMESTAMP),
 (2115,1115,1,'PUBLISHED','你要打劫一排房屋，不能打劫相邻房屋。给定每间房的金额，返回不触发警报时能偷到的最高金额。','- `1 <= nums.length <= 100`','import java.io.*;\npublic class Main { public static void main(String[] args) throws Exception { } }',CURRENT_TIMESTAMP),
 (2116,1116,1,'PUBLISHED','给定不同面额的硬币和总金额，计算凑成总金额所需的最少硬币数；无法凑出返回 -1。','- `0 <= amount <= 10^4`','import java.io.*;\npublic class Main { public static void main(String[] args) throws Exception { } }',CURRENT_TIMESTAMP),
 (2117,1117,1,'PUBLISHED','一个机器人位于 m x n 网格左上角，每次只能向右或向下，计算到达右下角的路径数量。','- `1 <= m,n <= 100`','import java.io.*;\npublic class Main { public static void main(String[] args) throws Exception { } }',CURRENT_TIMESTAMP),
 (2118,1118,1,'PUBLISHED','给定由 1 和 0 组成的二维网格，计算岛屿数量。岛屿由水平或垂直相邻的陆地连接形成。','- 网格行列数乘积不超过 `10^5`','import java.io.*;\npublic class Main { public static void main(String[] args) throws Exception { } }',CURRENT_TIMESTAMP),
 (2119,1119,1,'PUBLISHED','课程共有 numCourses 门，先修关系给出学习顺序。判断是否可能完成所有课程。','- `0 <= prerequisites.length <= 5000`','import java.io.*;\npublic class Main { public static void main(String[] args) throws Exception { } }',CURRENT_TIMESTAMP),
 (2120,1120,1,'PUBLISHED','给定不含重复数字的整数数组，返回其所有可能的全排列。输出顺序不限。','- `1 <= nums.length <= 6`','import java.io.*;\npublic class Main { public static void main(String[] args) throws Exception { } }',CURRENT_TIMESTAMP);

UPDATE problems SET published_version_id = id + 1000 WHERE id BETWEEN 1101 AND 1120;
INSERT INTO problem_tags (problem_id, tag_id) VALUES
 (1101,103),(1102,103),(1103,103),(1104,101),(1105,101),(1105,102),(1106,101),(1107,101),(1108,103),(1109,101),
 (1110,101),(1111,103),(1111,102),(1112,101),(1112,107),(1113,104),(1113,108),(1114,104),(1115,108),(1116,108),
 (1117,108),(1118,101),(1118,108),(1119,108),(1120,101),(1120,108);

INSERT INTO examples (id, problem_version_id, display_order, input_text, output_text, explanation_md) VALUES
 (3101,2101,1,'121','true',NULL),(3102,2102,1,'MCMXCIV','1994',NULL),(3103,2103,1,'flower,flow,flight','fl',NULL),(3104,2104,1,'[0,1,0,3,12]','[1,3,12,0,0]',NULL),(3105,2105,1,'[1,2,3,1]','true',NULL),(3106,2106,1,'[3,0,1]','2',NULL),(3107,2107,1,'[2,2,1,1,1,2,2]','2',NULL),(3108,2108,1,'leetcode','0',NULL),(3109,2109,1,'19','true',NULL),(3110,2110,1,'[1,2,3,0,0],[2,5,6]','[1,2,2,3,5,6]',NULL),(3111,2111,1,'eat,tea,tan,ate,nat,bat','[[eat,tea,ate],[tan,nat],[bat]]',NULL),(3112,2112,1,'[[1,3],[2,6],[8,10],[15,18]]','[[1,6],[8,10],[15,18]]',NULL),(3113,2113,1,'3','[((())),(()()),(())(),()(()),()()()]',NULL),(3114,2114,1,'push 2,push 1,getMin,pop,top','1,2',NULL),(3115,2115,1,'[2,7,9,3,1]','12',NULL),(3116,2116,1,'[1,2,5],11','3',NULL),(3117,2117,1,'3,7','28',NULL),(3118,2118,1,'[[1,1,0,0,0],[1,1,0,0,0],[0,0,1,0,0],[0,0,0,1,1]]','3',NULL),(3119,2119,1,'2,[[1,0]]','true',NULL),(3120,2120,1,'[1,2,3]','[[1,2,3],[1,3,2],[2,1,3],[2,3,1],[3,1,2],[3,2,1]]',NULL);

INSERT INTO test_cases (id, problem_version_id, visibility, input_text, expected_output) VALUES
 (4101,2101,'PUBLIC','121','true'),(4102,2101,'HIDDEN','-121','false'),
 (4103,2102,'PUBLIC','LVIII','58'),(4104,2102,'HIDDEN','IX','9'),
 (4105,2103,'PUBLIC','dog,racecar,car',''),(4106,2103,'HIDDEN','interview,interrupt','inter'),
 (4107,2104,'PUBLIC','[0,1,0,3,12]','[1,3,12,0,0]'),(4108,2104,'HIDDEN','[1,0,2,0,3]','[1,2,3,0,0]'),
 (4109,2105,'PUBLIC','[1,2,3,1]','true'),(4110,2105,'HIDDEN','[1,2,3,4]','false'),
 (4111,2106,'PUBLIC','[3,0,1]','2'),(4112,2106,'HIDDEN','[9,6,4,2,3,5,7,0,1]','8'),
 (4113,2107,'PUBLIC','[3,2,3]','3'),(4114,2107,'HIDDEN','[2,2,1,1,1,2,2]','2'),
 (4115,2108,'PUBLIC','loveleetcode','2'),(4116,2108,'HIDDEN','aabb','-1'),
 (4117,2109,'PUBLIC','19','true'),(4118,2109,'HIDDEN','2','false'),
 (4119,2110,'PUBLIC','[1,2,3,0,0],[2,5,6]','[1,2,2,3,5,6]'),(4120,2110,'HIDDEN','[1],[0]','[1]'),
 (4121,2111,'PUBLIC','eat,tea,tan,ate,nat,bat','[[eat,tea,ate],[tan,nat],[bat]]'),(4122,2111,'HIDDEN','a','[[a]]'),
 (4123,2112,'PUBLIC','[[1,4],[4,5]]','[[1,5]]'),(4124,2112,'HIDDEN','[[1,10],[2,3],[11,12]]','[[1,10],[11,12]]'),
 (4125,2113,'PUBLIC','1','[()]'),(4126,2113,'HIDDEN','2','[(()),(())]'),
 (4127,2114,'PUBLIC','push 2,push 1,getMin,pop,top','1,2'),(4128,2114,'HIDDEN','push -2,push 0,getMin','-2'),
 (4129,2115,'PUBLIC','[2,7,9,3,1]','12'),(4130,2115,'HIDDEN','[1,2,3,1]','4'),
 (4131,2116,'PUBLIC','[1,2,5],11','3'),(4132,2116,'HIDDEN','[2],3','-1'),
 (4133,2117,'PUBLIC','3,2','3'),(4134,2117,'HIDDEN','7,3','28'),
 (4135,2118,'PUBLIC','[[1,1,0],[0,1,0],[0,0,1]]','2'),(4136,2118,'HIDDEN','[[1,0],[0,1]]','2'),
 (4137,2119,'PUBLIC','2,[[1,0]]','true'),(4138,2119,'HIDDEN','2,[[1,0],[0,1]]','false'),
 (4139,2120,'PUBLIC','[1,2,3]','[[1,2,3],[1,3,2],[2,1,3],[2,3,1],[3,1,2],[3,2,1]]'),(4140,2120,'HIDDEN','[0,1]','[[0,1],[1,0]]');
