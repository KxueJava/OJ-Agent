"use client";

import { useLanguage } from "../i18n";

/**
 * 题库列表页 + 题目详情页的界面文案。
 * 只覆盖界面文案：题面 Markdown、示例的输入输出、服务端返回的标签名、难度枚举值
 * （EASY / MEDIUM / HARD）、判题结果与 Agent 回复都不做翻译。
 */
const messages = {
  zh: {
    "problems.title": "题库",
    "problems.subtitle": "选择一个明确的问题，开始今天的训练。",
    "problems.search": "搜索题目、标签",

    "problems.difficulty.all": "全部难度",
    "problems.difficulty.easy": "简单",
    "problems.difficulty.medium": "中等",
    "problems.difficulty.hard": "困难",

    "problems.tag.all": "全部标签",

    "problems.table.problem": "题目",
    "problems.table.difficulty": "难度",
    "problems.table.tags": "标签",

    "problems.empty": "没有匹配的题目。",

    "problems.pagination.showing": "显示",
    "problems.pagination.of": "/ 共",
    "problems.pagination.unit": "道题",
    "problems.pagination.prev": "上一页",
    "problems.pagination.next": "下一页",

    "problems.error.tags": "题库服务暂不可用",
    "problems.error.load": "加载题库失败",

    "problemDetail.loading": "正在加载题目...",
    "problemDetail.error.loginRequired": "登录后可以收藏题目",
    "problemDetail.error.favoriteFailed": "收藏失败",

    "problemDetail.statement": "题目描述",
    "problemDetail.examples": "示例",
    "problemDetail.example": "示例",
    "problemDetail.constraints": "约束",
    "problemDetail.inputLabel": "输入：",
    "problemDetail.outputLabel": "输出：",

    "problemDetail.favoriteTooltip": "收藏题目",
    "problemDetail.copyTooltip": "复制样例",
    "problemDetail.templateNote": "运行和提交将在阶段 3 判题链路接入。",
  },
  en: {
    "problems.title": "Problems",
    "problems.subtitle": "Pick one well-defined problem and start today's practice.",
    "problems.search": "Search problems or tags",

    "problems.difficulty.all": "All difficulties",
    "problems.difficulty.easy": "Easy",
    "problems.difficulty.medium": "Medium",
    "problems.difficulty.hard": "Hard",

    "problems.tag.all": "All tags",

    "problems.table.problem": "Problem",
    "problems.table.difficulty": "Difficulty",
    "problems.table.tags": "Tags",

    "problems.empty": "No matching problems.",

    "problems.pagination.showing": "Showing",
    "problems.pagination.of": "of",
    "problems.pagination.unit": "problems",
    "problems.pagination.prev": "Previous",
    "problems.pagination.next": "Next",

    "problems.error.tags": "The problem service is temporarily unavailable",
    "problems.error.load": "Failed to load problems",

    "problemDetail.loading": "Loading problem...",
    "problemDetail.error.loginRequired": "Sign in to favorite this problem",
    "problemDetail.error.favoriteFailed": "Failed to update favorite",

    "problemDetail.statement": "Description",
    "problemDetail.examples": "Examples",
    "problemDetail.example": "Example",
    "problemDetail.constraints": "Constraints",
    "problemDetail.inputLabel": "Input: ",
    "problemDetail.outputLabel": "Output: ",

    "problemDetail.favoriteTooltip": "Favorite this problem",
    "problemDetail.copyTooltip": "Copy sample",
    "problemDetail.templateNote": "Run and submit will be connected to the judging pipeline in Phase 3.",
  },
} as const;

export type ProblemsKey = keyof typeof messages.zh;

export function useProblemsMessages() {
  const { language } = useLanguage();
  return (key: ProblemsKey) => messages[language][key] ?? messages.zh[key];
}
