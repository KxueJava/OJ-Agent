"use client";

import Link from "next/link";
import { useState } from "react";
import { usePreferences, defaultPreferences, type Preferences } from "../../lib/preferences";
import { BrandMark } from "../../components/brand-mark";
import styles from "./page.module.css";

const languageLabels: Record<Preferences["defaultLanguage"], string> = {
  JAVA_21: "Java 21",
  CPP_17: "C++17",
  C_17: "C17",
};

export default function SettingsPage() {
  const [preferences, update] = usePreferences();
  const [savedAt, setSavedAt] = useState("");

  const change = (patch: Partial<Preferences>) => {
    update(patch);
    setSavedAt(new Date().toLocaleTimeString("zh-CN", { hour12: false }));
  };

  return (
    <main className={styles.page}>
      <header className="app-topbar">
        <Link className="brand" href="/"><BrandMark />CodeAgent OJ</Link>
        <nav aria-label="主导航">
          <Link href="/">主页</Link>
          <Link href="/problems">题库</Link>
          <Link href="/leaderboard">排行榜</Link>
          <Link href="/submissions">提交记录</Link>
        </nav>
      </header>

      <section className={styles.wrap}>
        <div className={styles.heading}>
          <div>
            <p className={styles.eyebrow}>SETTINGS</p>
            <h1>设置</h1>
            <p>这些偏好保存在本机浏览器（localStorage），不上传服务端，换设备不会同步。</p>
          </div>
          {savedAt && <span className={styles.saved}>已保存 {savedAt}</span>}
        </div>

        <section className={styles.group}>
          <h2>工作台</h2>
          <div className={styles.row}>
            <div><strong>默认编程语言</strong><small>打开工作台时预选的语言；如果该语言下已有本机草稿，会直接恢复草稿。</small></div>
            <select value={preferences.defaultLanguage} onChange={(event) => change({ defaultLanguage: event.target.value as Preferences["defaultLanguage"] })}>
              {(Object.keys(languageLabels) as Preferences["defaultLanguage"][]).map((item) => (
                <option key={item} value={item}>{languageLabels[item]}</option>
              ))}
            </select>
          </div>
          <div className={styles.row}>
            <div><strong>编辑器字号</strong><small>11–20 px，只影响你本机的显示。</small></div>
            <div className={styles.stepper}>
              <button type="button" onClick={() => change({ editorFontSize: Math.max(11, preferences.editorFontSize - 1) })} aria-label="减小字号">−</button>
              <span>{preferences.editorFontSize} px</span>
              <button type="button" onClick={() => change({ editorFontSize: Math.min(20, preferences.editorFontSize + 1) })} aria-label="增大字号">+</button>
            </div>
          </div>
        </section>

        <section className={styles.group}>
          <h2>Agent</h2>
          <div className={styles.row}>
            <div><strong>提交后自动诊断</strong><small>非 AC 提交完成后自动拉取多 Agent 诊断卡片；关掉后仍可在提交详情页看到已有诊断。</small></div>
            <label className={styles.switch}>
              <input type="checkbox" checked={preferences.autoDiagnosis} onChange={(event) => change({ autoDiagnosis: event.target.checked })} />
              <span>{preferences.autoDiagnosis ? "开启" : "关闭"}</span>
            </label>
          </div>
          <div className={styles.row}>
            <div><strong>桌面宠物</strong><small>右下角的快捷入口，可以直接把当前题目丢给 Agent 面板。</small></div>
            <label className={styles.switch}>
              <input type="checkbox" checked={preferences.desktopPet} onChange={(event) => change({ desktopPet: event.target.checked })} />
              <span>{preferences.desktopPet ? "显示" : "隐藏"}</span>
            </label>
          </div>
        </section>

        <div className={styles.actions}>
          <button type="button" className={styles.ghost} onClick={() => change({ ...defaultPreferences })}>恢复默认</button>
          <Link className={styles.primary} href="/problems">去题库</Link>
        </div>

        <p className={styles.note}>
          提示：语言、字号、诊断开关都是即时生效的；如果改完没反应，刷新一次页面即可（偏好读取发生在客户端挂载时）。
        </p>
      </section>
    </main>
  );
}
