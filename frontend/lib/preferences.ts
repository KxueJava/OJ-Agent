"use client";

import { useCallback, useEffect, useState } from "react";

export type Preferences = {
  defaultLanguage: "JAVA_21" | "CPP_17" | "C_17";
  editorFontSize: number;
  autoDiagnosis: boolean;
  desktopPet: boolean;
};

export const defaultPreferences: Preferences = {
  defaultLanguage: "JAVA_21",
  editorFontSize: 13,
  autoDiagnosis: true,
  desktopPet: true,
};

const storageKey = "codeagent-oj:preferences";
const eventName = "codeagent-oj:preferences-changed";
const languages: Preferences["defaultLanguage"][] = ["JAVA_21", "CPP_17", "C_17"];

/** 读取本机偏好；非法或损坏的值一律回落到默认值，避免设置页写坏后整个工作台不可用。 */
export function readPreferences(): Preferences {
  if (typeof window === "undefined") return defaultPreferences;
  try {
    const raw = window.localStorage.getItem(storageKey);
    if (!raw) return defaultPreferences;
    const parsed = JSON.parse(raw) as Partial<Preferences>;
    return {
      defaultLanguage: languages.includes(parsed.defaultLanguage as Preferences["defaultLanguage"])
        ? (parsed.defaultLanguage as Preferences["defaultLanguage"])
        : defaultPreferences.defaultLanguage,
      editorFontSize: typeof parsed.editorFontSize === "number" && parsed.editorFontSize >= 11 && parsed.editorFontSize <= 20
        ? Math.round(parsed.editorFontSize)
        : defaultPreferences.editorFontSize,
      autoDiagnosis: typeof parsed.autoDiagnosis === "boolean" ? parsed.autoDiagnosis : defaultPreferences.autoDiagnosis,
      desktopPet: typeof parsed.desktopPet === "boolean" ? parsed.desktopPet : defaultPreferences.desktopPet,
    };
  } catch {
    return defaultPreferences;
  }
}

export function writePreferences(next: Preferences) {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(storageKey, JSON.stringify(next));
  window.dispatchEvent(new Event(eventName));
}

/** 偏好状态：同页面内改设置立即生效，跨标签页靠 storage 事件同步。 */
export function usePreferences() {
  const [preferences, setPreferences] = useState<Preferences>(defaultPreferences);

  useEffect(() => {
    setPreferences(readPreferences());
    const sync = () => setPreferences(readPreferences());
    window.addEventListener(eventName, sync);
    window.addEventListener("storage", sync);
    return () => {
      window.removeEventListener(eventName, sync);
      window.removeEventListener("storage", sync);
    };
  }, []);

  const update = useCallback((patch: Partial<Preferences>) => {
    const next = { ...readPreferences(), ...patch };
    writePreferences(next);
    setPreferences(next);
  }, []);

  return [preferences, update] as const;
}
