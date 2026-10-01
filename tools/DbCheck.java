import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/** 只读校验脚本：确认 P1/P2 的审计与诊断数据真的落库了。 */
public class DbCheck {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://localhost:3306/codeagent_oj_local?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
        try (Connection connection = DriverManager.getConnection(url, "root", "root");
             Statement statement = connection.createStatement()) {

            System.out.println("== agent_tool_calls by tool ==");
            print(statement, "SELECT tool, COUNT(*) c, SUM(ok) okc, MAX(result_chars) max_chars, MAX(latency_ms) max_ms FROM agent_tool_calls GROUP BY tool ORDER BY c DESC");

            System.out.println("== agent_tool_calls last 8 ==");
            print(statement, "SELECT tool, ok, result_chars, latency_ms, created_at FROM agent_tool_calls ORDER BY created_at DESC, id DESC LIMIT 8");

            System.out.println("== agent_findings last 5 ==");
            print(statement, "SELECT submission_id, kind, verdict, safety_status, latency_ms, CHAR_LENGTH(content) len, created_at FROM agent_findings ORDER BY created_at DESC LIMIT 5");

            System.out.println("== agent_findings totals ==");
            print(statement, "SELECT kind, safety_status, COUNT(*) c FROM agent_findings GROUP BY kind, safety_status");

            System.out.println("== agent_messages memory rows per session (top 5) ==");
            print(statement, "SELECT session_id, COUNT(*) rows_total, SUM(in_memory) rows_in_memory, MIN(seq) min_seq, MAX(seq) max_seq FROM agent_messages GROUP BY session_id ORDER BY rows_total DESC LIMIT 5");

            System.out.println("== agent_messages role order sanity (latest session) ==");
            print(statement, "SELECT seq, role, in_memory, safety_status FROM agent_messages WHERE session_id=(SELECT session_id FROM agent_messages ORDER BY seq DESC LIMIT 1) ORDER BY seq");
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
