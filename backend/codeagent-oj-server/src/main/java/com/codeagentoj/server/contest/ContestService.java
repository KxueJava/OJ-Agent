package com.codeagentoj.server.contest;

import static com.codeagentoj.server.contest.ContestDtos.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 竞赛管理（P1：草稿与配置 + 发布校验预检）。
 *
 * <p>可编辑性由状态决定：只有 DRAFT 与 SCHEDULED 允许改配置/改题；
 * 发布后的守卫与状态推进在 P2 接入，这里先把 unpublished 的校验语义固定下来。
 */
@Service
public class ContestService {
    private static final Logger log = LoggerFactory.getLogger(ContestService.class);
    private static final String LABELS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final boolean agentLockedEnabled;

    public ContestService(JdbcTemplate jdbc, ObjectMapper mapper,
                          @Value("${app.contest.agent-locked:true}") boolean agentLockedEnabled) {
        this.jdbc = jdbc; this.mapper = mapper; this.agentLockedEnabled = agentLockedEnabled;
    }

    /**
     * 我报名参加的比赛（列表页「我参加的」区块用）。列名显式别名成驼峰，避免前端处理 snake_case；
     * rank/solved/penalty 取定榜快照（未定榜时为 null）。
     */
    public List<Map<String, Object>> myContests(long userId) {
        return jdbc.queryForList("SELECT c.id AS id, c.slug AS slug, c.title AS title, c.status AS status, "
                + "c.start_at AS startAt, c.end_at AS endAt, c.freeze_minutes AS freezeMinutes, c.penalty_minutes AS penaltyMinutes, "
                + "p.rank_final AS rankFinal, p.solved_final AS solvedFinal, p.penalty_final AS penaltyFinal "
                + "FROM contest_participants p JOIN contests c ON c.id=p.contest_id "
                + "WHERE p.user_id=? ORDER BY c.start_at DESC LIMIT 50", userId);
    }

    /**
     * 竞赛期间的 Agent 锁（按题目判定）：本人已报名 + 比赛正在进行 + **这道题是该场赛题** → 返回该场 slug，否则 null。
     *
     * 早期版本只看"是否报名了进行中的比赛"，会把普通题库的题也锁掉（用户实测反馈），所以现在必须带题目上下文；
     * 不带题目上下文的调用（如首页）一律不锁。整体开关见 app.contest.agent-locked。
     */
    public String lockedContestSlugForProblem(long userId, String problemSlug) {
        if (!agentLockedEnabled) return null;
        // 不带题目上下文（如首页卡片）→ 返回"我参加的任意进行中比赛"的全局判定；
        // 带题号时要求"这道题正是该场赛题"，避免赛中做普通题被误锁（用户实测反馈）。
        if (problemSlug == null || problemSlug.isBlank()) return lockedContestSlugAny(userId);
        List<String> slugs = jdbc.queryForList("SELECT c.slug FROM contests c "
                + "JOIN contest_participants p ON p.contest_id=c.id AND p.user_id=? "
                + "JOIN contest_problems cp ON cp.contest_id=c.id "
                + "JOIN problems pr ON pr.id=cp.problem_id "
                + "WHERE c.status='RUNNING' AND NOW()>=c.start_at AND NOW()<c.end_at AND pr.slug=? LIMIT 1", String.class, userId, problemSlug);
        return slugs.isEmpty() ? null : slugs.getFirst();
    }

    /** 全局判定：我参加的任意一场正在进行中的比赛（首页等无题目上下文的场景用）。 */
    public String lockedContestSlugAny(long userId) {
        if (!agentLockedEnabled) return null;
        List<String> slugs = jdbc.queryForList("SELECT c.slug FROM contests c "
                + "JOIN contest_participants p ON p.contest_id=c.id AND p.user_id=? "
                + "WHERE c.status='RUNNING' AND NOW()>=c.start_at AND NOW()<c.end_at LIMIT 1", String.class, userId);
        return slugs.isEmpty() ? null : slugs.getFirst();
    }

    /** 同上，但用题目版本 id（Agent 请求里带的是 problemVersion）。 */
    public String lockedContestSlugForVersion(long userId, Long problemVersionId) {
        if (!agentLockedEnabled || problemVersionId == null) return null;
        List<String> slugs = jdbc.queryForList("SELECT c.slug FROM contests c "
                + "JOIN contest_participants p ON p.contest_id=c.id AND p.user_id=? "
                + "JOIN contest_problems cp ON cp.contest_id=c.id "
                + "JOIN problem_versions pv ON pv.problem_id=cp.problem_id "
                + "WHERE c.status='RUNNING' AND NOW()>=c.start_at AND NOW()<c.end_at AND pv.id=? LIMIT 1", String.class, userId, problemVersionId);
        return slugs.isEmpty() ? null : slugs.getFirst();
    }

    @Transactional public ContestDetail create(CreateRequest request, long adminId) {
        if (!jdbc.queryForList("SELECT id FROM contests WHERE slug=?", request.slug()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该竞赛标识已被占用：" + request.slug());
        }
        long id = nextId();
        jdbc.update("INSERT INTO contests (id,slug,title,description_md,mode,status,start_at,end_at,freeze_minutes,penalty_minutes,max_participants,password_hash,created_by) "
                        + "VALUES (?,?,?,?,'ACM','DRAFT',?,?,?,?,?,?,?)",
                id, request.slug(), request.title(), request.descriptionMd(),
                Timestamp.from(request.startAt() == null ? Instant.now().plusSeconds(86400) : request.startAt()),
                Timestamp.from(request.endAt() == null ? Instant.now().plusSeconds(86400 + 7200) : request.endAt()),
                request.freezeMinutes() == null ? 20 : request.freezeMinutes(),
                request.penaltyMinutes() == null ? 20 : request.penaltyMinutes(),
                request.maxParticipants(), hash(request.password()), adminId);
        audit(adminId, "CONTEST_CREATE", id, Map.of("slug", request.slug(), "title", request.title()));
        return detail(id);
    }

    @Transactional public ContestDetail update(long id, UpdateRequest request, long adminId) {
        Map<String, Object> current = requireEditable(id);
        Instant startAt = request.startAt() == null ? time(current.get("start_at")) : request.startAt();
        Instant endAt = request.endAt() == null ? time(current.get("end_at")) : request.endAt();
        jdbc.update("UPDATE contests SET title=?,description_md=?,start_at=?,end_at=?,freeze_minutes=?,penalty_minutes=?,max_participants=?,password_hash=? WHERE id=?",
                request.title() == null ? current.get("title") : request.title(),
                request.descriptionMd() == null ? current.get("description_md") : request.descriptionMd(),
                Timestamp.from(startAt), Timestamp.from(endAt),
                request.freezeMinutes() == null ? current.get("freeze_minutes") : request.freezeMinutes(),
                request.penaltyMinutes() == null ? current.get("penalty_minutes") : request.penaltyMinutes(),
                request.maxParticipants() == null ? current.get("max_participants") : request.maxParticipants(),
                request.password() == null ? current.get("password_hash") : hash(request.password()),
                id);
        audit(adminId, "CONTEST_UPDATE", id, Map.of("title", String.valueOf(request.title())));
        return detail(id);
    }

    /** 加题：自动分配下一个题号（A、B、C…），并钉住当前已发布的题目版本。 */
    @Transactional public ContestDetail addProblem(long id, AddProblemRequest request, long adminId) {
        requireEditable(id);
        Map<String, Object> version = jdbc.queryForList(
                        "SELECT pv.id AS version_id, pv.problem_id, p.slug, p.title FROM problem_versions pv JOIN problems p ON p.id=pv.problem_id "
                                + "WHERE p.slug=? AND pv.status='PUBLISHED' AND p.status='PUBLISHED' ORDER BY pv.version_no DESC LIMIT 1", request.problemSlug())
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "题目不存在或没有已发布的版本：" + request.problemSlug()));
        List<Map<String, Object>> existing = jdbc.queryForList("SELECT id,problem_id,label,display_order FROM contest_problems WHERE contest_id=? ORDER BY display_order", id);
        long problemId = ((Number) version.get("problem_id")).longValue();
        for (Map<String, Object> row : existing) {
            if (((Number) row.get("problem_id")).longValue() == problemId) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "该题已在竞赛中（题号 " + row.get("label") + "）");
            }
        }
        if (existing.size() >= LABELS.length()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "题目数量已达上限");
        jdbc.update("INSERT INTO contest_problems (id,contest_id,problem_id,problem_version_id,label,score,display_order) VALUES (?,?,?,?,?,?,?)",
                nextId(), id, problemId, ((Number) version.get("version_id")).longValue(),
                String.valueOf(LABELS.charAt(existing.size())), request.score() == null ? 100 : request.score(), existing.size());
        audit(adminId, "CONTEST_ADD_PROBLEM", id, Map.of("slug", request.problemSlug()));
        return detail(id);
    }

    /** 删题并重排题号，避免出现 A、C、D 这样的空洞（校验也会拦，但这里直接修好）。 */
    @Transactional public ContestDetail removeProblem(long id, long problemId, long adminId) {
        requireEditable(id);
        if (jdbc.update("DELETE FROM contest_problems WHERE contest_id=? AND problem_id=?", id, problemId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "竞赛中不存在该题目");
        }
        List<Long> remaining = jdbc.queryForList("SELECT id FROM contest_problems WHERE contest_id=? ORDER BY display_order", Long.class, id);
        for (int index = 0; index < remaining.size(); index++) {
            jdbc.update("UPDATE contest_problems SET label=?,display_order=? WHERE id=?", String.valueOf(LABELS.charAt(index)), index, remaining.get(index));
        }
        audit(adminId, "CONTEST_REMOVE_PROBLEM", id, Map.of("problemId", problemId));
        return detail(id);
    }

    public List<ContestRow> list(String status, int page, int size) {
        int safeSize = Math.max(1, Math.min(50, size));
        String where = status == null || status.isBlank() ? "" : " WHERE c.status=?";
        List<Object> args = new ArrayList<>();
        if (!where.isEmpty()) args.add(status.toUpperCase());
        args.add(safeSize);
        args.add(Math.max(0, page) * safeSize);
        return jdbc.query(rowSelect() + where + " ORDER BY c.start_at DESC LIMIT ? OFFSET ?", (rs, n) -> mapRow(rs), args.toArray());
    }

    public ContestDetail detail(long id) {
        List<ContestRow> rows = jdbc.query(rowSelect() + " WHERE c.id=?", (rs, n) -> mapRow(rs), id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "竞赛不存在");
        List<ProblemView> problems = jdbc.query(
                "SELECT cp.id,cp.problem_id,cp.problem_version_id,cp.label,cp.score,cp.display_order,p.slug,p.title,p.difficulty,pv.status AS version_status "
                        + "FROM contest_problems cp JOIN problems p ON p.id=cp.problem_id JOIN problem_versions pv ON pv.id=cp.problem_version_id "
                        + "WHERE cp.contest_id=? ORDER BY cp.display_order", (rs, n) -> new ProblemView(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                        rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getString(7), rs.getString(8), rs.getString(9)), id);
        List<IssueView> issues = validate(id, rows.getFirst(), problems);
        String description = jdbc.queryForObject("SELECT description_md FROM contests WHERE id=?", String.class, id);
        return new ContestDetail(rows.getFirst(), description, problems, issues, announcements(id), ContestStatus.EDITABLE.contains(rows.getFirst().status()));
    }

    /**
     * 发布：先跑发布校验，**不通过就逐条返回原因**（不是抛一句"发布失败"）。
     * 校验通过后置为 SCHEDULED 并记录 published_at；到点由 ContestScheduler 自动推进。
     */
    @Transactional public PublishResult publish(long id, long adminId) {
        String status = status(id);
        if (!ContestStatus.canPublish(status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "竞赛当前状态为 " + status + "，不能发布");
        ContestDetail detail = detail(id);
        if (!detail.issues().isEmpty()) return new PublishResult(false, detail.issues(), detail.contest());
        jdbc.update("UPDATE contests SET status='SCHEDULED', published_at=NOW() WHERE id=? AND status IN ('DRAFT','SCHEDULED')", id);
        audit(adminId, "CONTEST_PUBLISH", id, Map.of("slug", detail.contest().slug()));
        return new PublishResult(true, List.of(), detail(id).contest());
    }

    @Transactional public ContestDetail cancel(long id, long adminId) {
        String status = status(id);
        if (!ContestStatus.canCancel(status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "竞赛当前状态为 " + status + "，不能取消");
        jdbc.update("UPDATE contests SET status='CANCELLED' WHERE id=? AND status IN ('DRAFT','SCHEDULED','RUNNING')", id);
        audit(adminId, "CONTEST_CANCEL", id, Map.of("from", status));
        return detail(id);
    }

    /** 定榜：只有 ENDED 可定榜，且不可重复。名次快照的计算在 P5 落地，这里先完成状态与时间戳。 */
    @Transactional public ContestDetail finalizeContest(long id, long adminId) {
        String status = status(id);
        if (!ContestStatus.canFinalize(status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已结束（ENDED）的竞赛才能定榜，当前为 " + status);
        jdbc.update("UPDATE contests SET status='FINALIZED', finalized_at=NOW() WHERE id=? AND status='ENDED'", id);
        // 定榜快照：把最终名次写进参赛者表，之后榜单的 名次/已解/罚时 不再随提交或重判变化
        List<String> slugs = jdbc.queryForList("SELECT slug FROM contests WHERE id=?", String.class, id);
        int ranked = 0;
        if (!slugs.isEmpty()) {
            for (StandingView row : liveStandings(slugs.getFirst(), true)) {
                ranked += jdbc.update("UPDATE contest_participants SET rank_final=?,solved_final=?,penalty_final=? WHERE contest_id=? AND user_id=?",
                        row.rank(), row.solved(), row.penaltySeconds(), id, row.userId());
            }
        }
        audit(adminId, "CONTEST_FINALIZE", id, Map.of("from", status, "ranked", ranked));
        return detail(id);
    }

    @Transactional public List<AnnouncementView> announce(long id, String body, long adminId) {
        String status = status(id);
        if (!ContestStatus.canAnnounce(status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "当前状态（" + status + "）不能发公告");
        jdbc.update("INSERT INTO contest_announcements (id,contest_id,body,created_by) VALUES (?,?,?,?)", nextId(), id, body, adminId);
        audit(adminId, "CONTEST_ANNOUNCE", id, Map.of("length", body.length()));
        return announcements(id);
    }

    public List<AnnouncementView> announcements(long id) {
        return jdbc.query("SELECT id,body,created_at FROM contest_announcements WHERE contest_id=? ORDER BY created_at DESC",
                (rs, n) -> new AnnouncementView(rs.getLong(1), rs.getString(2), time(rs.getObject(3))), id);
    }

    /** 公开列表：只暴露已发布过的比赛（DRAFT 与 CANCELLED 不可见）。 */
    public List<ContestRow> publicList(int page, int size) {
        int safeSize = Math.max(1, Math.min(50, size));
        return jdbc.query(rowSelect() + " WHERE c.status IN ('SCHEDULED','RUNNING','ENDED','FINALIZED') ORDER BY c.start_at DESC LIMIT ? OFFSET ?",
                (rs, n) -> mapRow(rs), safeSize, Math.max(0, page) * safeSize);
    }

    /** 公开详情：未发布/已取消返回 404；SCHEDULED 阶段隐藏题目标题与标识；登录时附带"我是否已报名"。 */
    public PublicContestDetail publicDetail(String slug, Long userId) {
        ContestRow row = publicRow(slug);
        boolean hidden = ContestStatus.SCHEDULED.equals(row.status());
        List<PublicProblemView> problems = jdbc.query(
                "SELECT cp.label,cp.score,cp.display_order,p.slug,p.title,p.difficulty FROM contest_problems cp JOIN problems p ON p.id=cp.problem_id "
                        + "WHERE cp.contest_id=? ORDER BY cp.display_order",
                (rs, n) -> new PublicProblemView(rs.getString(1), rs.getInt(2), rs.getInt(3),
                        hidden ? null : rs.getString(4), hidden ? null : rs.getString(5), hidden ? null : rs.getString(6)), row.id());
        String description = jdbc.queryForObject("SELECT description_md FROM contests WHERE id=?", String.class, row.id());
        boolean registered = userId != null && jdbc.queryForList("SELECT 1 FROM contest_participants WHERE contest_id=? AND user_id=?", row.id(), userId).size() > 0;
        return new PublicContestDetail(row, description, problems, announcements(row.id()), hidden, registered);
    }

    public PublicContestDetail publicDetail(String slug) { return publicDetail(slug, null); }

    /** 公开可见性收敛到一处：DRAFT / CANCELLED 对外一律 404。 */
    private ContestRow publicRow(String slug) {
        List<ContestRow> rows = jdbc.query(rowSelect() + " WHERE c.slug=? AND c.status IN ('SCHEDULED','RUNNING','ENDED','FINALIZED')", (rs, n) -> mapRow(rs), slug);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "竞赛不存在或尚未发布");
        return rows.getFirst();
    }

    /** 报名：未开始与进行中都可报，已结束不可；重复报名幂等（唯一键 + INSERT IGNORE）。 */
    @Transactional public RegisterResult register(String slug, long userId) {
        ContestRow row = publicRow(slug);
        if (ContestStatus.ENDED.equals(row.status()) || ContestStatus.FINALIZED.equals(row.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "比赛已结束，无法报名");
        }
        jdbc.update("INSERT IGNORE INTO contest_participants (id,contest_id,user_id) VALUES (?,?,?)", nextId(), row.id(), userId);
        return new RegisterResult(true, row.id(), row.status(), count("SELECT COUNT(*) FROM contest_participants WHERE contest_id=?", row.id()));
    }

    /**
     * 榜单：includeFrozen=false 为选手视角（冻结期后的提交不计入），true 为管理员视角。
     * 用户名单独查（每场参与人数有限，N+1 可接受；人数上千再改联表）。
     */
    /**
     * 榜单入口：定榜（FINALIZED）后返回**快照**的名次/已解/罚时（每题格子仍按当前数据算，便于赛后复盘），
     * 否则实时计算；includeFrozen=false 为选手视角（冻结期内的提交不计入）。
     */
    public List<StandingView> standings(String slug, boolean includeFrozen) {
        ContestRow row = publicRow(slug);
        boolean finalized = ContestStatus.FINALIZED.equals(row.status());
        // 冻结只在"进行中"才有意义：已结束/已定榜后对所有人解冻（真实 ACM 赛制是结束即揭晓）
        List<StandingView> live = liveStandings(slug, includeFrozen || finalized || !ContestStatus.RUNNING.equals(row.status()));
        if (!finalized) return live;
        Map<Long, List<CellView>> cellsByUser = new java.util.HashMap<>();
        for (StandingView item : live) cellsByUser.put(item.userId(), item.cells());
        return jdbc.query("SELECT p.user_id,p.rank_final,p.solved_final,p.penalty_final,u.username,u.display_name FROM contest_participants p "
                        + "JOIN users u ON u.id=p.user_id WHERE p.contest_id=? AND p.rank_final IS NOT NULL ORDER BY p.rank_final",
                (rs, n) -> {
                    long userId = rs.getLong(1);
                    return new StandingView(rs.getInt(2), userId, rs.getString(5), rs.getString(6), rs.getInt(3), rs.getLong(4),
                            cellsByUser.getOrDefault(userId, List.of()));
                }, row.id());
    }

    private List<StandingView> liveStandings(String slug, boolean includeFrozen) {
        ContestRow row = publicRow(slug);
        List<String> labels = jdbc.queryForList("SELECT label FROM contest_problems WHERE contest_id=? ORDER BY display_order", String.class, row.id());
        if (labels.isEmpty()) return List.of();
        Instant freezeAt = row.freezeMinutes() <= 0 ? null : row.endAt().minusSeconds(row.freezeMinutes() * 60L);
        List<StandingsCalculator.Submission> submissions = jdbc.query(
                "SELECT s.user_id, cp.label, s.created_at, s.status FROM submissions s "
                        + "JOIN contest_problems cp ON cp.contest_id=? AND cp.problem_id=s.problem_id WHERE s.contest_id=?",
                (rs, n) -> new StandingsCalculator.Submission(rs.getLong(1), rs.getString(2), time(rs.getObject(3)), "AC".equals(rs.getString(4))), row.id(), row.id());
        List<StandingsCalculator.Row> computed = StandingsCalculator.compute(submissions, labels, row.startAt(), row.endAt(), freezeAt, row.penaltyMinutes(), includeFrozen);
        List<StandingView> views = new ArrayList<>();
        int rank = 1;
        for (StandingsCalculator.Row item : computed) {
            Map<String, Object> user = jdbc.queryForList("SELECT username,display_name FROM users WHERE id=?", item.userId()).stream().findFirst().orElse(Map.of());
            views.add(new StandingView(rank++, item.userId(), String.valueOf(user.getOrDefault("username", "?")),
                    String.valueOf(user.getOrDefault("display_name", "?")), item.solved(), item.penaltySeconds(),
                    item.cells().stream().map(cell -> new CellView(cell.label(), cell.state(), cell.acceptedAtSeconds(), cell.wrongAttempts())).toList()));
        }
        return views;
    }

    /** 管理员视角榜单：按竞赛 id 查询，不受冻结限制（供 /api/admin 使用）。 */
    public List<StandingView> standingsByContestId(long contestId, boolean includeFrozen) {
        List<String> slugs = jdbc.queryForList("SELECT slug FROM contests WHERE id=?", String.class, contestId);
        if (slugs.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "竞赛不存在");
        return standings(slugs.getFirst(), includeFrozen);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private String status(long id) {
        List<String> rows = jdbc.queryForList("SELECT status FROM contests WHERE id=?", String.class, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "竞赛不存在");
        return rows.getFirst();
    }

    /** 发布校验预检：把当前库里的真实状态喂给纯函数校验器。 */
    public List<IssueView> validate(long id, ContestRow row, List<ProblemView> problems) {
        List<ContestValidator.Problem> snapshot = new ArrayList<>();
        for (ProblemView problem : problems) {
            boolean published = "PUBLISHED".equals(versionStatus(problem.problemVersionId()));
            snapshot.add(new ContestValidator.Problem(problem.problemVersionId(), problem.label(), problem.score(), problem.displayOrder(), published));
        }
        return ContestValidator.validate(new ContestValidator.Draft(row.slug(), row.title(), row.startAt(), row.endAt(),
                row.freezeMinutes(), row.penaltyMinutes(), snapshot), Instant.now()).stream().map(issue -> new IssueView(issue.code(), issue.message())).toList();
    }

    private String versionStatus(long problemVersionId) {
        List<String> status = jdbc.queryForList("SELECT status FROM problem_versions WHERE id=?", String.class, problemVersionId);
        return status.isEmpty() ? "MISSING" : status.getFirst();
    }

    private Map<String, Object> requireEditable(long id) {
        Map<String, Object> row = jdbc.queryForList("SELECT * FROM contests WHERE id=?", id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "竞赛不存在"));
        String status = (String) row.get("status");
        if (!ContestStatus.EDITABLE.contains(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "竞赛当前状态为 " + status + "，不可再修改配置");
        }
        return row;
    }

    private String rowSelect() {
        return "SELECT c.id,c.slug,c.title,c.mode,c.status,c.start_at,c.end_at,c.freeze_minutes,c.penalty_minutes,c.published_at,c.finalized_at,c.updated_at,"
                + "(SELECT COUNT(*) FROM contest_problems cp WHERE cp.contest_id=c.id) AS problem_count,"
                + "(SELECT COUNT(*) FROM contest_participants cpa WHERE cpa.contest_id=c.id) AS participant_count FROM contests c";
    }

    private ContestRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ContestRow(rs.getLong("id"), rs.getString("slug"), rs.getString("title"), rs.getString("mode"), rs.getString("status"),
                time(rs.getObject("start_at")), time(rs.getObject("end_at")), rs.getInt("freeze_minutes"), rs.getInt("penalty_minutes"),
                rs.getInt("problem_count"), rs.getLong("participant_count"),
                time(rs.getObject("published_at")), time(rs.getObject("finalized_at")), time(rs.getObject("updated_at")));
    }

    private void audit(long adminId, String action, long targetId, Map<String, Object> detail) {
        try {
            jdbc.update("INSERT INTO admin_audit_log (id,admin_user_id,action,target_type,target_id,detail_json) VALUES (?,?,?,'CONTEST',?,?)",
                    nextId(), adminId, action, targetId, mapper.writeValueAsString(new LinkedHashMap<>(detail)));
        } catch (JsonProcessingException error) {
            log.warn("审计写入失败 action={} target={}", action, targetId, error);
        }
    }

    private String hash(String password) {
        if (password == null || password.isBlank()) return null;
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static Instant time(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        return ((java.time.LocalDateTime) value).atZone(java.time.ZoneId.systemDefault()).toInstant();
    }

    private static long nextId() { return Math.abs(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)); }
}
