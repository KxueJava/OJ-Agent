-- 让「公开样例」与题面示例保持一致。
--
-- 背景：岛屿数量（problem_version 2118）题面示例 1 是 4 行网格 → 3，
-- 而判题跑的公开用例却是另一个 3 行网格 → 2。两条数据本身都没错，
-- 但做题的人对着题面示例调代码，判题却喂了另一个输入，失败提示只写
-- 「公开样例输出不匹配」，非常难自查（实测中确实误导了使用者）。
--
-- 处理：公开用例改成题面示例本身；原来的 3 行网格降级为隐藏用例，覆盖不缩水。
UPDATE test_cases
   SET input_text = '[[1,1,0,0,0],[1,1,0,0,0],[0,0,1,0,0],[0,0,0,1,1]]',
       expected_output = '3'
 WHERE id = 4135 AND problem_version_id = 2118 AND visibility = 'PUBLIC';

-- 注意：id 取 4999 是为了避开 V22 题库生成器占用的区间（它从 4201 起连续分配）。
INSERT INTO test_cases (id, problem_version_id, visibility, input_text, expected_output)
VALUES (4999, 2118, 'HIDDEN', '[[1,1,0],[0,1,0],[0,0,1]]', '2');
