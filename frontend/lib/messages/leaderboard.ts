"use client";

import { useLanguage } from "../i18n";

const messages = {
  zh: {
    "leaderboard.title": "排行榜",
    "leaderboard.subtitle": "按 Accepted 题数排名，记录每一次真正完成的题目。",
    "leaderboard.period.week": "本周",
    "leaderboard.period.month": "本月",
    "leaderboard.period.all": "总榜",
    "leaderboard.periodAria": "榜单周期",
    "leaderboard.tableAria": "Accepted 排行榜",
    "leaderboard.navAria": "主导航",
    "leaderboard.profileAria": "个人资料",
    "leaderboard.loginAria": "登录",
    "leaderboard.loadError": "排行榜暂时无法读取",
    "leaderboard.unavailable": "排行榜暂时不可用",
    "leaderboard.loading": "正在整理最新排名…",
    "leaderboard.emptyTitle": "榜单还没有数据",
    "leaderboard.emptyDesc": "完成第一道题后，你会出现在这里。",
    "leaderboard.avatarAlt": " 的头像",
    "leaderboard.activeUsersPrefix": "",
    "leaderboard.activeUsersSuffix": " 位用户",
    "leaderboard.noteSuffix": " · 只统计独立题目的 Accepted 结果",
  },
  en: {
    "leaderboard.title": "Leaderboard",
    "leaderboard.subtitle": "Ranked by Accepted count — every problem you genuinely finish is recorded.",
    "leaderboard.period.week": "This week",
    "leaderboard.period.month": "This month",
    "leaderboard.period.all": "All time",
    "leaderboard.periodAria": "Leaderboard period",
    "leaderboard.tableAria": "Accepted leaderboard",
    "leaderboard.navAria": "Main navigation",
    "leaderboard.profileAria": "Profile",
    "leaderboard.loginAria": "Log in",
    "leaderboard.loadError": "The leaderboard is temporarily unavailable.",
    "leaderboard.unavailable": "Leaderboard unavailable",
    "leaderboard.loading": "Compiling the latest rankings…",
    "leaderboard.emptyTitle": "No rankings yet",
    "leaderboard.emptyDesc": "Solve your first problem and you will show up here.",
    "leaderboard.avatarAlt": "'s avatar",
    "leaderboard.activeUsersPrefix": "",
    "leaderboard.activeUsersSuffix": " users",
    "leaderboard.noteSuffix": " · Only Accepted results on distinct problems are counted",
  },
} as const;

export type LeaderboardKey = keyof typeof messages.zh;

export function useLeaderboardMessages() {
  const { language } = useLanguage();
  return (key: LeaderboardKey) => messages[language][key] ?? messages.zh[key];
}
