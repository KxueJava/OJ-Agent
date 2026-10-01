import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 迁移失败后的修复：MySQL 下 Flyway 会把失败的迁移记进 flyway_schema_history(success=0)，
 * 之后每次启动都会因 “Detected failed migration” 拒绝继续，必须先删掉这条失败记录。
 * 这里只删失败记录，不动成功记录。
 */
public class FlywayRepair {
    public static void main(String[] args) throws Exception {
        String version = args.length > 0 ? args[0] : "23";
        String url = "jdbc:mysql://localhost:3306/codeagent_oj_local?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
        try (Connection connection = DriverManager.getConnection(url, "root", "root");
             Statement statement = connection.createStatement()) {
            System.out.println("== before ==");
            print(statement);
            int removed = statement.executeUpdate("DELETE FROM flyway_schema_history WHERE version='" + version + "' AND success=0");
            System.out.println("removed_failed_rows=" + removed);
            System.out.println("== after ==");
            print(statement);
        }
    }

    private static void print(Statement statement) throws Exception {
        try (ResultSet rs = statement.executeQuery(
                "SELECT version, description, success, installed_on FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 4")) {
            while (rs.next()) {
                System.out.println("  v" + rs.getString(1) + " " + rs.getString(2) + " success=" + rs.getBoolean(3) + " at " + rs.getString(4));
            }
        }
    }
}
