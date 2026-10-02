package com.codeagentoj.server.discussion;

import com.codeagentoj.server.contest.ContestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 讨论区（阶段一/二）：列表、发帖、详情、回复、采纳。
 *
 * 关键策略：
 *  - 关联题目时，若该题属于"本人已报名 + 比赛进行中"的赛题，则**拒绝发帖/回复** ——
 *    与 Agent 赛中锁复用同一判定（ContestService.lockedContestSlugForProblem），
 *    这样"竞赛期间禁止赛题讨论"是服务端强制的，而不是靠前端藏按钮；
 *  - 采纳只能由楼主操作，且只能采纳本帖内的楼层；
 *  - 已被锁定的主题帖不允许新增回复。
 */
@Service
public class DiscussionService {

    private static final List<String> CATEGORIES = List.of("SOLUTION", "QUESTION", "NOTICE", "CHAT");

    private final JdbcTemplate jdbc;

    /** 竞赛锁：字段注入，避免影响构造签名；测试/独立运行时可缺省。 */
    @Autowired(required = false)
    private com.codeagentoj.server.agent.DeepSeekClient deepSeek;
    private ContestService contests;

    public DiscussionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static long nextId() { return Math.abs(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)); }

    private void requireNotContestProblem(long userId, String problemSlug) {
        if (contests == null || problemSlug == null || problemSlug.isBlank()) return;
        String locked = contests.lockedContestSlugForProblem(userId, problemSlug);
        if (locked != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "比赛进行中（" + locked + "），期间不能发布或回复该赛题的讨论");
        }
    }

    /** 列表：支持分类过滤、关键词搜索（标题/正文/题目 slug）、三种排序。 */
    public List<Map<String, Object>> list(String category, String query, String sort, int page, int size) {
        int limit = Math.min(Math.max(size, 1), 50);
        int offset = Math.max(page, 0) * limit;
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (category != null && !category.isBlank()) { where.append(" AND d.category = ?"); args.add(category.toUpperCase()); }
        if (query != null && !query.isBlank()) {
            where.append(" AND (d.title LIKE ? OR d.body_md LIKE ? OR p.slug LIKE ?)");
            String like = "%" + query.trim() + "%";
            args.add(like); args.add(like); args.add(like);
        }
        String order = switch (sort == null ? "" : sort) {
            case "new" -> "d.created_at DESC";
            case "hot" -> "d.views DESC, d.reply_count DESC";
            case "unanswered" -> "d.reply_count ASC, d.last_reply_at DESC";
            default -> "d.pinned DESC, d.last_reply_at DESC";
        };
        args.add(limit); args.add(offset);
        return jdbc.queryForList("SELECT d.id AS id, d.category AS category, d.title AS title, d.pinned AS pinned, "
                + "d.locked AS locked, d.views AS views, d.reply_count AS replyCount, "
                + "d.accepted_post_id AS acceptedPostId, d.created_at AS createdAt, d.last_reply_at AS lastReplyAt, "
                + "u.display_name AS authorName, p.slug AS problemSlug, p.title AS problemTitle "
                + "FROM discussions d JOIN users u ON u.id = d.author_id "
                + "LEFT JOIN problems p ON p.id = d.problem_id" + where + " ORDER BY " + order + " LIMIT ? OFFSET ?",
                args.toArray());
    }

    /** 发帖。problemSlug 可空；非空时校验题目存在且不在进行中的比赛里。 */
    public Map<String, Object> create(long userId, String category, String title, String body, String problemSlug) {
        String cat = category == null ? "QUESTION" : category.toUpperCase();
        if (!CATEGORIES.contains(cat)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分类不合法");
        if (title == null || title.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题不能为空");
        if (body == null || body.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "正文不能为空");
        requireNotContestProblem(userId, problemSlug);
        Long problemId = null;
        if (problemSlug != null && !problemSlug.isBlank()) {
            problemId = jdbc.query("SELECT id FROM problems WHERE slug=? AND status='PUBLISHED'",
                    (rs, n) -> rs.getLong(1), problemSlug.trim()).stream().findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "题目不存在"));
        }
        long id = nextId();
        jdbc.update("INSERT INTO discussions (id, category, title, body_md, problem_id, author_id) VALUES (?,?,?,?,?,?)",
                id, cat, title.trim(), body, problemId, userId);
        return detail(id, userId);
    }

    /** 详情：主题帖 + 全部楼层；浏览数 +1（阅读时自增，简单直接）。 */
    public Map<String, Object> detail(long id, Long userId) {
        jdbc.update("UPDATE discussions SET views = views + 1 WHERE id = ?", id);
        List<Map<String, Object>> head = jdbc.queryForList(
                "SELECT d.id AS id, d.category AS category, d.title AS title, d.body_md AS body, d.pinned AS pinned, "
                        + "d.locked AS locked, d.views AS views, d.reply_count AS replyCount, "
                        + "d.accepted_post_id AS acceptedPostId, d.created_at AS createdAt, d.last_reply_at AS lastReplyAt, "
                        + "u.display_name AS authorName, u.username AS authorUsername, p.slug AS problemSlug, p.title AS problemTitle "
                        + "FROM discussions d JOIN users u ON u.id = d.author_id LEFT JOIN problems p ON p.id = d.problem_id "
                        + "WHERE d.id = ?", id);
        if (head.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "帖子不存在");
        List<Map<String, Object>> posts = jdbc.queryForList(
                "SELECT c.id AS id, c.body_md AS body, c.quoted_post_id AS quotedPostId, c.upvotes AS upvotes, "
                        + "c.accepted AS accepted, c.created_at AS createdAt, u.display_name AS authorName, u.username AS authorUsername "
                        + "FROM discussion_posts c JOIN users u ON u.id = c.author_id WHERE c.discussion_id = ? ORDER BY c.created_at",
                id);
        Map<String, Object> result = new java.util.LinkedHashMap<>(head.getFirst());
        result.put("posts", posts);
        return result;
    }

    /** 回复：写入楼层并更新主题帖的回复数与最后回复时间；锁定的帖子拒绝回复。 */
    public Map<String, Object> reply(long userId, long discussionId, String body, Long quotedPostId) {
        if (body == null || body.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "回复内容不能为空");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT locked, problem_id FROM discussions WHERE id = ?", discussionId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "帖子不存在");
        Object lockedValue = rows.getFirst().get("locked"); boolean locked = lockedValue instanceof Boolean flag ? flag : ((Number) lockedValue).intValue() == 1;
        if (locked) throw new ResponseStatusException(HttpStatus.CONFLICT, "该主题帖已锁定，不能再回复");
        Object problemId = rows.getFirst().get("problem_id");
        if (problemId != null) {
            String slug = jdbc.query("SELECT slug FROM problems WHERE id=?", (rs, n) -> rs.getString(1), ((Number) problemId).longValue())
                    .stream().findFirst().orElse(null);
            requireNotContestProblem(userId, slug);
        }
        long postId = nextId();
        jdbc.update("INSERT INTO discussion_posts (id, discussion_id, author_id, body_md, quoted_post_id) VALUES (?,?,?,?,?)",
                postId, discussionId, userId, body, quotedPostId);
        jdbc.update("UPDATE discussions SET reply_count = reply_count + 1, last_reply_at = NOW() WHERE id = ?", discussionId);
        return detail(discussionId, userId);
    }

    /** 采纳：仅楼主，且只能采纳本主题帖下的楼层。 */
    public Map<String, Object> accept(long userId, long discussionId, long postId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT author_id FROM discussions WHERE id = ?", discussionId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "帖子不存在");
        if (((Number) rows.getFirst().get("author_id")).longValue() != userId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有楼主可以采纳回复");
        }
        int updated = jdbc.update("UPDATE discussion_posts SET accepted = 1 WHERE id = ? AND discussion_id = ?", postId, discussionId);
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "回复不存在");
        jdbc.update("UPDATE discussions SET accepted_post_id = ? WHERE id = ?", postId, discussionId);
        return detail(discussionId, userId);
    }

    /** 管理端：置顶 / 取消置顶。 */
    public Map<String, Object> setPinned(long adminId, long id, boolean value) {
        if (jdbc.update("UPDATE discussions SET pinned = ? WHERE id = ?", value, id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "帖子不存在");
        }
        audit(adminId, value ? "DISCUSSION_PIN" : "DISCUSSION_UNPIN", id);
        return detail(id, adminId);
    }

    /** 管理端：锁定 / 解锁（锁定后不能再回复，回复接口会返回 409）。 */
    public Map<String, Object> setLocked(long adminId, long id, boolean value) {
        if (jdbc.update("UPDATE discussions SET locked = ? WHERE id = ?", value, id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "帖子不存在");
        }
        audit(adminId, value ? "DISCUSSION_LOCK" : "DISCUSSION_UNLOCK", id);
        return detail(id, adminId);
    }

    /** 管理端：删除主题帖及其全部楼层。 */
    public Map<String, Object> deleteThread(long adminId, long id) {
        if (jdbc.queryForList("SELECT id FROM discussions WHERE id = ?", id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "帖子不存在");
        }
        jdbc.update("DELETE FROM discussion_posts WHERE discussion_id = ?", id);
        jdbc.update("DELETE FROM discussions WHERE id = ?", id);
        audit(adminId, "DISCUSSION_DELETE", id);
        return Map.of("deleted", id);
    }

    /**
     * Agent 摘要：楼主或管理员触发，结果落库只读展示。
     * 赛中关联赛题的帖子**不允许**生成摘要 —— 否则摘要会变成变相提示（复用同一套竞赛锁判定）。
     */
    public Map<String, Object> summarize(long userId, long id, boolean admin) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT d.title, d.body_md, d.author_id, p.slug AS problem_slug FROM discussions d "
                        + "LEFT JOIN problems p ON p.id = d.problem_id WHERE d.id = ?", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "帖子不存在");
        Map<String, Object> head = rows.getFirst();
        if (!admin && ((Number) head.get("author_id")).longValue() != userId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有楼主或管理员可以生成摘要");
        }
        Object slug = head.get("problem_slug");
        if (slug != null) requireNotContestProblem(admin ? 0L : userId, String.valueOf(slug));
        if (deepSeek == null || !deepSeek.configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "模型未配置，暂时无法生成摘要");
        }
        List<Map<String, Object>> posts = jdbc.queryForList(
                "SELECT u.display_name AS author, c.body_md AS body FROM discussion_posts c JOIN users u ON u.id = c.author_id "
                        + "WHERE c.discussion_id = ? ORDER BY c.created_at LIMIT 30", id);
        StringBuilder material = new StringBuilder();
        material.append("标题：").append(head.get("title")).append("\n\n正文：\n").append(head.get("body_md")).append("\n\n");
        for (Map<String, Object> post : posts) {
            material.append("回复（").append(post.get("author")).append("）：\n").append(post.get("body")).append("\n---\n");
        }
        String context = material.length() > 6000 ? material.substring(0, 6000) : material.toString();
        String system = "你是编程学习社区的助手。只根据用户提供的帖子内容，输出三段 Markdown："
                + "## 要点（不超过 4 条）、## 高频坑（没有写「暂无」）、## 仍待解决（没有写「暂无」）。"
                + "不要编造帖子里没有的信息，也不要给出完整可提交的题解代码。";
        String summary = deepSeek.answer(system, context);
        jdbc.update("UPDATE discussions SET summary_md = ?, summary_at = NOW() WHERE id = ?", summary, id);
        audit(userId, "DISCUSSION_SUMMARY", id);
        return Map.of("summaryMd", summary);
    }

    /** 管理动作审计（失败不影响主流程）。 */
    private void audit(long adminId, String action, long targetId) {
        try {
            jdbc.update("INSERT INTO admin_audit_log (id,admin_user_id,action,target_type,target_id,detail_json) "
                    + "VALUES (?,?,?,'DISCUSSION',?,NULL)", nextId(), adminId, action, targetId);
        } catch (RuntimeException ignored) {
            // 审计失败不应影响管理操作本身
        }
    }
}