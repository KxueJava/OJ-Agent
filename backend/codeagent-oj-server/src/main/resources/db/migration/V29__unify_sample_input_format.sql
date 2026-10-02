-- 统一示例与判题用例的输入格式：一律使用「原始 stdin 内容」。
--
-- 背景：题库里存在两种写法，且两者混用过 ——
--   * 展示型（带变量名）：`nums = [2,7,11,15], target = 9`、`s = "()abc"`、`n = 3`
--   * 原始 stdin：`[2,7,11,15];9`、`()[]{}`、`3`
-- 因为判题器只认「原始 stdin」，而工作台的"运行样例"预填的是示例文本，
-- 于是出现"样例能过、提交必 WA"（用户在二分查找等题上踩到）。
--
-- 规则（与 V3 起就在用的其余 259 条用例保持一致）：
--   1) 去掉 `变量名 = ` 前缀；
--   2) 字符串值去掉外层双引号；
--   3) 多个参数用半角分号连接（如 `[2,7,11,15];9`），单参数直接给值。
--
-- 幂等：每条都带 `WHERE input_text = '<旧值>'`，重复执行不会改动任何行。

-- ===== 示例（examples）：11 条 =====
UPDATE examples SET input_text = '[2,7,11,15];9'              WHERE id = 3001 AND input_text = 'nums = [2,7,11,15], target = 9';
UPDATE examples SET input_text = '[3,2,4];6'                  WHERE id = 3002 AND input_text = 'nums = [3,2,4], target = 6';
UPDATE examples SET input_text = '()[]{}'                     WHERE id = 3003 AND input_text = 's = "()[]{}"';
UPDATE examples SET input_text = 'abcabcbb'                   WHERE id = 3004 AND input_text = 's = "abcabcbb"';
UPDATE examples SET input_text = '[-1,0,1,2,-1,-4]'           WHERE id = 3005 AND input_text = 'nums = [-1,0,1,2,-1,-4]';
UPDATE examples SET input_text = '[-1,0,3,5,9,12];9'          WHERE id = 3006 AND input_text = 'nums = [-1,0,3,5,9,12], target = 9';
UPDATE examples SET input_text = '[7,1,5,3,6,4]'              WHERE id = 3007 AND input_text = 'prices = [7,1,5,3,6,4]';
UPDATE examples SET input_text = '3'                          WHERE id = 3008 AND input_text = 'n = 3';
UPDATE examples SET input_text = '[-2,1,-3,4,-1,2,1,-5,4]'    WHERE id = 3009 AND input_text = 'nums = [-2,1,-3,4,-1,2,1,-5,4]';
UPDATE examples SET input_text = '[1,2,3,4]'                  WHERE id = 3010 AND input_text = 'nums = [1,2,3,4]';
UPDATE examples SET input_text = '1'                          WHERE id = 5979052396417076712 AND input_text = 'n = 1';

-- ===== 判题用例（test_cases）：14 条 =====
UPDATE test_cases SET input_text = 'abcabcbb'                 WHERE id = 4005 AND input_text = 's = "abcabcbb"';
UPDATE test_cases SET input_text = 'bbbbb'                    WHERE id = 4006 AND input_text = 's = "bbbbb"';
UPDATE test_cases SET input_text = '[-1,0,1,2,-1,-4]'         WHERE id = 4007 AND input_text = 'nums = [-1,0,1,2,-1,-4]';
UPDATE test_cases SET input_text = '[0,0,0]'                  WHERE id = 4008 AND input_text = 'nums = [0,0,0]';
UPDATE test_cases SET input_text = '[-1,0,3,5,9,12];9'        WHERE id = 4009 AND input_text = 'nums = [-1,0,3,5,9,12], target = 9';
UPDATE test_cases SET input_text = '[-1,0,3,5,9,12];2'        WHERE id = 4010 AND input_text = 'nums = [-1,0,3,5,9,12], target = 2';
UPDATE test_cases SET input_text = '[7,1,5,3,6,4]'            WHERE id = 4013 AND input_text = 'prices = [7,1,5,3,6,4]';
UPDATE test_cases SET input_text = '[7,6,4,3,1]'              WHERE id = 4014 AND input_text = 'prices = [7,6,4,3,1]';
UPDATE test_cases SET input_text = '3'                        WHERE id = 4015 AND input_text = 'n = 3';
UPDATE test_cases SET input_text = '4'                        WHERE id = 4016 AND input_text = 'n = 4';
UPDATE test_cases SET input_text = '[-2,1,-3,4,-1,2,1,-5,4]'  WHERE id = 4017 AND input_text = 'nums = [-2,1,-3,4,-1,2,1,-5,4]';
UPDATE test_cases SET input_text = '[1]'                      WHERE id = 4018 AND input_text = 'nums = [1]';
UPDATE test_cases SET input_text = '[1,2,3,4]'                WHERE id = 4019 AND input_text = 'nums = [1,2,3,4]';
UPDATE test_cases SET input_text = '[-1,1,0,-3,3]'            WHERE id = 4020 AND input_text = 'nums = [-1,1,0,-3,3]';
