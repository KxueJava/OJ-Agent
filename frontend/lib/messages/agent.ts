"use client";

import { useLanguage } from "../i18n";

const messages = {
  zh: {
    "agent.tutor": "学习助手",
    "agent.closeLabel": "收回 Agent 侧栏",
    "agent.closeTitle": "收回侧栏",
    "agent.quickExplain": "解释题意",
    "agent.quickHint1": "提示 1",
    "agent.quickHint2": "提示 2",
    "agent.quickHint3": "提示 3",
    "agent.quickAnalyze": "分析错误",
    "agent.safetyPassed": "已通过 Safety 审核",
    "agent.safetyBlocked": "请求已拦截",
    "agent.emptyHint": "可以解释题意、给分级提示，或分析你的公开判题结果。",
    "agent.placeholder": "问 Agent 一个具体问题",
    "agent.send": "发送",
    "agent.loginRequired": "请先登录后使用 Agent",
    "agent.requestFailed": "Agent 请求失败",
    "agent.unavailable": "Agent 暂不可用",
    "agent.loading": "正在载入 Agent...",
    "agent.signInPrompt": "请先登录后使用 Agent。",
    "agent.goLogin": "前往登录",
    "agent.backToWorkbench": "返回工作台",
    "agent.problemContext": "题目上下文",
  },
  en: {
    "agent.tutor": "Learning assistant",
    "agent.closeLabel": "Collapse the Agent panel",
    "agent.closeTitle": "Collapse panel",
    "agent.quickExplain": "Explain the problem",
    "agent.quickHint1": "Hint 1",
    "agent.quickHint2": "Hint 2",
    "agent.quickHint3": "Hint 3",
    "agent.quickAnalyze": "Analyze the error",
    "agent.safetyPassed": "Passed Safety review",
    "agent.safetyBlocked": "Request blocked",
    "agent.emptyHint": "It can explain the problem, give graded hints, or analyze your public judge results.",
    "agent.placeholder": "Ask the Agent a specific question",
    "agent.send": "Send",
    "agent.loginRequired": "Sign in to use the Agent",
    "agent.requestFailed": "Agent request failed",
    "agent.unavailable": "The Agent is temporarily unavailable",
    "agent.loading": "Loading the Agent...",
    "agent.signInPrompt": "Please sign in to use the Agent.",
    "agent.goLogin": "Go to sign in",
    "agent.backToWorkbench": "Back to workbench",
    "agent.problemContext": "Problem context",
  },
} as const;

export type AgentKey = keyof typeof messages.zh;

export function useAgentMessages() {
  const { language } = useLanguage();
  return (key: AgentKey) => messages[language][key] ?? messages.zh[key];
}
