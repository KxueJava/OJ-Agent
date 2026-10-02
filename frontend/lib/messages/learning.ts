"use client";

import { useLanguage } from "../i18n";

const messages = {
  zh: {
    "learning.heroEyebrow": "学习中心",
    "learning.heroTitle": "把错误变成下一道题。",
    "learning.heroDesc": "每次失败提交都会留下可复盘的线索，推荐根据你的真实记录更新。",
    "learning.backOverview": "返回概览",
    "learning.today.title": "今日训练",
    "learning.metric.events": "学习事件",
    "learning.status.pending": "待复盘",
    "learning.status.reviewed": "已复盘",
    "learning.mistakes.title": "错题本",
    "learning.mistakes.countPrefix": "",
    "learning.mistakes.countSuffix": " 条记录",
    "learning.mistakes.submissionLabel": "提交 #",
    "learning.mistakes.markReviewed": "标记已复盘",
    "learning.mistakes.empty": "还没有错题记录。完成一次失败提交后，这里会自动生成归因。",
    "learning.verdict.WA": "答案错误",
    "learning.verdict.CE": "编译失败",
    "learning.verdict.RE": "运行异常",
    "learning.verdict.TLE": "超时",
    "learning.verdict.MLE": "内存超限",
    "learning.loadError": "学习数据读取失败",
    "learning.loginRequired": "请先登录后查看学习记录",
    "learning.reviewError": "复盘状态更新失败",
    "learning.loading": "正在整理学习记录...",
    "learning.goToLogin": "前往登录",
    "learning.agent.title": "下一次提交后继续复盘",
    "learning.agent.desc": "Reviewer 关注复杂度、边界和代码质量，Learning Agent 根据错误标签安排下一题。",
  },
  en: {
    "learning.heroEyebrow": "Learning center",
    "learning.heroTitle": "Turn every mistake into your next problem.",
    "learning.heroDesc": "Every failed submission leaves a clue worth revisiting, and your recommendations update from your real record.",
    "learning.backOverview": "Back to overview",
    "learning.today.title": "Today's training",
    "learning.metric.events": "Learning events",
    "learning.status.pending": "Pending review",
    "learning.status.reviewed": "Reviewed",
    "learning.mistakes.title": "Mistake book",
    "learning.mistakes.countPrefix": "",
    "learning.mistakes.countSuffix": " records",
    "learning.mistakes.submissionLabel": "Submission #",
    "learning.mistakes.markReviewed": "Mark as reviewed",
    "learning.mistakes.empty": "No mistakes recorded yet. Once a submission fails, a diagnosis appears here automatically.",
    "learning.verdict.WA": "Wrong answer",
    "learning.verdict.CE": "Compilation error",
    "learning.verdict.RE": "Runtime error",
    "learning.verdict.TLE": "Time limit exceeded",
    "learning.verdict.MLE": "Memory limit exceeded",
    "learning.loadError": "Could not load your learning data.",
    "learning.loginRequired": "Sign in to view your learning records.",
    "learning.reviewError": "Could not update the review status.",
    "learning.loading": "Compiling your learning records...",
    "learning.goToLogin": "Go to sign in",
    "learning.agent.title": "Keep reviewing after your next submission",
    "learning.agent.desc": "Reviewer watches complexity, edge cases, and code quality, while the Learning Agent lines up your next problem from your error tags.",
  },
} as const;

export type LearningKey = keyof typeof messages.zh;

export function useLearningMessages() {
  const { language } = useLanguage();
  return (key: LearningKey) => messages[language][key] ?? messages.zh[key];
}
