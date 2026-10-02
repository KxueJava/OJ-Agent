"use client";

import Link from "next/link";
import { useState } from "react";
import { usePreferences, defaultPreferences, type Preferences } from "../../lib/preferences";
import { useLanguage } from "../../lib/i18n";
import { useSettingsMessages } from "../../lib/messages/settings";
import { BrandMark } from "../../components/brand-mark";
import styles from "./page.module.css";

const languageLabels: Record<Preferences["defaultLanguage"], string> = {
  JAVA_21: "Java 21",
  CPP_17: "C++17",
  C_17: "C17",
};

export default function SettingsPage() {
  const [preferences, update] = usePreferences();
  const { t: nav } = useLanguage();
  const t = useSettingsMessages();
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
          <Link href="/">{nav("nav.home")}</Link>
          <Link href="/problems">{nav("nav.problems")}</Link>
          <Link href="/leaderboard">{nav("nav.leaderboard")}</Link>
          <Link href="/contests">{nav("nav.contests")}</Link>
          <Link href="/submissions">{nav("nav.submissions")}</Link>
        </nav>
      </header>

      <section className={styles.wrap}>
        <div className={styles.heading}>
          <div>
            <p className={styles.eyebrow}>{nav("settings.eyebrow")}</p>
            <h1>{nav("settings.title")}</h1>
            <p>{t("settings.desc")}</p>
          </div>
          {savedAt && <span className={styles.saved}>{t("settings.savedPrefix")}{savedAt}</span>}
        </div>

        <section className={styles.group}>
          <h2>{t("settings.group.workbench")}</h2>
          <div className={styles.row}>
            <div><strong>{t("settings.language.title")}</strong><small>{t("settings.language.desc")}</small></div>
            <select value={preferences.defaultLanguage} onChange={(event) => change({ defaultLanguage: event.target.value as Preferences["defaultLanguage"] })}>
              {(Object.keys(languageLabels) as Preferences["defaultLanguage"][]).map((item) => (
                <option key={item} value={item}>{languageLabels[item]}</option>
              ))}
            </select>
          </div>
          <div className={styles.row}>
            <div><strong>{t("settings.fontSize.title")}</strong><small>{t("settings.fontSize.desc")}</small></div>
            <div className={styles.stepper}>
              <button type="button" onClick={() => change({ editorFontSize: Math.max(11, preferences.editorFontSize - 1) })} aria-label={t("settings.fontSize.decrease")}>−</button>
              <span>{preferences.editorFontSize} px</span>
              <button type="button" onClick={() => change({ editorFontSize: Math.min(20, preferences.editorFontSize + 1) })} aria-label={t("settings.fontSize.increase")}>+</button>
            </div>
          </div>
        </section>

        <section className={styles.group}>
          <h2>{t("settings.group.agent")}</h2>
          <div className={styles.row}>
            <div><strong>{t("settings.autoDiagnosis.title")}</strong><small>{t("settings.autoDiagnosis.desc")}</small></div>
            <label className={styles.switch}>
              <input type="checkbox" checked={preferences.autoDiagnosis} onChange={(event) => change({ autoDiagnosis: event.target.checked })} />
              <span>{preferences.autoDiagnosis ? t("settings.autoDiagnosis.on") : t("settings.autoDiagnosis.off")}</span>
            </label>
          </div>
          <div className={styles.row}>
            <div><strong>{t("settings.desktopPet.title")}</strong><small>{t("settings.desktopPet.desc")}</small></div>
            <label className={styles.switch}>
              <input type="checkbox" checked={preferences.desktopPet} onChange={(event) => change({ desktopPet: event.target.checked })} />
              <span>{preferences.desktopPet ? t("settings.desktopPet.show") : t("settings.desktopPet.hide")}</span>
            </label>
          </div>
        </section>

        <div className={styles.actions}>
          <button type="button" className={styles.ghost} onClick={() => change({ ...defaultPreferences })}>{t("settings.reset")}</button>
          <Link className={styles.primary} href="/problems">{t("settings.goProblems")}</Link>
        </div>

        <p className={styles.note}>
          {t("settings.note")}
        </p>
      </section>
    </main>
  );
}
