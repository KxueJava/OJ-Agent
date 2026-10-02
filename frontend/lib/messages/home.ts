"use client";

import { useLanguage } from "../i18n";

/**
 * 首页（概览）正文文案。
 * 只覆盖界面文案：题目标题/主题/理由、Agent 回复、判题信息与 trace 都来自服务端，不做翻译。
 * 动态数字用 prefix / middle / suffix 三段 key + String(n) 拼接，不引入模板引擎。
 *
 * 日期：中文用 home.date.* 组装 "2026年10月1日 星期四"；
 * 英文沿用 app/page.tsx 原有的 weekdays / months 数组（"THURSDAY / 01 OCT 2026"），
 * 因此英文字典里 home.date.* 这些 key 留空。
 */
const messages = {
  zh: {
    "home.status.online": "Java 21 · 在线",

    "home.header.problems": "题库",
    "home.header.resume": "继续练习",
    "home.header.start": "开始练习",

    "home.progress": "今日练习进度",
    "home.progress.solvedPrefix": "已解决 ",
    "home.progress.solvedMiddle": " / ",
    "home.progress.solvedSuffix": " 道推荐题",
    "home.metric.solved": "已解决",
    "home.metric.submissions": "累计提交",
    "home.metric.acceptance": "通过率",

    "home.continue.lastStopped": "上次停在这里",
    "home.continue.updatedSuffix": " 更新",
    "home.continue.submissionCount": "提交次数",
    "home.continue.resume": "继续做题",
    "home.continue.emptyTitle": "还没有练习记录",
    "home.continue.emptyHeading": "从题库选一道题",
    "home.continue.emptyHint": "第一次提交后，这里会显示你的最近练习。",
    "home.continue.openProblems": "打开题库",

    "home.queue.kicker": "练习队列",
    "home.queue.title": "今天做什么",
    "home.queue.filter.all": "全部",
    "home.queue.filter.easy": "简单",
    "home.queue.filter.medium": "中等",
    "home.queue.notSubmitted": "未提交",
    "home.queue.empty": "这个筛选下还没有练习记录。",
    "home.queue.showingPrefix": "显示 ",
    "home.queue.showingMiddle": " / ",
    "home.queue.showingSuffix": " 道题",
    "home.queue.viewAll": "查看完整题库 →",

    "home.stats.kicker": "学习统计",
    "home.stats.title": "来自真实提交",
    "home.stats.solved": "已解决",
    "home.stats.totalSolved": "题库已解决",
    "home.stats.recentPrefix": "近 7 天 ",
    "home.stats.recentSuffix": " 次提交",
    "home.stats.recentLabel": "近7天提交",

    "home.coach.title": "今日训练",
    "home.coach.online": "在线",
    "home.coach.hintPrompt": "需要一点提示？",
    "home.coach.hintNote": "不会直接展示答案",
    "home.coach.hintPrefix": "提示 ",
    "home.coach.allDone": "今日推荐已完成，继续浏览题库巩固。",
    "home.coach.startPractice": "开始练习",
    "home.coach.details": "详情 →",

    "home.ask.placeholder": "问 Agent 一个具体问题",

    "home.digest.recommendPrefix": "建议练习《",
    "home.digest.recommendMiddle": "》（",
    "home.digest.recommendSuffix": "）：",

    "home.focus.kicker": "练习状态",
    "home.focus.activePrefix": "最近 7 天活跃 ",
    "home.focus.activeSuffix": " 天",
    "home.focus.noActivity": "开始第一次提交",
    "home.focus.dataNote": "首页数据来自判题服务和你的提交记录。",
    "home.focus.emptyNote": "完成一道题后，这里会出现真实的学习统计。",
    "home.focus.browse": "浏览题库",
    "home.footer.workspace": "练习工作台 / build 0.2",
    "home.footer.note": "数据以服务端判题结果为准",

    "home.date.yearSuffix": "年",
    "home.date.monthSuffix": "月",
    "home.date.daySuffix": "日 ",
    "home.date.weekday.0": "星期日",
    "home.date.weekday.1": "星期一",
    "home.date.weekday.2": "星期二",
    "home.date.weekday.3": "星期三",
    "home.date.weekday.4": "星期四",
    "home.date.weekday.5": "星期五",
    "home.date.weekday.6": "星期六",
  },
  en: {
    "home.status.online": "Java 21 · Online",

    "home.header.problems": "Problems",
    "home.header.resume": "Resume",
    "home.header.start": "Start",

    "home.progress": "Today's progress",
    "home.progress.solvedPrefix": "Solved ",
    "home.progress.solvedMiddle": " / ",
    "home.progress.solvedSuffix": " recommended problems",
    "home.metric.solved": "Solved",
    "home.metric.submissions": "Total submissions",
    "home.metric.acceptance": "Acceptance",

    "home.continue.lastStopped": "Resume here",
    "home.continue.updatedSuffix": " updated",
    "home.continue.submissionCount": "Submissions",
    "home.continue.resume": "Continue",
    "home.continue.emptyTitle": "No practice history yet",
    "home.continue.emptyHeading": "Pick a problem from the problem set",
    "home.continue.emptyHint": "Your most recent practice shows up here after your first submission.",
    "home.continue.openProblems": "Open problem set",

    "home.queue.kicker": "Practice queue",
    "home.queue.title": "What to practice today",
    "home.queue.filter.all": "All",
    "home.queue.filter.easy": "Easy",
    "home.queue.filter.medium": "Medium",
    "home.queue.notSubmitted": "Not submitted",
    "home.queue.empty": "No practice history under this filter.",
    "home.queue.showingPrefix": "Showing ",
    "home.queue.showingMiddle": " / ",
    "home.queue.showingSuffix": " problems",
    "home.queue.viewAll": "View full problem set →",

    "home.stats.kicker": "Learning stats",
    "home.stats.title": "Based on real submissions",
    "home.stats.solved": "Solved",
    "home.stats.totalSolved": "Problem set solved",
    "home.stats.recentPrefix": "Last 7 days: ",
    "home.stats.recentSuffix": " submissions",
    "home.stats.recentLabel": "Last 7 days",

    "home.coach.title": "Today's training",
    "home.coach.online": "Online",
    "home.coach.hintPrompt": "Need a hint?",
    "home.coach.hintNote": "Answers are never revealed",
    "home.coach.hintPrefix": "Hint ",
    "home.coach.allDone": "Today's recommendations are complete — browse the problem set to keep practicing.",
    "home.coach.startPractice": "Start practice",
    "home.coach.details": "Details →",

    "home.ask.placeholder": "Ask the agent a specific question",

    "home.digest.recommendPrefix": "Recommended practice: ",
    "home.digest.recommendMiddle": " (",
    "home.digest.recommendSuffix": "): ",

    "home.focus.kicker": "Practice status",
    "home.focus.activePrefix": "Active ",
    "home.focus.activeSuffix": " days in the last 7",
    "home.focus.noActivity": "Make your first submission",
    "home.focus.dataNote": "Home data comes from the judging service and your own submission history.",
    "home.focus.emptyNote": "Solve a problem and real learning stats will appear here.",
    "home.focus.browse": "Browse problem set",
    "home.footer.workspace": "Practice workspace / build 0.2",
    "home.footer.note": "Data reflects server-side judging results",

    "home.date.yearSuffix": "",
    "home.date.monthSuffix": "",
    "home.date.daySuffix": "",
    "home.date.weekday.0": "",
    "home.date.weekday.1": "",
    "home.date.weekday.2": "",
    "home.date.weekday.3": "",
    "home.date.weekday.4": "",
    "home.date.weekday.5": "",
    "home.date.weekday.6": "",
  },
} as const;

export type HomeKey = keyof typeof messages.zh;

export function useHomeMessages() {
  const { language } = useLanguage();
  return (key: HomeKey) => messages[language][key] ?? messages.zh[key];
}
