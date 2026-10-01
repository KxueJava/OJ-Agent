package com.codeagentoj.server.submission;

import static com.codeagentoj.server.submission.SubmissionDtos.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SubmissionService {
    private static final Logger log = LoggerFactory.getLogger(SubmissionService.class);
    private final JdbcTemplate jdbc; private final ObjectMapper mapper; private final RabbitTemplate rabbit; private final String queue;
    private final int outboxMaxAttempts; private final int maxPending; private final SubmissionRateLimiter limiter;
    public SubmissionService(JdbcTemplate jdbc, ObjectMapper mapper, RabbitTemplate rabbit,
                             @Value("${app.judge.queue:oj.submissions}") String queue,
                             @Value("${app.judge.outbox.max-attempts:8}") int outboxMaxAttempts,
                             @Value("${app.judge.rate.per-minute:6}") int perMinute,
                             @Value("${app.judge.rate.per-hour:60}") int perHour,
                             @Value("${app.judge.rate.max-pending:500}") int maxPending) {
        this.jdbc=jdbc; this.mapper=mapper; this.rabbit=rabbit; this.queue=queue;
        this.outboxMaxAttempts=Math.max(1,outboxMaxAttempts); this.maxPending=Math.max(1,maxPending);
        this.limiter=new SubmissionRateLimiter(perMinute,perHour);
    }
    @Transactional public Summary create(long userId, CreateRequest req) {
        if (!supportedLanguage(req.language())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"当前仅支持 Java 21、C++17 和 C17");
        // 限流：先算、被拒不计入；再做队列保护，避免雪崩式堆积
        long now=System.currentTimeMillis();
        SubmissionRateLimiter.Decision decision=limiter.check(userId,now);
        if (decision!=SubmissionRateLimiter.Decision.ALLOW) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                decision==SubmissionRateLimiter.Decision.PER_MINUTE?"提交过于频繁（每分钟上限已到），请稍后再试":"本小时提交次数已达上限，请稍后再试");
        Long waiting=jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE status='PENDING'",Long.class);
        if (waiting!=null&&waiting>=maxPending) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"判题队列繁忙（待处理 "+waiting+" 条），请稍后重试");
        limiter.record(userId,now);
        String language=canonicalLanguage(req.language());
        Map<String,Object> version = jdbc.queryForList("SELECT pv.id,pv.problem_id,p.status FROM problem_versions pv JOIN problems p ON p.id=pv.problem_id WHERE pv.id=? AND pv.status='PUBLISHED' AND p.status='PUBLISHED'",req.problemVersion()).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"题目版本不可提交"));
        long id=nextId(); jdbc.update("INSERT INTO submissions (id,user_id,problem_id,problem_version_id,language,source_code,status) VALUES (?,?,?,?,?,?,?)",id,userId,version.get("problem_id"),req.problemVersion(),language,req.sourceCode(),"PENDING");
        try { jdbc.update("INSERT INTO outbox_events (id,aggregate_id,event_type,payload_json) VALUES (?,?,?,?)",nextId(),id,"SUBMISSION_CREATED",mapper.writeValueAsString(Map.of("submissionId",id,"language",language,"problemVersionId",req.problemVersion()))); } catch(JsonProcessingException e){ throw new IllegalStateException(e); }
        return summary(id,userId);
    }
    /**
     * outbox 投递：失败按 attempts 递增退避重试，超过上限落 DEAD（死信）并打告警日志。
     * 之前是无限重试（永远 3 秒一次），一条坏消息会长时间占着轮询窗口且没人发现。
     */
    @Scheduled(fixedDelayString="${app.judge.poll-ms:500}") public void publishOutbox(){ List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,aggregate_id,payload_json,attempts FROM outbox_events WHERE status='PENDING' AND available_at<=CURRENT_TIMESTAMP ORDER BY created_at LIMIT 20"); for(var row:rows){long id=((Number)row.get("id")).longValue(); int attempts=((Number)row.get("attempts")).intValue()+1; try { rabbit.convertAndSend(queue,row.get("payload_json")); jdbc.update("UPDATE outbox_events SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP,attempts=? WHERE id=? AND status='PENDING'",attempts,id); } catch(Exception e){ String error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(); if(attempts>=outboxMaxAttempts){ jdbc.update("UPDATE outbox_events SET attempts=?,last_error=?,status='DEAD',dead_lettered_at=CURRENT_TIMESTAMP WHERE id=?",attempts,error,id); log.error("OUTBOX_DEAD event_id={} submission={} attempts={} error={} —— 已停止重试，可在管理端 /api/admin/judge/queue 查看并重投",id,row.get("aggregate_id"),attempts,error); } else { int backoffSeconds=Math.min(60,3*attempts); jdbc.update("UPDATE outbox_events SET attempts=?,last_error=?,available_at=DATE_ADD(CURRENT_TIMESTAMP,INTERVAL ? SECOND) WHERE id=?",attempts,error,backoffSeconds,id); } } } }
    public Summary summary(long id,long userId){ List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,status,verdict_message,failure_kind,runtime_ms,compile_ms,memory_kb,rejudge_count,created_at,finished_at FROM submissions WHERE id=? AND user_id=?",id,userId); if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"提交不存在"); var r=rows.getFirst(); return new Summary(((Number)r.get("id")).longValue(),(String)r.get("status"),(String)r.get("verdict_message"),(Integer)r.get("runtime_ms"),(Integer)r.get("memory_kb"),time(r.get("created_at")),time(r.get("finished_at")),(Integer)r.get("compile_ms"),(String)r.get("failure_kind"),r.get("rejudge_count")==null?0:((Number)r.get("rejudge_count")).intValue()); }

    /**
     * 重判：把提交复位为 PENDING 并重新投递判题事件（复用既有 outbox → MQ → worker 链路，worker 无需改动）。
     * 旧的判题用例与旧的自动诊断一并清除，避免新旧结果混在一起。
     */
    @Transactional public Summary rejudge(long id,long userId){
        if(reset(id,userId) == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"提交不存在，或仍在判题中，无法重判");
        return summary(id,userId);
    }

    /** 管理员按题目版本批量重判：改过测试数据或升级沙箱后使用。 */
    @Transactional public int rejudgeByVersion(long problemVersionId,int limit){
        List<Long> ids=jdbc.queryForList("SELECT id FROM submissions WHERE problem_version_id=? AND status IN ('AC','WA','CE','RE','TLE','MLE') ORDER BY created_at DESC LIMIT ?",Long.class,problemVersionId,Math.max(1,Math.min(500,limit)));
        int count=0; for(Long id:ids){ if(reset(id,null)==1) count++; } return count;
    }

    /** 复位 + 清派生数据 + 重新投递；userId 为 null 表示不限定用户（管理员批量）。 */
    private int reset(long id,Long userId){
        // 注意：这里是 UPDATE，不能用查询里的表别名（s.user_id）——只有限定用户时才加条件
        List<Object> args=new ArrayList<>(); args.add(id); if(userId!=null) args.add(userId);
        int changed=jdbc.update("UPDATE submissions SET status='PENDING',verdict_message=NULL,failure_kind=NULL,runtime_ms=NULL,compile_ms=NULL,memory_kb=NULL,started_at=NULL,finished_at=NULL,rejudge_count=rejudge_count+1 WHERE id=? AND status IN ('AC','WA','CE','RE','TLE','MLE')"+(userId==null?"":" AND user_id=?"),args.toArray());
        if(changed==0) return 0;
        jdbc.update("DELETE FROM submission_cases WHERE submission_id=?",id);
        jdbc.update("DELETE FROM agent_findings WHERE submission_id=? AND kind='DIAGNOSIS'",id);
        Map<String,Object> row=jdbc.queryForList("SELECT language,problem_version_id FROM submissions WHERE id=?",id).getFirst();
        try { jdbc.update("INSERT INTO outbox_events (id,aggregate_id,event_type,payload_json) VALUES (?,?,?,?)",nextId(),id,"SUBMISSION_CREATED",mapper.writeValueAsString(Map.of("submissionId",id,"language",row.get("language"),"problemVersionId",((Number)row.get("problem_version_id")).longValue()))); }
        catch(JsonProcessingException e){ throw new IllegalStateException(e); }
        return 1;
    }
    public Detail detail(long id,long userId){ Summary s=summary(id,userId); List<CaseSummary> cases=jdbc.query("SELECT sc.verdict,sc.runtime_ms,sc.memory_kb,sc.output_summary FROM submission_cases sc JOIN test_cases tc ON tc.id=sc.test_case_id WHERE sc.submission_id=? AND tc.visibility='PUBLIC' ORDER BY sc.id",(rs,n)->new CaseSummary(n+1,rs.getString(1),rs.getObject(2,Integer.class),rs.getObject(3,Integer.class),rs.getString(4)),id); Map<String,Object> row=jdbc.queryForList("SELECT s.language,s.source_code,p.slug FROM submissions s JOIN problems p ON p.id=s.problem_id WHERE s.id=? AND s.user_id=?",id,userId).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"提交不存在")); return new Detail(s,cases,(String)row.get("language"),(String)row.get("source_code"),(String)row.get("slug")); }
    public List<Event> events(long id,long userId){ Summary s=summary(id,userId); List<Event> events=new ArrayList<>(); events.add(new Event("status",s.status(),s.verdictMessage(),s.finishedAt()==null?s.createdAt():s.finishedAt())); return events; }

    /**
     * 当前用户的提交记录分页列表。
     * 对象级授权：SQL 里恒带 user_id=?，别人的提交既不会出现在列表里，也查不到详情（detail 同样带 user_id）。
     */
    public Page list(long userId,int page,int size,String status,String slug){
        int safeSize=Math.max(1,Math.min(50,size)); int safePage=Math.max(0,page);
        StringBuilder where=new StringBuilder(" WHERE s.user_id=?");
        List<Object> args=new ArrayList<>(); args.add(userId);
        if(status!=null&&!status.isBlank()){ where.append(" AND s.status=?"); args.add(status.toUpperCase()); }
        if(slug!=null&&!slug.isBlank()){ where.append(" AND p.slug=?"); args.add(slug); }
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM submissions s JOIN problems p ON p.id=s.problem_id"+where,Long.class,args.toArray());
        List<Object> pageArgs=new ArrayList<>(args); pageArgs.add(safeSize); pageArgs.add(safePage*safeSize);
        List<ListItem> items=jdbc.query("SELECT s.id,p.slug,p.title,s.language,s.status,s.verdict_message,s.runtime_ms,s.memory_kb,s.created_at,s.finished_at,"
                +"EXISTS(SELECT 1 FROM agent_findings f WHERE f.submission_id=s.id AND f.kind='DIAGNOSIS') AS diagnosed"
                +" FROM submissions s JOIN problems p ON p.id=s.problem_id"+where+" ORDER BY s.created_at DESC, s.id DESC LIMIT ? OFFSET ?",
                (rs,n)->new ListItem(rs.getLong("id"),rs.getString("slug"),rs.getString("title"),rs.getString("language"),rs.getString("status"),
                        rs.getString("verdict_message"),rs.getObject("runtime_ms",Integer.class),rs.getObject("memory_kb",Integer.class),
                        time(rs.getObject("created_at")),time(rs.getObject("finished_at")),rs.getBoolean("diagnosed")),pageArgs.toArray());
        return new Page(items,safePage,safeSize,total==null?0:total);
    }
    private static Instant time(Object v){return v==null?null:(v instanceof Timestamp t?t.toInstant():((java.time.LocalDateTime)v).atZone(java.time.ZoneId.systemDefault()).toInstant());}
    private boolean supportedLanguage(String language){return "JAVA_21".equals(language)||"Java 21".equals(language)||"CPP_17".equals(language)||"C++17".equals(language)||"C_17".equals(language)||"C17".equals(language);}
    private String canonicalLanguage(String language){if("CPP_17".equals(language)||"C++17".equals(language))return "CPP_17";if("C_17".equals(language)||"C17".equals(language))return "C_17";return "JAVA_21";}
    private long nextId(){return Math.abs(java.util.concurrent.ThreadLocalRandom.current().nextLong(1,Long.MAX_VALUE));}
}
