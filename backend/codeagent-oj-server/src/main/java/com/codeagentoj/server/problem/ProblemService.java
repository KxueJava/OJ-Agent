package com.codeagentoj.server.problem;

import static com.codeagentoj.server.problem.ProblemDtos.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProblemService {
    private final JdbcTemplate jdbc;
    public ProblemService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public PageView list(String query, String difficulty, String tag, int page, int size, Long userId, boolean favoriteOnly) {
        page = Math.max(page, 0); size = Math.min(Math.max(size, 1), 50);
        StringBuilder where = new StringBuilder(" WHERE p.status = 'PUBLISHED'"); List<Object> args = new ArrayList<>();
        if (query != null && !query.isBlank()) { where.append(" AND (p.title LIKE ? OR p.slug LIKE ?)"); args.add("%" + query.trim() + "%"); args.add("%" + query.trim() + "%"); }
        if (difficulty != null && !difficulty.isBlank()) { where.append(" AND p.difficulty = ?"); args.add(difficulty); }
        if (tag != null && !tag.isBlank()) { where.append(" AND EXISTS (SELECT 1 FROM problem_tags pt JOIN tags t ON t.id=pt.tag_id WHERE pt.problem_id=p.id AND t.slug=?)"); args.add(tag); }
        // 只看收藏（收藏页用）：必须登录，否则"我的收藏"无从谈起
        if (favoriteOnly) {
            if (userId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "查看收藏需要先登录");
            where.append(" AND EXISTS (SELECT 1 FROM problem_favorites f WHERE f.problem_id=p.id AND f.user_id=?)");
            args.add(userId);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM problems p" + where, Long.class, args.toArray());
        String sql = "SELECT p.id,p.slug,p.title,p.difficulty, " + (userId == null ? "FALSE" : "EXISTS (SELECT 1 FROM problem_favorites f WHERE f.problem_id=p.id AND f.user_id=" + userId + ")") + " favorite FROM problems p" + where + " ORDER BY p.id LIMIT ? OFFSET ?";
        args.add(size); args.add(page * size);
        List<ProblemSummary> items = jdbc.query(sql, (rs, n) -> new ProblemSummary(rs.getString("slug"), rs.getString("title"), rs.getString("difficulty"), tags(rs.getLong("id")), rs.getBoolean("favorite")), args.toArray());
        return new PageView(items, page, size, total);
    }
    public ProblemDetail detail(String slug, Long userId) {
        String sql = "SELECT p.id,p.slug,p.title,p.difficulty,pv.id vid,pv.version_no,pv.statement_md,pv.constraints_md,pv.java_template FROM problems p JOIN problem_versions pv ON pv.id=p.published_version_id WHERE p.slug=? AND p.status='PUBLISHED'";
        List<Map<String,Object>> rows = jdbc.queryForList(sql, slug); if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "题目不存在或尚未发布"); Map<String,Object> r = rows.getFirst(); long problemId = ((Number) r.get("id")).longValue(); long versionId = ((Number) r.get("vid")).longValue();
        boolean favorite = userId != null && Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM problem_favorites WHERE user_id=? AND problem_id=?)", Boolean.class, userId, problemId));
        List<ExampleView> examples = jdbc.query("SELECT display_order,input_text,output_text,explanation_md FROM examples WHERE problem_version_id=? ORDER BY display_order", (rs,n) -> new ExampleView(rs.getInt(1),rs.getString(2),rs.getString(3),rs.getString(4)), versionId);
        return new ProblemDetail((String) r.get("slug"),(String) r.get("title"),(String) r.get("difficulty"),(String) r.get("statement_md"),(String) r.get("constraints_md"),(String) r.get("java_template"),((Number)r.get("version_no")).intValue(),tags(problemId),examples,favorite);
    }
    public List<TagView> tags() { return jdbc.query("SELECT name,slug FROM tags ORDER BY name", (rs,n) -> new TagView(rs.getString(1),rs.getString(2))); }
    public void favorite(String slug, long userId, boolean enabled) { Long id = jdbc.query("SELECT id FROM problems WHERE slug=? AND status='PUBLISHED'", (rs,n)->rs.getLong(1), slug).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"题目不存在")); if (enabled) { try { jdbc.update("INSERT INTO problem_favorites (user_id,problem_id) VALUES (?,?)",userId,id); } catch (DataIntegrityViolationException ignored) {} } else jdbc.update("DELETE FROM problem_favorites WHERE user_id=? AND problem_id=?",userId,id); }
    @Transactional public AdminProblemView create(CreateProblemRequest req, long adminId) { if (jdbc.queryForObject("SELECT COUNT(*) FROM problems WHERE slug=?",Long.class,req.slug()) > 0) throw new ResponseStatusException(HttpStatus.CONFLICT,"题目 slug 已存在"); long id = nextId(); jdbc.update("INSERT INTO problems (id,slug,title,difficulty,status,created_by) VALUES (?,?,?,?, 'DRAFT',?)",id,req.slug(),req.title(),req.difficulty(),adminId); createVersion(id,req.version(),adminId); saveTags(id,req.tags()); return admin(id); }
    @Transactional public AdminProblemView addVersion(String slug, VersionRequest req, long adminId) { long id = idBySlug(slug); createVersion(id,req,adminId); return admin(id); }
    @Transactional public AdminProblemView publish(String slug) {
        long id = idBySlug(slug);
        Instant now = Instant.now();
        Long version = jdbc.query("SELECT id FROM problem_versions WHERE problem_id=? AND status='DRAFT' ORDER BY version_no DESC LIMIT 1", (rs,n) -> rs.getLong(1), id).stream().findFirst().orElse(null);
        if (version != null) {
            jdbc.update("UPDATE problem_versions SET status='PUBLISHED',published_at=? WHERE id=?", now, version);
        } else {
            version = jdbc.query("SELECT id FROM problem_versions WHERE problem_id=? AND status='PUBLISHED' ORDER BY version_no DESC LIMIT 1", (rs,n) -> rs.getLong(1), id).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,"没有可发布的题目版本"));
        }
        jdbc.update("UPDATE problems SET status='PUBLISHED',published_version_id=? WHERE id=?", version, id);
        return admin(id);
    }
    @Transactional public AdminProblemView unpublish(String slug) { long id=idBySlug(slug); jdbc.update("UPDATE problems SET status='OFFLINE' WHERE id=?",id); return admin(id); }
    private void createVersion(long problemId, VersionRequest req, long adminId) { int no=jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM problem_versions WHERE problem_id=?",Integer.class,problemId); long versionId=nextId(); jdbc.update("INSERT INTO problem_versions (id,problem_id,version_no,status,statement_md,constraints_md,java_template,created_by) VALUES (?,?,?,'DRAFT',?,?,?,?)",versionId,problemId,no,req.statementMd(),req.constraintsMd(),req.javaTemplate(),adminId); int order=1; for (ExampleRequest e : req.examples()==null?List.<ExampleRequest>of():req.examples()) jdbc.update("INSERT INTO examples (id,problem_version_id,display_order,input_text,output_text,explanation_md) VALUES (?,?,?,?,?,?)",nextId(),versionId,order++,e.input(),e.output(),e.explanation()); for (TestCaseRequest t : req.testCases()==null?List.<TestCaseRequest>of():req.testCases()) jdbc.update("INSERT INTO test_cases (id,problem_version_id,visibility,input_text,expected_output,weight) VALUES (?,?,?,?,?,?)",nextId(),versionId,t.visibility(),t.input(),t.expectedOutput(),t.weight()==null?1:t.weight()); }
    private void saveTags(long problemId,List<String> slugs) { for(String slug:slugs) { Long tag=jdbc.query("SELECT id FROM tags WHERE slug=?",(rs,n)->rs.getLong(1),slug).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"未知标签: "+slug)); jdbc.update("INSERT INTO problem_tags (problem_id,tag_id) VALUES (?,?)",problemId,tag); } }
    private List<TagView> tags(long id) { return jdbc.query("SELECT t.name,t.slug FROM tags t JOIN problem_tags pt ON pt.tag_id=t.id WHERE pt.problem_id=? ORDER BY t.name",(rs,n)->new TagView(rs.getString(1),rs.getString(2)),id); }
    private long idBySlug(String slug){ return jdbc.query("SELECT id FROM problems WHERE slug=?",(rs,n)->rs.getLong(1),slug).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"题目不存在")); }
    private long nextId(){ return Math.abs(java.util.concurrent.ThreadLocalRandom.current().nextLong(1,Long.MAX_VALUE)); }
    private AdminProblemView admin(long id){ return jdbc.query("SELECT slug,title,difficulty,status,published_version_id,updated_at FROM problems WHERE id=?",(rs,n)->{ Number published = (Number) rs.getObject(5); return new AdminProblemView(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),published == null ? null : published.longValue(),rs.getTimestamp(6).toInstant()); },id).getFirst(); }
}
