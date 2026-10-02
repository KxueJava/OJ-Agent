"use client";

import { useLanguage } from "../i18n";

const messages = {
  zh: {
    "auth.login": "登录",
    "auth.loginSubtitle": "继续你的 Java 练习。",
    "auth.identifier": "用户名或邮箱",
    "auth.password": "密码",
    "auth.showPassword": "显示密码",
    "auth.hidePassword": "隐藏密码",
    "auth.signingIn": "登录中...",
    "auth.noAccount": "还没有账号？",
    "auth.register": "注册",
    "auth.loginFailed": "登录失败",
    "auth.registerFailed": "注册失败",
    "auth.createAccount": "创建账号",
    "auth.registerSubtitle": "注册后即可开始做题。",
    "auth.username": "用户名",
    "auth.email": "邮箱",
    "auth.displayName": "显示名称",
    "auth.creatingAccount": "创建中...",
    "auth.haveAccount": "已有账号？",
    "auth.backToLogin": "返回登录",
  },
  en: {
    "auth.login": "Sign in",
    "auth.loginSubtitle": "Continue your Java practice.",
    "auth.identifier": "Username or email",
    "auth.password": "Password",
    "auth.showPassword": "Show password",
    "auth.hidePassword": "Hide password",
    "auth.signingIn": "Signing in...",
    "auth.noAccount": "Don't have an account?",
    "auth.register": "Sign up",
    "auth.loginFailed": "Sign-in failed",
    "auth.registerFailed": "Sign-up failed",
    "auth.createAccount": "Create account",
    "auth.registerSubtitle": "Create an account to start solving problems.",
    "auth.username": "Username",
    "auth.email": "Email",
    "auth.displayName": "Display name",
    "auth.creatingAccount": "Creating...",
    "auth.haveAccount": "Already have an account?",
    "auth.backToLogin": "Back to sign in",
  },
} as const;

export type AuthKey = keyof typeof messages.zh;

export function useAuthMessages() {
  const { language } = useLanguage();
  return (key: AuthKey) => messages[language][key] ?? messages.zh[key];
}
