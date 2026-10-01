import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 题库批量生成器 + 校验器。
 *
 * 输入：tools/gen-problems/batch-*.json（每批由内容生成器撰写，含题面、模板、测试输入、参考解）
 * 做法：**真实编译并运行每道题的参考解**，用它的 stdout 作为示例与判题用例的期望输出，
 *       从而保证"题面-示例-判题数据-参考解"四者自洽（手写期望输出是不可靠的）。
 * 输出：V22__seed_70_problems.sql + 校验报告。
 */
public class GenerateProblems {

    static final Path BASE = Path.of("D:/workspace/OJ-Agent");
    static final Path BATCH_DIR = BASE.resolve("tools/gen-problems");
    static final Path WORK = BATCH_DIR.resolve("work");
    static final Path MIGRATION = BASE.resolve("backend/codeagent-oj-server/src/main/resources/db/migration/V22__seed_70_problems.sql");
    static final Path REPORT = BATCH_DIR.resolve("report.txt");

    static final String JAVA_TEMPLATE = "import java.io.*;\n\npublic class Main {\n    public static void main(String[] args) throws Exception {\n        // 从 stdin 读取输入，把答案打印到 stdout\n    }\n}\n";

    /** 标签词表：slug -> [id, 中文名]（101-108 已存在，109+ 为本批新增） */
    static final Map<String, Object[]> TAGS = new LinkedHashMap<>();
    static {
        TAGS.put("array", new Object[]{101, "数组"});
        TAGS.put("hash-table", new Object[]{102, "哈希表"});
        TAGS.put("string", new Object[]{103, "字符串"});
        TAGS.put("stack", new Object[]{104, "栈"});
        TAGS.put("binary-search", new Object[]{105, "二分查找"});
        TAGS.put("linked-list", new Object[]{106, "链表"});
        TAGS.put("two-pointers", new Object[]{107, "双指针"});
        TAGS.put("dynamic-programming", new Object[]{108, "动态规划"});
        TAGS.put("math", new Object[]{109, "数学"});
        TAGS.put("greedy", new Object[]{110, "贪心"});
        TAGS.put("sorting", new Object[]{111, "排序"});
        TAGS.put("bit-manipulation", new Object[]{112, "位运算"});
        TAGS.put("sliding-window", new Object[]{113, "滑动窗口"});
        TAGS.put("prefix-sum", new Object[]{114, "前缀和"});
        TAGS.put("recursion", new Object[]{115, "递归"});
        TAGS.put("tree", new Object[]{116, "树"});
        TAGS.put("graph", new Object[]{117, "图"});
        TAGS.put("bfs", new Object[]{118, "广度优先搜索"});
        TAGS.put("dfs", new Object[]{119, "深度优先搜索"});
        TAGS.put("simulation", new Object[]{120, "模拟"});
        TAGS.put("matrix", new Object[]{121, "矩阵"});
        TAGS.put("heap", new Object[]{122, "堆"});
        TAGS.put("queue", new Object[]{123, "队列"});
        TAGS.put("counting", new Object[]{124, "计数"});
    }

    static final List<String> lines = new ArrayList<>();
    static final List<String> failures = new ArrayList<>();
    static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        List<Problem> problems = loadProblems();
        log("loaded problems = " + problems.size());

        // ---------- 校验 ----------
        Set<String> existing = existingSlugs();
        Set<String> existingNormalized = existing.stream().map(GenerateProblems::normalize).collect(Collectors.toSet());
        Set<String> seen = new LinkedHashSet<>();
        Map<String, String> byNormalized = new LinkedHashMap<>();
        for (Problem problem : problems) {
            if (problem.slug == null || !problem.slug.matches("[a-z0-9-]{3,64}")) {
                fail(problem, "slug 非法: " + problem.slug);
            } else if (existing.contains(problem.slug)) {
                fail(problem, "slug 与现有题目重复");
            } else if (!seen.add(problem.slug)) {
                fail(problem, "slug 与其它批次重复");
            } else if (existingNormalized.contains(normalize(problem.slug))) {
                fail(problem, "与现有题目同题换名: " + normalize(problem.slug));
            } else {
                String normalized = normalize(problem.slug);
                String previous = byNormalized.putIfAbsent(normalized, problem.slug);
                if (previous != null) {
                    fail(problem, "与 " + previous + " 同题换名（归一化后都是 " + normalized + "）");
                }
            }
            if (problem.title == null || problem.title.isBlank()) fail(problem, "缺少 title");
            if (!List.of("EASY", "MEDIUM", "HARD").contains(problem.difficulty)) fail(problem, "difficulty 非法: " + problem.difficulty);
            if (problem.statement == null || !problem.statement.contains("输入格式")) fail(problem, "statement 缺少「输入格式」");
            if (problem.statement == null || !problem.statement.contains("输出格式")) fail(problem, "statement 缺少「输出格式」");
            if (problem.solution == null || !problem.solution.contains("class Main")) fail(problem, "参考解缺失或类名不对");
            if (problem.tests.isEmpty()) fail(problem, "没有测试用例");
            else if (!"PUBLIC".equals(problem.tests.get(0).visibility)) fail(problem, "第一条测试用例不是 PUBLIC");
            for (String tag : problem.tags) {
                if (!TAGS.containsKey(tag)) fail(problem, "未知 tag: " + tag);
            }
            if (problem.tags.isEmpty()) fail(problem, "没有 tag");
        }

        // ---------- 编译并运行参考解，推导期望输出 ----------
        Files.createDirectories(WORK);
        for (Problem problem : problems) {
            if (problem.solution == null || !problem.solution.contains("class Main")) continue;
            if (!compileAndRun(problem)) continue;
        }

        // ---------- 出 SQL ----------
        writeMigration(problems);
        log("");
        log("failures = " + failures.size());
        for (String failure : failures) log("  ! " + failure);
        Files.writeString(REPORT, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        log("migration -> " + MIGRATION);
        log("report    -> " + REPORT);
    }

    // ------------------------------------------------------------------ 数据模型

    static class Test {
        String input;
        String visibility = "HIDDEN";
        String expected;
    }

    static class Example {
        String input;
        String claimedOutput;
        String derivedOutput;
    }

    static class Problem {
        int batch;
        String slug;
        String title;
        String difficulty;
        String statement;
        String constraints;
        List<String> tags = new ArrayList<>();
        List<Example> examples = new ArrayList<>();
        List<Test> tests = new ArrayList<>();
        String solution;
        boolean compiled;
        boolean valid = true;
        int id;
        int versionId;
    }

    static void fail(Problem problem, String message) {
        problem.valid = false;
        failures.add((problem.slug == null ? "(no-slug)" : problem.slug) + " — " + message);
    }

    static void log(String message) {
        System.out.println(message);
        lines.add(message);
    }

    // ------------------------------------------------------------------ 读入

    static List<Problem> loadProblems() throws IOException {
        List<Problem> problems = new ArrayList<>();
        List<Path> files = Files.list(BATCH_DIR)
                .filter(path -> path.getFileName().toString().matches("batch-\\d+\\.json"))
                .sorted()
                .collect(Collectors.toList());
        for (Path file : files) {
            JsonNode root;
            try {
                root = MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
            } catch (Exception error) {
                // 代理可能正在写这个文件：跳过而不是让整轮校验崩掉
                log("  " + file.getFileName() + " -> JSON 解析失败，已跳过: " + error.getMessage());
                failures.add(file.getFileName() + " — JSON 无法解析: " + error.getMessage());
                continue;
            }
            int batch = root.path("batch").asInt(0);
            for (JsonNode node : root.path("problems")) {
                Problem problem = new Problem();
                problem.batch = batch;
                problem.slug = text(node, "slug");
                problem.title = text(node, "title");
                problem.difficulty = text(node, "difficulty");
                problem.statement = text(node, "statement");
                problem.constraints = text(node, "constraints");
                problem.solution = text(node, "solution");
                for (JsonNode tag : node.path("tags")) problem.tags.add(tag.asText());
                for (JsonNode example : node.path("examples")) {
                    Example item = new Example();
                    item.input = text(example, "input");
                    item.claimedOutput = text(example, "output");
                    problem.examples.add(item);
                }
                for (JsonNode test : node.path("tests")) {
                    Test item = new Test();
                    item.input = text(test, "input");
                    item.visibility = test.path("visibility").asText("HIDDEN");
                    problem.tests.add(item);
                }
                problems.add(problem);
            }
            log("  " + file.getFileName() + " -> " + root.path("problems").size() + " problems");
        }
        return problems;
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    /**
     * slug 归一化：去掉虚词后按词排序，用来抓"同题换名"
     * （例如 squares-of-a-sorted-array 与 squares-of-sorted-array 会归一化成同一个键）。
     */
    static String normalize(String slug) {
        Set<String> stopWords = Set.of("a", "an", "the", "of", "in", "to", "from", "with", "by", "for", "and", "or");
        return java.util.Arrays.stream(slug.split("-"))
                .filter(token -> !stopWords.contains(token))
                .sorted()
                .collect(Collectors.joining("-"));
    }

    /** 从已有迁移里提取占用中的 slug。 */
    static Set<String> existingSlugs() throws IOException {
        Set<String> slugs = new LinkedHashSet<>();
        Pattern pattern = Pattern.compile("\\(\\s*\\d+\\s*,\\s*'([a-z0-9-]+)'\\s*,\\s*'");
        Path migrationDir = BASE.resolve("backend/codeagent-oj-server/src/main/resources/db/migration");
        for (Path file : Files.list(migrationDir)
                .filter(path -> path.toString().endsWith(".sql"))
                .filter(path -> !path.getFileName().toString().startsWith("V22__seed_70"))
                .collect(Collectors.toList())) {
            String sql = Files.readString(file, StandardCharsets.UTF_8);
            if (!sql.contains("INSERT INTO problems")) continue;
            Matcher matcher = pattern.matcher(sql);
            while (matcher.find()) slugs.add(matcher.group(1));
        }
        log("existing slugs = " + slugs.size());
        return slugs;
    }

    // ------------------------------------------------------------------ 编译运行

    static boolean compileAndRun(Problem problem) throws IOException, InterruptedException {
        Path dir = WORK.resolve(problem.slug);
        Files.createDirectories(dir);
        Path source = dir.resolve("Main.java");
        Files.writeString(source, problem.solution, StandardCharsets.UTF_8);

        Process compile = new ProcessBuilder("javac", "-encoding", "UTF-8", "Main.java")
                .directory(dir.toFile()).redirectErrorStream(true).start();
        String compileOutput = new String(compile.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!compile.waitFor(90, TimeUnit.SECONDS) || compile.exitValue() != 0) {
            fail(problem, "参考解编译失败: " + compileOutput.strip().replace('\n', ' '));
            return false;
        }
        problem.compiled = true;

        for (Test test : problem.tests) {
            String output = run(dir, test.input);
            if (output == null) {
                fail(problem, "测试用例运行失败/超时: " + shortInput(test.input));
                continue;
            }
            test.expected = output;
        }
        for (Example example : problem.examples) {
            String output = run(dir, example.input);
            if (output == null) {
                fail(problem, "示例运行失败/超时: " + shortInput(example.input));
                continue;
            }
            example.derivedOutput = output;
            if (example.claimedOutput != null && !example.claimedOutput.equals(output)) {
                fail(problem, "示例输出与参考解不一致（以参考解为准，已自动修正）: " + shortInput(example.input)
                        + " 声称=" + example.claimedOutput.replace('\n', ' ') + " 实际=" + output.replace('\n', ' '));
            }
        }
        return true;
    }

    static String run(Path dir, String input) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("java", "-Xmx256m", "Main")
                .directory(dir.toFile()).redirectErrorStream(false).start();
        process.getOutputStream().write((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(8, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return null;
        }
        if (process.exitValue() != 0) {
            return null;
        }
        if (!stderr.isBlank()) {
            // 只作为提示：stderr 有输出说明参考解在打印调试信息
            return stdout.strip();
        }
        return stdout.strip();
    }

    static String shortInput(String input) {
        if (input == null) return "null";
        String flat = input.replace('\n', '⏎');
        return flat.length() <= 40 ? flat : flat.substring(0, 40) + "…";
    }

    // ------------------------------------------------------------------ 出 SQL

    static void writeMigration(List<Problem> problems) throws IOException {
        List<Problem> usable = problems.stream()
                .filter(p -> p.valid && p.compiled && !p.tests.isEmpty() && p.tests.stream().allMatch(t -> t.expected != null))
                .collect(Collectors.toList());
        List<String> sql = new ArrayList<>();
        sql.add("-- 70 道新题（由 tools/gen-problems/GenerateProblems.java 生成）");
        sql.add("-- 期望输出全部来自真实编译并运行参考解的 stdout，因此题面/示例/判题数据与参考解四者自洽。");
        sql.add("");
        sql.add("INSERT INTO tags (id, name, slug) VALUES " + TAGS.entrySet().stream()
                .filter(entry -> ((Integer) entry.getValue()[0]) >= 109)
                .map(entry -> "(" + entry.getValue()[0] + ", '" + entry.getValue()[1] + "', '" + entry.getKey() + "')")
                .collect(Collectors.joining(",")) + ";");
        sql.add("");

        int id = 1201;
        for (Problem problem : usable) {
            problem.id = id++;
            problem.versionId = problem.id + 1000;
        }

        sql.add("INSERT INTO problems (id, slug, title, difficulty, status) VALUES");
        sql.add(usable.stream().map(p -> " (" + p.id + ", '" + sql(p.slug) + "', '" + sql(p.title) + "', '" + p.difficulty + "', 'PUBLISHED')")
                .collect(Collectors.joining(",\n")) + ";");
        sql.add("");

        sql.add("INSERT INTO problem_versions (id, problem_id, version_no, status, statement_md, constraints_md, java_template, published_at) VALUES");
        sql.add(usable.stream().map(p -> " (" + p.versionId + ", " + p.id + ", 1, 'PUBLISHED', '" + sql(p.statement) + "', '"
                        + sql(p.constraints == null ? "" : p.constraints) + "', '" + sql(JAVA_TEMPLATE) + "', CURRENT_TIMESTAMP)")
                .collect(Collectors.joining(",\n")) + ";");
        sql.add("");
        sql.add("UPDATE problems SET published_version_id = id + 1000 WHERE id BETWEEN " + usable.get(0).id + " AND " + usable.get(usable.size() - 1).id + ";");
        sql.add("");

        List<String> tagRows = new ArrayList<>();
        for (Problem problem : usable) {
            for (String tag : problem.tags) {
                tagRows.add("(" + problem.id + "," + TAGS.get(tag)[0] + ")");
            }
        }
        sql.add("INSERT INTO problem_tags (problem_id, tag_id) VALUES " + String.join(",", tagRows) + ";");
        sql.add("");

        int exampleId = 3201;
        List<String> exampleRows = new ArrayList<>();
        for (Problem problem : usable) {
            int order = 1;
            for (Example example : problem.examples) {
                String output = example.derivedOutput != null ? example.derivedOutput : example.claimedOutput;
                if (output == null) continue;
                exampleRows.add("(" + exampleId++ + "," + problem.versionId + "," + order++ + ",'" + sql(example.input) + "','" + sql(output) + "',NULL)");
            }
        }
        sql.add("INSERT INTO examples (id, problem_version_id, display_order, input_text, output_text, explanation_md) VALUES");
        sql.add(String.join(",\n", exampleRows) + ";");
        sql.add("");

        int caseId = 4201;
        List<String> caseRows = new ArrayList<>();
        for (Problem problem : usable) {
            for (Test test : problem.tests) {
                caseRows.add("(" + caseId++ + "," + problem.versionId + ",'" + test.visibility + "','" + sql(test.input) + "','" + sql(test.expected) + "')");
            }
        }
        sql.add("INSERT INTO test_cases (id, problem_version_id, visibility, input_text, expected_output) VALUES");
        sql.add(String.join(",\n", caseRows) + ";");
        sql.add("");

        Files.writeString(MIGRATION, String.join("\n", sql), StandardCharsets.UTF_8);
        log("");
        log("usable problems = " + usable.size() + " (skipped " + (problems.size() - usable.size()) + ")");
        log("examples = " + exampleRows.size() + ", test_cases = " + caseRows.size());
        Map<String, Long> byDifficulty = usable.stream().collect(Collectors.groupingBy(p -> p.difficulty, LinkedHashMap::new, Collectors.counting()));
        log("difficulty = " + byDifficulty);
        log("by batch = " + usable.stream().collect(Collectors.groupingBy(p -> p.batch, LinkedHashMap::new, Collectors.counting())));
    }

    static String sql(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("'", "''")
                .replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
    }
}
