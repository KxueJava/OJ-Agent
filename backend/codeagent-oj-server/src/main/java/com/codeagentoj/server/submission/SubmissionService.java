package com.codeagentoj.server.submission;

import static com.codeagentoj.server.submission.SubmissionDtos.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final JdbcTemplate jdbc; private final ObjectMapper mapper; private final RabbitTemplate rabbit; private final String queue;
    public SubmissionService(JdbcTemplate jdbc, ObjectMapper mapper, RabbitTemplate rabbit, @Value("${app.judge.queue:oj.submissions}") String queue) { this.jdbc=jdbc;this.mapper=mapper;this.rabbit=rabbit;this.queue=queue; }
    @Transactional public Summary create(long userId, CreateRequest req) {
        if (!supportedLanguage(req.language())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"当前仅支持 Java 21、C++17 和 C17");
        String language=canonicalLanguage(req.language());
        Map<String,Object> version = jdbc.queryForList("SELECT pv.id,pv.problem_id,p.status FROM problem_versions pv JOIN problems p ON p.id=pv.problem_id WHERE pv.id=? AND pv.status='PUBLISHED' AND p.status='PUBLISHED'",req.problemVersion()).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"题目版本不可提交"));
        long id=nextId(); jdbc.update("INSERT INTO submissions (id,user_id,problem_id,problem_version_id,language,source_code,status) VALUES (?,?,?,?,?,?,?)",id,userId,version.get("problem_id"),req.problemVersion(),language,req.sourceCode(),"PENDING");
        try { jdbc.update("INSERT INTO outbox_events (id,aggregate_id,event_type,payload_json) VALUES (?,?,?,?)",nextId(),id,"SUBMISSION_CREATED",mapper.writeValueAsString(Map.of("submissionId",id,"language",language,"problemVersionId",req.problemVersion()))); } catch(JsonProcessingException e){ throw new IllegalStateException(e); }
        return summary(id,userId);
    }
    @Scheduled(fixedDelayString="${app.judge.poll-ms:500}") public void publishOutbox(){ List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,aggregate_id,payload_json FROM outbox_events WHERE status='PENDING' AND available_at<=CURRENT_TIMESTAMP ORDER BY created_at LIMIT 20"); for(var row:rows){long id=((Number)row.get("id")).longValue(); try { rabbit.convertAndSend(queue,row.get("payload_json")); jdbc.update("UPDATE outbox_events SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP,attempts=attempts+1 WHERE id=? AND status='PENDING'",id); } catch(Exception e){ jdbc.update("UPDATE outbox_events SET attempts=attempts+1,last_error=?,available_at=DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 3 SECOND) WHERE id=?",e.getMessage(),id); } } }
    public Summary summary(long id,long userId){ List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,status,verdict_message,runtime_ms,memory_kb,created_at,finished_at FROM submissions WHERE id=? AND user_id=?",id,userId); if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"提交不存在"); var r=rows.getFirst(); return new Summary(((Number)r.get("id")).longValue(),(String)r.get("status"),(String)r.get("verdict_message"),(Integer)r.get("runtime_ms"),(Integer)r.get("memory_kb"),time(r.get("created_at")),time(r.get("finished_at"))); }
    public Detail detail(long id,long userId){ Summary s=summary(id,userId); List<CaseSummary> cases=jdbc.query("SELECT sc.verdict,sc.runtime_ms,sc.memory_kb,sc.output_summary FROM submission_cases sc JOIN test_cases tc ON tc.id=sc.test_case_id WHERE sc.submission_id=? AND tc.visibility='PUBLIC' ORDER BY sc.id",(rs,n)->new CaseSummary(n+1,rs.getString(1),rs.getObject(2,Integer.class),rs.getObject(3,Integer.class),rs.getString(4)),id); return new Detail(s,cases); }
    public List<Event> events(long id,long userId){ Summary s=summary(id,userId); List<Event> events=new ArrayList<>(); events.add(new Event("status",s.status(),s.verdictMessage(),s.finishedAt()==null?s.createdAt():s.finishedAt())); return events; }
    private static Instant time(Object v){return v==null?null:(v instanceof Timestamp t?t.toInstant():((java.time.LocalDateTime)v).atZone(java.time.ZoneId.systemDefault()).toInstant());}
    private boolean supportedLanguage(String language){return "JAVA_21".equals(language)||"Java 21".equals(language)||"CPP_17".equals(language)||"C++17".equals(language)||"C_17".equals(language)||"C17".equals(language);}
    private String canonicalLanguage(String language){if("CPP_17".equals(language)||"C++17".equals(language))return "CPP_17";if("C_17".equals(language)||"C17".equals(language))return "C_17";return "JAVA_21";}
    private long nextId(){return Math.abs(java.util.concurrent.ThreadLocalRandom.current().nextLong(1,Long.MAX_VALUE));}
}
