"use client";

import { useLanguage } from "../i18n";

const messages = {
  zh: {
    "workspace.checkingSession": "正在检查登录状态...",
    "workspace.signOut": "退出登录",
    "workspace.greeting": "你好，",
    "workspace.sessionRestored": "登录态已恢复。题库和做题工作台将在下一阶段接入。",
  },
  en: {
    "workspace.checkingSession": "Checking your session...",
    "workspace.signOut": "Sign out",
    "workspace.greeting": "Hi, ",
    "workspace.sessionRestored": "Your session has been restored. The problem bank and solving workspace arrive in the next phase.",
  },
} as const;

export type WorkspaceKey = keyof typeof messages.zh;

export function useWorkspaceMessages() {
  const { language } = useLanguage();
  return (key: WorkspaceKey) => messages[language][key] ?? messages.zh[key];
}
