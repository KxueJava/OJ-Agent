package com.codeagentoj.server.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * G4：Agent 每人每日用量。
 *
 * - 计数用 {@code INSERT ... ON DUPLICATE KEY UPDATE}，单条语句原子自增，多实例部署下也准确；
 * - 上限由配置 {@code app.agent.daily-limit} 控制（默认 20），便于练习/正式场景调整；
 * - 按自然日统计（{@code usage_day = CURDATE()}），跨天自动从 0 开始，无需清理任务。
 */
@Service
public class AgentUsageService {

    private final JdbcTemplate jdbc;
    private final int dailyLimit;

    public AgentUsageService(JdbcTemplate jdbc, @Value("${app.agent.daily-limit:20}") int dailyLimit) {
        this.jdbc = jdbc;
        this.dailyLimit = Math.max(1, dailyLimit);
    }

    public int dailyLimit() { return dailyLimit; }

    /** 今天已用次数（不产生计数）。 */
    public int usedToday(long userId) {
        Integer used = jdbc.queryForObject(
                "SELECT COALESCE((SELECT calls FROM agent_usage_daily WHERE user_id=? AND usage_day=CURDATE()), 0)",
                Integer.class, userId);
        return used == null ? 0 : used;
    }

    /**
     * 占用一次配额。返回占用后的今日次数；若已达上限则返回一个大于上限的值（调用方据此拒绝）。
     * 先自增再判断：这样并发下不会出现"都通过检查、然后都执行"的超发。
     */
    public int consume(long userId) {
        jdbc.update("INSERT INTO agent_usage_daily (user_id, usage_day, calls) VALUES (?, CURDATE(), 1) "
                + "ON DUPLICATE KEY UPDATE calls = calls + 1", userId);
        return usedToday(userId);
    }

    /** 前端/管理端展示用。 */
    public Map<String, Object> usage(long userId) {
        int used = usedToday(userId);
        return Map.of(
                "used", used,
                "limit", dailyLimit,
                "remaining", Math.max(0, dailyLimit - used),
                "message", used >= dailyLimit
                        ? "今日 Agent 调用已达上限（" + dailyLimit + " 次），明天 0 点重置"
                        : "今日剩余 " + Math.max(0, dailyLimit - used) + " 次");
    }
}
