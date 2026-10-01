package com.codeagentoj.server.workspace;

import com.codeagentoj.server.api.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Public problem data plus the immutable version identifier required by the judge. */
@RestController
@RequestMapping("/api/workspace/problems")
public class WorkspaceProblemController {
    private final JdbcTemplate jdbc;

    public WorkspaceProblemController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Tag(String name, String slug) {}
    public record Example(int order, String input, String output, String explanation) {}
    public record WorkspaceProblem(
            long problemVersionId,
            String slug,
            String title,
            String difficulty,
            String statementMd,
            String constraintsMd,
            String javaTemplate,
            List<Tag> tags,
            List<Example> examples) {}

    @GetMapping("/{slug}")
    public ApiResponse<WorkspaceProblem> detail(@PathVariable String slug) {
        Map<String, Object> problem = jdbc.queryForList("""
                SELECT p.id AS problem_id, pv.id AS version_id, p.slug, p.title, p.difficulty,
                       pv.statement_md, pv.constraints_md, pv.java_template
                FROM problems p JOIN problem_versions pv ON pv.problem_id = p.id
                WHERE p.slug = ? AND p.status = 'PUBLISHED' AND pv.status = 'PUBLISHED'
                ORDER BY pv.version_no DESC LIMIT 1
                """, slug).stream().findFirst().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "题目不存在或未发布"));
        long problemId = ((Number) problem.get("problem_id")).longValue();
        long versionId = ((Number) problem.get("version_id")).longValue();
        List<Tag> tags = jdbc.query("""
                SELECT t.name, t.slug FROM tags t JOIN problem_tags pt ON pt.tag_id = t.id
                WHERE pt.problem_id = ? ORDER BY t.name
                """, (rs, row) -> new Tag(rs.getString(1), rs.getString(2)), problemId);
        List<Example> examples = jdbc.query("""
                SELECT display_order, input_text, output_text, explanation_md FROM examples
                WHERE problem_version_id = ? ORDER BY display_order
                """, (rs, row) -> new Example(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)), versionId);
        return ApiResponse.ok(new WorkspaceProblem(
                versionId,
                (String) problem.get("slug"),
                (String) problem.get("title"),
                (String) problem.get("difficulty"),
                (String) problem.get("statement_md"),
                (String) problem.get("constraints_md"),
                (String) problem.get("java_template"),
                tags,
                examples));
    }
}
