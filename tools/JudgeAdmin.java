import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 阶段 4 验收辅助（只读为主，dead/admin 会写测试数据）：
 *   dead            插入一条合成的死信 outbox 记录，打印 id
 *   admin <用户名>   把该用户提升为 ADMIN（用于验证管理端接口）
 *   cleanup         删除合成记录（id=999000001）
 *   stats           打印 outbox 统计
 */
public class JudgeAdmin {
    static final String URL = "jdbc:mysql://localhost:3306/codeagent_oj_local?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";

    public static void main(String[] args) throws Exception {
        String action = args.length > 0 ? args[0] : "stats";
        try (Connection connection = DriverManager.getConnection(URL, "root", "root");
             Statement statement = connection.createStatement()) {
            switch (action) {
                case "dead" -> {
                    statement.executeUpdate("DELETE FROM outbox_events WHERE id=999000001");
                    statement.executeUpdate("INSERT INTO outbox_events (id,aggregate_id,event_type,payload_json,status,attempts,last_error,available_at,dead_lettered_at) "
                            + "VALUES (999000001,0,'SUBMISSION_CREATED','{\"submissionId\":0,\"language\":\"JAVA_21\",\"problemVersionId\":2101}','DEAD',8,'synthetic dead letter for stage-4 acceptance',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                    System.out.println("synthetic_dead_id=999000001");
                }
                case "admin" -> {
                    String username = args[1];
                    int changed = statement.executeUpdate("UPDATE users SET role_id=(SELECT id FROM roles WHERE name='ADMIN') WHERE username='" + username + "'");
                    System.out.println("promoted=" + changed + " user=" + username);
                }
                case "cleanup" -> System.out.println("removed=" + statement.executeUpdate("DELETE FROM outbox_events WHERE id=999000001"));
                case "audit" -> {
                    try (ResultSet rs = statement.executeQuery("SELECT admin_user_id,action,target_id,created_at FROM admin_audit_log ORDER BY created_at DESC LIMIT 8")) {
                        while (rs.next()) System.out.println("  admin=" + rs.getLong(1) + " " + rs.getString(2) + " target=" + rs.getLong(3) + " at " + rs.getString(4));
                    }
                }
                case "contest" -> {
                    try (ResultSet rs = statement.executeQuery("SELECT id,slug,title,status,start_at,end_at,freeze_minutes FROM contests ORDER BY created_at DESC LIMIT 5")) {
                        while (rs.next()) System.out.println("  #" + rs.getLong(1) + " " + rs.getString(2) + " " + rs.getString(3) + " [" + rs.getString(4) + "] " + rs.getString(5) + " → " + rs.getString(6) + " freeze=" + rs.getInt(7));
                    }
                    try (ResultSet rs = statement.executeQuery("SELECT contest_id,label,score,display_order FROM contest_problems ORDER BY contest_id,display_order LIMIT 10")) {
                        while (rs.next()) System.out.println("    contest=" + rs.getLong(1) + " " + rs.getString(2) + " score=" + rs.getInt(3) + " order=" + rs.getInt(4));
                    }
                }
                default -> {
                    try (ResultSet rs = statement.executeQuery("SELECT status, COUNT(*) c FROM outbox_events GROUP BY status")) {
                        while (rs.next()) System.out.println("  " + rs.getString(1) + "=" + rs.getInt(2));
                    }
                }
            }
        }
    }
}
