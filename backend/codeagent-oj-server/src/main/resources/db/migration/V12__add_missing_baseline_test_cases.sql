-- The original catalog only seeded test cases through problem version 2002.
-- Keep public test input aligned with the sample shown in the workspace.
INSERT INTO test_cases (id, problem_version_id, visibility, input_text, expected_output) VALUES
 (4005, 2003, 'PUBLIC', 's = "abcabcbb"', '3'),
 (4006, 2003, 'HIDDEN', 's = "bbbbb"', '1'),
 (4007, 2004, 'PUBLIC', 'nums = [-1,0,1,2,-1,-4]', '[[-1,-1,2],[-1,0,1]]'),
 (4008, 2004, 'HIDDEN', 'nums = [0,0,0]', '[[0,0,0]]'),
 (4009, 2005, 'PUBLIC', 'nums = [-1,0,3,5,9,12], target = 9', '4'),
 (4010, 2005, 'HIDDEN', 'nums = [-1,0,3,5,9,12], target = 2', '-1'),
 (4011, 2006, 'PUBLIC', '[1,2,3,4,5]', '[5,4,3,2,1]'),
 (4012, 2006, 'HIDDEN', '[]', '[]'),
 (4013, 2007, 'PUBLIC', 'prices = [7,1,5,3,6,4]', '5'),
 (4014, 2007, 'HIDDEN', 'prices = [7,6,4,3,1]', '0'),
 (4015, 2008, 'PUBLIC', 'n = 3', '3'),
 (4016, 2008, 'HIDDEN', 'n = 4', '5'),
 (4017, 2009, 'PUBLIC', 'nums = [-2,1,-3,4,-1,2,1,-5,4]', '6'),
 (4018, 2009, 'HIDDEN', 'nums = [1]', '1'),
 (4019, 2010, 'PUBLIC', 'nums = [1,2,3,4]', '[24,12,8,6]'),
 (4020, 2010, 'HIDDEN', 'nums = [-1,1,0,-3,3]', '[0,0,9,0,0]');
