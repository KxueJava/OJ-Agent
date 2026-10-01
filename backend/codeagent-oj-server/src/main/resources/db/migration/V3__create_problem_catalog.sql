CREATE TABLE tags (
    id BIGINT NOT NULL PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE,
    slug VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE problems (
    id BIGINT NOT NULL PRIMARY KEY,
    slug VARCHAR(128) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    difficulty VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    published_version_id BIGINT NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_problems_catalog (status, difficulty, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE problem_versions (
    id BIGINT NOT NULL PRIMARY KEY,
    problem_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    statement_md MEDIUMTEXT NOT NULL,
    constraints_md TEXT NOT NULL,
    java_template MEDIUMTEXT NOT NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL,
    CONSTRAINT fk_problem_versions_problem FOREIGN KEY (problem_id) REFERENCES problems (id),
    UNIQUE KEY uk_problem_versions (problem_id, version_no),
    INDEX idx_problem_versions_status (problem_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE problems ADD CONSTRAINT fk_problems_published_version FOREIGN KEY (published_version_id) REFERENCES problem_versions (id);

CREATE TABLE problem_tags (
    problem_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (problem_id, tag_id),
    CONSTRAINT fk_problem_tags_problem FOREIGN KEY (problem_id) REFERENCES problems (id),
    CONSTRAINT fk_problem_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE examples (
    id BIGINT NOT NULL PRIMARY KEY,
    problem_version_id BIGINT NOT NULL,
    display_order INT NOT NULL,
    input_text TEXT NOT NULL,
    output_text TEXT NOT NULL,
    explanation_md TEXT NULL,
    CONSTRAINT fk_examples_version FOREIGN KEY (problem_version_id) REFERENCES problem_versions (id),
    INDEX idx_examples_version (problem_version_id, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE test_cases (
    id BIGINT NOT NULL PRIMARY KEY,
    problem_version_id BIGINT NOT NULL,
    visibility VARCHAR(16) NOT NULL,
    input_text MEDIUMTEXT NOT NULL,
    expected_output MEDIUMTEXT NOT NULL,
    weight INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_test_cases_version FOREIGN KEY (problem_version_id) REFERENCES problem_versions (id),
    INDEX idx_test_cases_worker (problem_version_id, visibility)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE problem_favorites (
    user_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, problem_id),
    CONSTRAINT fk_problem_favorites_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_problem_favorites_problem FOREIGN KEY (problem_id) REFERENCES problems (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO tags (id, name, slug) VALUES (101, '数组', 'array'), (102, '哈希表', 'hash-table'), (103, '字符串', 'string'), (104, '栈', 'stack'), (105, '二分查找', 'binary-search'), (106, '链表', 'linked-list'), (107, '双指针', 'two-pointers'), (108, '动态规划', 'dynamic-programming');

INSERT INTO problems (id, slug, title, difficulty, status) VALUES
 (1001, 'two-sum', '两数之和', 'MEDIUM', 'PUBLISHED'), (1002, 'valid-parentheses', '有效的括号', 'EASY', 'PUBLISHED'), (1003, 'longest-substring-without-repeating-characters', '无重复字符的最长子串', 'MEDIUM', 'PUBLISHED'), (1004, 'three-sum', '三数之和', 'MEDIUM', 'PUBLISHED'), (1005, 'binary-search', '二分查找', 'EASY', 'PUBLISHED'), (1006, 'reverse-linked-list', '反转链表', 'EASY', 'PUBLISHED'), (1007, 'best-time-to-buy-and-sell-stock', '买卖股票的最佳时机', 'EASY', 'PUBLISHED'), (1008, 'climbing-stairs', '爬楼梯', 'EASY', 'PUBLISHED'), (1009, 'maximum-subarray', '最大子数组和', 'MEDIUM', 'PUBLISHED'), (1010, 'product-of-array-except-self', '除自身以外数组的乘积', 'MEDIUM', 'PUBLISHED');

INSERT INTO problem_versions (id, problem_id, version_no, status, statement_md, constraints_md, java_template, published_at) VALUES
 (2001,1001,1,'PUBLISHED','给定整数数组 `nums` 和目标值 `target`，找出和为目标值的两个下标。每种输入恰有一个答案，同一元素不能重复使用。','- `2 <= nums.length <= 10^4`\n- `-10^9 <= nums[i] <= 10^9`','import java.util.*;\n\npublic class Main {\n  public int[] twoSum(int[] nums, int target) {\n    return new int[0];\n  }\n}',CURRENT_TIMESTAMP),
 (2002,1002,1,'PUBLISHED','给定只包括 `(`、`)`、`{`、`}`、`[`、`]` 的字符串，判断字符串是否有效。','- `1 <= s.length <= 10^4`','import java.util.*;\n\npublic class Main {\n  public boolean isValid(String s) {\n    return false;\n  }\n}',CURRENT_TIMESTAMP),
 (2003,1003,1,'PUBLISHED','给定一个字符串，请找出其中不含重复字符的最长子串长度。','- `0 <= s.length <= 5 * 10^4`','import java.util.*;\n\npublic class Main {\n  public int lengthOfLongestSubstring(String s) {\n    return 0;\n  }\n}',CURRENT_TIMESTAMP),
 (2004,1004,1,'PUBLISHED','给你一个整数数组 `nums`，返回所有和为 0 且不重复的三元组。','- `3 <= nums.length <= 3000`','import java.util.*;\n\npublic class Main {\n  public List<List<Integer>> threeSum(int[] nums) {\n    return List.of();\n  }\n}',CURRENT_TIMESTAMP),
 (2005,1005,1,'PUBLISHED','给定一个升序数组和目标值，返回目标值下标；不存在则返回 -1。','- `1 <= nums.length <= 10^4`','public class Main {\n  public int search(int[] nums, int target) {\n    return -1;\n  }\n}',CURRENT_TIMESTAMP),
 (2006,1006,1,'PUBLISHED','反转一个单链表并返回新的头节点。','- 链表节点数范围为 `[0, 5000]`','public class Main {\n  public ListNode reverseList(ListNode head) {\n    return null;\n  }\n}',CURRENT_TIMESTAMP),
 (2007,1007,1,'PUBLISHED','给定价格数组，选择一次买入和一次卖出，返回最大利润。','- `1 <= prices.length <= 10^5`','public class Main {\n  public int maxProfit(int[] prices) {\n    return 0;\n  }\n}',CURRENT_TIMESTAMP),
 (2008,1008,1,'PUBLISHED','每次可爬 1 或 2 个台阶，计算到达第 n 阶的方法数。','- `1 <= n <= 45`','public class Main {\n  public int climbStairs(int n) {\n    return 0;\n  }\n}',CURRENT_TIMESTAMP),
 (2009,1009,1,'PUBLISHED','找出一个具有最大和的连续子数组，返回其最大和。','- `1 <= nums.length <= 10^5`','public class Main {\n  public int maxSubArray(int[] nums) {\n    return 0;\n  }\n}',CURRENT_TIMESTAMP),
 (2010,1010,1,'PUBLISHED','返回数组中每个元素左侧和右侧所有元素的乘积，不能使用除法。','- `2 <= nums.length <= 10^5`','public class Main {\n  public int[] productExceptSelf(int[] nums) {\n    return new int[0];\n  }\n}',CURRENT_TIMESTAMP);

UPDATE problems SET published_version_id = id + 1000;
INSERT INTO problem_tags (problem_id, tag_id) VALUES (1001,101),(1001,102),(1002,104),(1002,103),(1003,102),(1003,103),(1004,101),(1004,107),(1005,101),(1005,105),(1006,106),(1007,101),(1008,108),(1009,101),(1009,108),(1010,101),(1010,102);
INSERT INTO examples (id, problem_version_id, display_order, input_text, output_text, explanation_md) VALUES (3001,2001,1,'nums = [2,7,11,15], target = 9','[0,1]','因为 `nums[0] + nums[1] = 9`。'),(3002,2001,2,'nums = [3,2,4], target = 6','[1,2]',NULL),(3003,2002,1,'s = "()[]{}"','true',NULL),(3004,2003,1,'s = "abcabcbb"','3','答案是 `abc`。'),(3005,2004,1,'nums = [-1,0,1,2,-1,-4]','[[-1,-1,2],[-1,0,1]]',NULL),(3006,2005,1,'nums = [-1,0,3,5,9,12], target = 9','4',NULL),(3007,2007,1,'prices = [7,1,5,3,6,4]','5',NULL),(3008,2008,1,'n = 3','3',NULL),(3009,2009,1,'nums = [-2,1,-3,4,-1,2,1,-5,4]','6',NULL),(3010,2010,1,'nums = [1,2,3,4]','[24,12,8,6]',NULL);
INSERT INTO test_cases (id, problem_version_id, visibility, input_text, expected_output) VALUES (4001,2001,'PUBLIC','[2,7,11,15];9','[0,1]'),(4002,2001,'HIDDEN','[3,3];6','[0,1]'),(4003,2002,'PUBLIC','()[]{}','true'),(4004,2002,'HIDDEN','(]','false');
