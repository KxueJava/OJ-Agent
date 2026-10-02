"use client";

import { useLanguage } from "../i18n";

/** 设置页正文文案。偏好项本身（localStorage）不参与翻译。 */
const messages = {
  zh: {
    "settings.desc": "这些偏好保存在本机浏览器（localStorage），不上传服务端，换设备不会同步。",
    "settings.savedPrefix": "已保存 ",

    "settings.group.workbench": "工作台",
    "settings.language.title": "默认编程语言",
    "settings.language.desc": "打开工作台时预选的语言；如果该语言下已有本机草稿，会直接恢复草稿。",
    "settings.fontSize.title": "编辑器字号",
    "settings.fontSize.desc": "11–20 px，只影响你本机的显示。",
    "settings.fontSize.decrease": "减小字号",
    "settings.fontSize.increase": "增大字号",

    "settings.group.agent": "Agent",
    "settings.autoDiagnosis.title": "提交后自动诊断",
    "settings.autoDiagnosis.desc": "非 AC 提交完成后自动拉取多 Agent 诊断卡片；关掉后仍可在提交详情页看到已有诊断。",
    "settings.autoDiagnosis.on": "开启",
    "settings.autoDiagnosis.off": "关闭",
    "settings.desktopPet.title": "桌面宠物",
    "settings.desktopPet.desc": "右下角的快捷入口，可以直接把当前题目丢给 Agent 面板。",
    "settings.desktopPet.show": "显示",
    "settings.desktopPet.hide": "隐藏",

    "settings.reset": "恢复默认",
    "settings.goProblems": "去题库",
    "settings.note": "提示：语言、字号、诊断开关都是即时生效的；如果改完没反应，刷新一次页面即可（偏好读取发生在客户端挂载时）。",
  },
  en: {
    "settings.desc": "These preferences live in this browser (localStorage). They are never uploaded and do not sync across devices.",
    "settings.savedPrefix": "Saved ",

    "settings.group.workbench": "Workbench",
    "settings.language.title": "Default language",
    "settings.language.desc": "The language preselected when you open the workbench; if a local draft exists for it, that draft is restored instead.",
    "settings.fontSize.title": "Editor font size",
    "settings.fontSize.desc": "11–20 px; affects only how it looks on this device.",
    "settings.fontSize.decrease": "Decrease font size",
    "settings.fontSize.increase": "Increase font size",

    "settings.group.agent": "Agent",
    "settings.autoDiagnosis.title": "Automatic diagnosis after submitting",
    "settings.autoDiagnosis.desc": "Pulls the multi-agent diagnosis card automatically once a non-AC submission finishes; with this off you can still see existing diagnoses on the submission page.",
    "settings.autoDiagnosis.on": "On",
    "settings.autoDiagnosis.off": "Off",
    "settings.desktopPet.title": "Desktop pet",
    "settings.desktopPet.desc": "The shortcut in the bottom-right corner that hands the current problem to the Agent panel.",
    "settings.desktopPet.show": "Shown",
    "settings.desktopPet.hide": "Hidden",

    "settings.reset": "Restore defaults",
    "settings.goProblems": "Go to problems",
    "settings.note": "Note: language, font size and the diagnosis toggle all take effect immediately; if nothing happens, refresh the page once (preferences are read when the client mounts).",
  },
} as const;

export type SettingsKey = keyof typeof messages.zh;

export function useSettingsMessages() {
  const { language } = useLanguage();
  return (key: SettingsKey) => messages[language][key] ?? messages.zh[key];
}
