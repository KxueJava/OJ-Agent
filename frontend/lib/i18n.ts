"use client";

import { useCallback, useEffect, useState } from "react";

export type Language = "zh" | "en";

const storageKey = "codeagent-oj:language";
const eventName = "codeagent-oj:language-changed";

/**
 * 界面语言字典。只覆盖"界面文案"（导航、页头、按钮、标题），
 * 题目内容、Agent 回复与判题信息保持原样 —— 它们由服务端数据决定，不做翻译。
 */
export const messages = {
  zh: {
    "nav.home": "主页",
    "nav.problems": "题库",
    "nav.favorites": "收藏",
    "nav.discussions": "讨论区",
    "nav.leaderboard": "排行榜",
    "nav.contests": "竞赛",
    "nav.submissions": "提交记录",
    "nav.settings": "设置",
    "nav.overview": "概览",
    "nav.mistakes": "错题本",
    "nav.admin": "管理端",
    "nav.workspace": "我的工作台",
    "home.overview": "概览",
    "submissions.title": "提交记录",
    "submissions.eyebrow": "SUBMISSIONS",
    "settings.title": "设置",
    "settings.eyebrow": "SETTINGS",
    "problems.title": "题库",
    "leaderboard.title": "排行榜",
    "language.label": "语言",
    "language.zh": "中文",
    "profile.open": "打开个人资料",
    "profile.title": "个人资料",
    "home.routeRule": "Learning（规则推荐）",
    "home.hintCopy": "提问会带上你正在练的这道题，答案只基于题目公开信息。",
    "home.agentDefault": "先完成一道简单题，我会根据你的提交结果调整训练建议。",
  },
  en: {
    "nav.home": "Home",
    "nav.problems": "Problems",
    "nav.favorites": "Favorites",
    "nav.discussions": "Discussion",
    "nav.leaderboard": "Leaderboard",
    "nav.contests": "Contests",
    "nav.submissions": "Submissions",
    "nav.settings": "Settings",
    "nav.overview": "Overview",
    "nav.mistakes": "Mistakes",
    "nav.admin": "Admin",
    "nav.workspace": "Workspace",
    "home.overview": "Overview",
    "submissions.title": "Submissions",
    "submissions.eyebrow": "SUBMISSIONS",
    "settings.title": "Settings",
    "settings.eyebrow": "SETTINGS",
    "problems.title": "Problems",
    "leaderboard.title": "Leaderboard",
    "language.label": "Language",
    "language.zh": "中文",
    "profile.open": "Open profile",
    "profile.title": "Profile",
    "home.routeRule": "Learning (rule-based)",
    "home.hintCopy": "Questions include the problem you are practising; answers rely only on public problem data.",
    "home.agentDefault": "Solve one easy problem first, then I can tune the plan to your real results.",
  },
} as const;

export type MessageKey = keyof typeof messages.zh;

export function readLanguage(): Language {
  if (typeof window === "undefined") return "zh";
  return window.localStorage.getItem(storageKey) === "en" ? "en" : "zh";
}

export function writeLanguage(next: Language) {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(storageKey, next);
  document.documentElement.lang = next === "en" ? "en" : "zh-CN";
  window.dispatchEvent(new Event(eventName));
}

/** 语言状态：切换后同页面立即生效，跨标签页靠 storage 事件同步。 */
export function useLanguage() {
  const [language, setLanguage] = useState<Language>("zh");

  useEffect(() => {
    const sync = () => setLanguage(readLanguage());
    sync();
    window.addEventListener(eventName, sync);
    window.addEventListener("storage", sync);
    return () => {
      window.removeEventListener(eventName, sync);
      window.removeEventListener("storage", sync);
    };
  }, []);

  const change = useCallback((next: Language) => {
    writeLanguage(next);
    setLanguage(next);
  }, []);

  const toggle = useCallback(() => {
    setLanguage((current) => {
      const next: Language = current === "zh" ? "en" : "zh";
      writeLanguage(next);
      return next;
    });
  }, []);

  const t = useCallback((key: MessageKey) => messages[language][key] ?? messages.zh[key], [language]);

  return { language, setLanguage: change, toggle, t };
}
