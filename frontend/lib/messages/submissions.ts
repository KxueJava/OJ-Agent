"use client";

import { useLanguage } from "../i18n";

/**
 * 提交记录列表页 + 提交详情页的正文文案。
 * 只覆盖界面文案：服务端返回的 verdictMessage、诊断内容、源码、题目标题与 slug 都不做翻译。
 */
const messages = {
  zh: {
    "submissions.status.PENDING": "排队中",
    "submissions.status.RUNNING": "判题中",
    "submissions.status.AC": "通过",
    "submissions.status.WA": "答案错误",
    "submissions.status.CE": "编译错误",
    "submissions.status.RE": "运行错误",
    "submissions.status.TLE": "超出时间",
    "submissions.status.MLE": "超出内存",

    "submissions.filter.all": "全部",
    "submissions.filter.ac": "通过",
    "submissions.filter.wa": "答案错误",
    "submissions.filter.ce": "编译错误",
    "submissions.filter.re": "运行错误",
    "submissions.filter.tle": "超出时间",

    "submissions.desc": "每一次提交的判题结果、用时与自动诊断都在这里，只显示你自己的记录。",

    "submissions.table.problem": "题目",
    "submissions.table.language": "语言",
    "submissions.table.status": "状态",
    "submissions.table.time": "用时",
    "submissions.table.memory": "内存",
    "submissions.table.createdAt": "提交时间",
    "submissions.table.diagnosis": "诊断",
    "submissions.table.diagnosed": "有",

    "submissions.unit.ms": " ms",
    "submissions.unit.kb": " KB",

    "submissions.pager.prefix": "共 ",
    "submissions.pager.middle": " 条 · 第 ",
    "submissions.pager.suffix": " 页",
    "submissions.pager.prev": "上一页",
    "submissions.pager.next": "下一页",

    "submissions.loading": "正在加载…",
    "submissions.loadFailed": "加载提交记录失败",
    "submissions.loginLink": "登录",
    "submissions.loginSuffix": "后查看你的提交记录。",
    "submissions.emptyPrefix": "还没有提交记录，去",
    "submissions.emptyLink": "题库",
    "submissions.emptySuffix": "挑一道题开始练习。",

    "submissionDetail.loadFailed": "加载提交详情失败",
    "submissionDetail.loginSuffix": "后查看提交详情。",
    "submissionDetail.loading": "正在加载…",
    "submissionDetail.back": "← 提交记录",
    "submissionDetail.verdictPending": "判题队列已接收，等待结果",
    "submissionDetail.hint.infra": "这是判题基础设施的问题（沙箱不可用），不是你代码的错误 —— 可以用下面的「重新判题」再跑一次。",
    "submissionDetail.hint.internal": "这是判题器内部异常，不是你代码的错误 —— 请稍后重新判题。",

    "submissionDetail.metric.language": "语言",
    "submissionDetail.metric.compileMs": "编译耗时",
    "submissionDetail.metric.runtimeMs": "运行时间",
    "submissionDetail.metric.memoryKb": "内存占用",
    "submissionDetail.metric.rejudgeCount": "重判次数",
    "submissionDetail.metric.createdAt": "提交时间",

    "submissionDetail.cases.title": "公开用例结果",
    "submissionDetail.cases.empty": "这次提交没有公开用例结果。",
    "submissionDetail.cases.itemPrefix": "测试 ",
    "submissionDetail.cases.note": "隐藏用例的内容不会被展示，只参与判题与自动诊断的计数。",

    "submissionDetail.diagnosis.title": "自动诊断",
    "submissionDetail.diagnosis.pending": "诊断尚未生成，稍候会自动出现。",
    "submissionDetail.diagnosis.sourceFallback": "规则化建议（模型不可用时生成）",
    "submissionDetail.diagnosis.sourcePipeline": "由 Debugger / Reviewer 多 Agent 流水线生成",
    "submissionDetail.finding.codeFallback": "代码",

    "submissionDetail.code.title": "提交的代码",

    "submissionDetail.rejudgeQueued": "已重新提交判题队列",
    "submissionDetail.rejudgeFailed": "重新判题失败",
    "submissionDetail.actions.back": "返回提交记录",
    "submissionDetail.actions.rejudging": "重新判题中…",
    "submissionDetail.actions.rejudge": "重新判题",
    "submissionDetail.actions.workbench": "去工作台改这题",
    "submissionDetail.actions.problem": "查看题面",
  },
  en: {
    "submissions.status.PENDING": "Pending",
    "submissions.status.RUNNING": "Judging",
    "submissions.status.AC": "Accepted",
    "submissions.status.WA": "Wrong Answer",
    "submissions.status.CE": "Compile Error",
    "submissions.status.RE": "Runtime Error",
    "submissions.status.TLE": "Time Limit Exceeded",
    "submissions.status.MLE": "Memory Limit Exceeded",

    "submissions.filter.all": "All",
    "submissions.filter.ac": "Accepted",
    "submissions.filter.wa": "Wrong Answer",
    "submissions.filter.ce": "Compile Error",
    "submissions.filter.re": "Runtime Error",
    "submissions.filter.tle": "Time Limit Exceeded",

    "submissions.desc": "Verdicts, runtime and automatic diagnosis for every submission — only your own records are shown.",

    "submissions.table.problem": "Problem",
    "submissions.table.language": "Language",
    "submissions.table.status": "Status",
    "submissions.table.time": "Time",
    "submissions.table.memory": "Memory",
    "submissions.table.createdAt": "Submitted",
    "submissions.table.diagnosis": "Diagnosis",
    "submissions.table.diagnosed": "Yes",

    "submissions.unit.ms": " ms",
    "submissions.unit.kb": " KB",

    "submissions.pager.prefix": "Total ",
    "submissions.pager.middle": " · Page ",
    "submissions.pager.suffix": "",
    "submissions.pager.prev": "Previous",
    "submissions.pager.next": "Next",

    "submissions.loading": "Loading…",
    "submissions.loadFailed": "Failed to load submissions",
    "submissions.loginLink": "Log in",
    "submissions.loginSuffix": " to view your submissions.",
    "submissions.emptyPrefix": "No submissions yet — head to the ",
    "submissions.emptyLink": "problem set",
    "submissions.emptySuffix": " and pick a problem to start practicing.",

    "submissionDetail.loadFailed": "Failed to load the submission",
    "submissionDetail.loginSuffix": " to view this submission.",
    "submissionDetail.loading": "Loading…",
    "submissionDetail.back": "← Submissions",
    "submissionDetail.verdictPending": "Accepted by the judging queue, waiting for the result",
    "submissionDetail.hint.infra": "This is a judging infrastructure problem (the sandbox was unavailable), not a mistake in your code — run it again with Rejudge below.",
    "submissionDetail.hint.internal": "This is a judge internal error, not a mistake in your code — please rejudge later.",

    "submissionDetail.metric.language": "Language",
    "submissionDetail.metric.compileMs": "Compile time",
    "submissionDetail.metric.runtimeMs": "Runtime",
    "submissionDetail.metric.memoryKb": "Memory",
    "submissionDetail.metric.rejudgeCount": "Rejudges",
    "submissionDetail.metric.createdAt": "Submitted",

    "submissionDetail.cases.title": "Public test case results",
    "submissionDetail.cases.empty": "This submission has no public test case results.",
    "submissionDetail.cases.itemPrefix": "Case ",
    "submissionDetail.cases.note": "Hidden test cases are never shown; they only count toward judging and automatic diagnosis.",

    "submissionDetail.diagnosis.title": "Automatic diagnosis",
    "submissionDetail.diagnosis.pending": "The diagnosis has not been generated yet; it will appear here automatically.",
    "submissionDetail.diagnosis.sourceFallback": "Rule-based suggestions (generated while the model was unavailable)",
    "submissionDetail.diagnosis.sourcePipeline": "Generated by the Debugger / Reviewer multi-agent pipeline",
    "submissionDetail.finding.codeFallback": "Code",

    "submissionDetail.code.title": "Submitted code",

    "submissionDetail.rejudgeQueued": "Resubmitted to the judging queue",
    "submissionDetail.rejudgeFailed": "Rejudge failed",
    "submissionDetail.actions.back": "Back to submissions",
    "submissionDetail.actions.rejudging": "Rejudging…",
    "submissionDetail.actions.rejudge": "Rejudge",
    "submissionDetail.actions.workbench": "Fix it in the workbench",
    "submissionDetail.actions.problem": "View the problem",
  },
} as const;

export type SubmissionsKey = keyof typeof messages.zh;

export function useSubmissionsMessages() {
  const { language } = useLanguage();
  return (key: SubmissionsKey) => messages[language][key] ?? messages.zh[key];
}
