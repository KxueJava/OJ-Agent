-- 统一早期题目的 java_template。
--
-- 问题：two-sum / valid-parentheses / longest-substring-without-repeating-characters / three-sum /
--       binary-search / reverse-linked-list / best-time-to-buy-and-sell-stock / climbing-stairs /
--       maximum-subarray / product-of-array-except-self 这 10 道题的模板是 LeetCode 风格：
--       `public class Main { public int search(int[] nums, int target) { ... } }`
--       —— 有 Main 类，但没有 main 方法。而判题器是把测试输入喂给 Main 的 stdin、比对 stdout，
--       照这种模板写永远不会输出任何东西（用户在实际使用中踩到：算法正确也一定 WA）。
--
-- 处置：把仍是这种"没有 main 方法"的已发布模板换成标准的 stdin/stdout 骨架，与后续题库保持一致。
-- 幂等：替换后不再匹配 WHERE 条件，重复执行不会改动任何行。
UPDATE problem_versions
   SET java_template = 'import java.io.*;\n\npublic class Main {\n    public static void main(String[] args) throws Exception {\n        // 从 stdin 读取输入，把答案打印到 stdout\n    }\n}\n'
 WHERE status = 'PUBLISHED'
   AND java_template NOT LIKE '%static void main%';
