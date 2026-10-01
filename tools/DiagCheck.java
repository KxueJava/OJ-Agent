import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/** 只读诊断：最近提交与诊断记录的对应关系。 */
public class DiagCheck {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://localhost:3306/codeagent_oj_local?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
        try (Connection connection = DriverManager.getConnection(url, "root", "root");
             Statement statement = connection.createStatement()) {
            System.out.println("== latest 12 submissions ==");
            print(statement, "SELECT id, user_id, problem_version_id, status, LEFT(verdict_message,30) msg, created_at FROM submissions ORDER BY created_at DESC LIMIT 12");
            System.out.println("== latest 12 findings ==");
            print(statement, "SELECT submission_id, verdict, safety_status, output_review, latency_ms, CHAR_LENGTH(content) len, created_at FROM agent_findings ORDER BY created_at DESC LIMIT 12");
            System.out.println("== WA submissions without finding (candidates the scanner should pick) ==");
            print(statement, """
                SELECT s.id, s.user_id, s.problem_version_id, s.status, s.created_at
                  FROM submissions s
                 WHERE s.status IN ('WA','CE','RE','TLE','MLE')
                   AND s.created_at > DATE_SUB(NOW(), INTERVAL 30 MINUTE)
                   AND NOT EXISTS (SELECT 1 FROM agent_findings f WHERE f.submission_id=s.id AND f.kind='DIAGNOSIS')
                 ORDER BY s.created_at DESC
                """);
            System.out.println("== findings count by hour ==");
            print(statement, "SELECT DATE_FORMAT(created_at,'%Y-%m-%d %H:00') hour, COUNT(*) c FROM agent_findings GROUP BY hour ORDER BY hour DESC LIMIT 8");
        }
    }

    private static void print(Statement statement, String sql) throws Exception {
        try (ResultSet rs = statement.executeQuery(sql)) {
            int columns = rs.getMetaData().getColumnCount();
            int rows = 0;
            while (rs.next()) {
                rows++;
                StringBuilder line = new StringBuilder();
                for (int index = 1; index <= columns; index++) {
                    if (index > 1) line.append(" | ");
                    line.append(rs.getMetaData().getColumnLabel(index)).append('=').append(rs.getString(index));
                }
                System.out.println("  " + line);
            }
            if (rows == 0) System.out.println("  (empty)");
        }
    }
}
