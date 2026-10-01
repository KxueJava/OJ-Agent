-- P4：多 Agent 流水线的真实执行轨迹（Supervisor 规划 → 专职 Agent 并行 → Safety → Finalizer）
ALTER TABLE agent_findings
    ADD COLUMN trace_json MEDIUMTEXT NULL;
