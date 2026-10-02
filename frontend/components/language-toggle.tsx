"use client";

import { useLanguage } from "../lib/i18n";
import styles from "./language-toggle.module.css";

export default function LanguageToggle() {
  const { language, setLanguage, t } = useLanguage();
  return <div className={styles.wrap} role="group" aria-label={`${t("language.label")} / 语言`} title={`${t("language.label")} / 语言`}>
    <button type="button" className={language === "zh" ? styles.active : styles.button} aria-pressed={language === "zh"} onClick={() => setLanguage("zh")}>{t("language.zh")}</button>
    <button type="button" className={language === "en" ? styles.active : styles.button} aria-pressed={language === "en"} onClick={() => setLanguage("en")}>EN</button>
  </div>;
}
