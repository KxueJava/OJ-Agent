import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/** 阶段 2 只读校验：Flyway 版本 + 岛屿数量的示例与用例是否已经一致。 */
public class Stage2Check {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://localhost:3306/codeagent_oj_local?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
        try (Connection connection = DriverManager.getConnection(url, "root", "root");
             Statement statement = connection.createStatement()) {
            System.out.println("== flyway (latest 3) ==");
            try (ResultSet rs = statement.executeQuery(
                    "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3")) {
                while (rs.next()) System.out.println("  v" + rs.getString(1) + " " + rs.getString(2) + " success=" + rs.getBoolean(3));
            }
            System.out.println("== problem_version 2118 examples ==");
            try (ResultSet rs = statement.executeQuery(
                    "SELECT display_order, input_text, output_text FROM examples WHERE problem_version_id=2118 ORDER BY display_order")) {
                while (rs.next()) System.out.println("  #" + rs.getInt(1) + " in=" + rs.getString(2) + " out=" + rs.getString(3));
            }
            System.out.println("== problem_version 2118 test_cases ==");
            try (ResultSet rs = statement.executeQuery(
                    "SELECT id, visibility, input_text, expected_output FROM test_cases WHERE problem_version_id=2118 ORDER BY id")) {
                while (rs.next()) System.out.println("  #" + rs.getLong(1) + " " + rs.getString(2) + " in=" + rs.getString(3) + " out=" + rs.getString(4));
            }
        }
    }
}
