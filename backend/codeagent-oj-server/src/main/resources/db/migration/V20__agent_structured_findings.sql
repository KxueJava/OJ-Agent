-- P3：结构化 finding + 输出侧审核结果 + 真实执行轨迹
ALTER TABLE agent_findings
    ADD COLUMN findings_json MEDIUMTEXT NULL,
    ADD COLUMN output_review VARCHAR(16) NULL;

ALTER TABLE agent_audits
    ADD COLUMN trace_json MEDIUMTEXT NULL,
    ADD COLUMN output_review VARCHAR(16) NULL;
