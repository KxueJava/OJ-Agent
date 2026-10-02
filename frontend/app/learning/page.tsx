"use client";

import Link from "next/link";
import { Check, ChevronLeft, CircleAlert, RotateCcw } from "lucide-react";
import { useEffect, useState } from "react";
import { apiRequest, useAuth } from "../../lib/auth";
import { useLearningMessages, type LearningKey } from "../../lib/messages/learning";
import styles from "./learning.module.css";

type Mistake = { id:number; slug:string; title:string; difficulty:string; errorType:string; diagnosis:string; reviewNote?:string; status:string; createdAt:string; submissionId:number };
type Recommendation = { slug:string; title:string; difficulty:string; topic:string; reason:string };
type Plan = { date:string; targetCount:number; completedCount:number; recommendations:Recommendation[] };
type Overview = { openMistakes:number; reviewedMistakes:number; totalEvents:number; today:Plan; mistakes:Mistake[]; recommendations:Recommendation[] };

const labels:Record<string,LearningKey> = { WA:"learning.verdict.WA", CE:"learning.verdict.CE", RE:"learning.verdict.RE", TLE:"learning.verdict.TLE", MLE:"learning.verdict.MLE" };

export default function LearningPage() {
  const t = useLearningMessages();
  const token = useAuth((state) => state.accessToken); const [data,setData] = useState<Overview|null>(null); const [error,setError] = useState("");
  async function load(){ try { setData(await apiRequest<Overview>("/api/learning/overview")); } catch (cause) { setError(cause instanceof Error ? cause.message : t("learning.loadError")); } }
  useEffect(()=>{if(token) load(); else setError(t("learning.loginRequired"));},[token]);
  async function review(id:number){ try { await apiRequest(`/api/learning/mistakes/${id}/review`,{method:"PUT"}); await load(); } catch(cause) { setError(cause instanceof Error?cause.message:t("learning.reviewError")); } }
  if(!data) return <main className={styles.loading}>{error?<><CircleAlert size={19}/><p>{error}</p><Link href="/login">{t("learning.goToLogin")}</Link></>:<p>{t("learning.loading")}</p>}</main>;
  return <main className={styles.shell}><header><Link href="/" className={styles.back}><ChevronLeft size={16}/>{t("learning.backOverview")}</Link><span className={styles.eyebrow}>LEARNING / REVIEW</span></header><section className={styles.hero}><div><p className={styles.eyebrow}>{t("learning.heroEyebrow")}</p><h1>{t("learning.heroTitle")}</h1><p>{t("learning.heroDesc")}</p></div><div className={styles.metrics}><div><strong>{data.openMistakes}</strong><span>{t("learning.status.pending")}</span></div><div><strong>{data.today.completedCount}/{data.today.targetCount}</strong><span>{t("learning.today.title")}</span></div><div><strong>{data.totalEvents}</strong><span>{t("learning.metric.events")}</span></div></div></section><section className={styles.grid}><article className={styles.main}><div className={styles.sectionTitle}><div><p className={styles.eyebrow}>MISTAKE BOOK</p><h2>{t("learning.mistakes.title")}</h2></div><span>{t("learning.mistakes.countPrefix")}{String(data.mistakes.length)}{t("learning.mistakes.countSuffix")}</span></div>{data.mistakes.length?data.mistakes.map((item)=><article className={styles.mistake} key={item.id}><div className={styles.mistakeHead}><div><Link href={`/workbench/${item.slug}`}>{item.title}</Link><small>{t(labels[item.errorType])??item.errorType} · {t("learning.mistakes.submissionLabel")}{String(item.submissionId).slice(-6)}</small></div><b className={item.status==="REVIEWED"?styles.reviewed:styles.open}>{item.status==="REVIEWED"?t("learning.status.reviewed"):t("learning.status.pending")}</b></div><p>{item.diagnosis}</p><div className={styles.review}><span><RotateCcw size={13}/>{item.reviewNote}</span>{item.status!=="REVIEWED"&&<button onClick={()=>review(item.id)}><Check size={13}/>{t("learning.mistakes.markReviewed")}</button>}</div></article>):<div className={styles.empty}><Check size={18}/><p>{t("learning.mistakes.empty")}</p></div>}</article><aside className={styles.side}><section className={styles.plan}><div className={styles.sectionTitle}><div><p className={styles.eyebrow}>TODAY / {data.today.date}</p><h2>{t("learning.today.title")}</h2></div><span>{data.today.completedCount}/{data.today.targetCount}</span></div><div className={styles.planMeter}><i style={{width:`${Math.min(100,data.today.completedCount/data.today.targetCount*100)}%`}}/></div>{data.today.recommendations.map((item)=><Link className={styles.recommend} key={item.slug} href={`/workbench/${item.slug}`}><span><strong>{item.title}</strong><small>{item.topic} · {item.reason}</small></span><b>→</b></Link>)}</section><section className={styles.note}><p className={styles.eyebrow}>LEARNING AGENT</p><h2>{t("learning.agent.title")}</h2><p>{t("learning.agent.desc")}</p></section></aside></section></main>;
}
