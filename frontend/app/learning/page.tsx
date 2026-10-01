"use client";

import Link from "next/link";
import { Check, ChevronLeft, CircleAlert, RotateCcw } from "lucide-react";
import { useEffect, useState } from "react";
import { apiRequest, useAuth } from "../../lib/auth";
import styles from "./learning.module.css";

type Mistake = { id:number; slug:string; title:string; difficulty:string; errorType:string; diagnosis:string; reviewNote?:string; status:string; createdAt:string; submissionId:number };
type Recommendation = { slug:string; title:string; difficulty:string; topic:string; reason:string };
type Plan = { date:string; targetCount:number; completedCount:number; recommendations:Recommendation[] };
type Overview = { openMistakes:number; reviewedMistakes:number; totalEvents:number; today:Plan; mistakes:Mistake[]; recommendations:Recommendation[] };

const labels:Record<string,string> = { WA:"答案错误", CE:"编译失败", RE:"运行异常", TLE:"超时", MLE:"内存超限" };

export default function LearningPage() {
  const token = useAuth((state) => state.accessToken); const [data,setData] = useState<Overview|null>(null); const [error,setError] = useState("");
  async function load(){ try { setData(await apiRequest<Overview>("/api/learning/overview")); } catch (cause) { setError(cause instanceof Error ? cause.message : "学习数据读取失败"); } }
  useEffect(()=>{if(token) load(); else setError("请先登录后查看学习记录");},[token]);
  async function review(id:number){ try { await apiRequest(`/api/learning/mistakes/${id}/review`,{method:"PUT"}); await load(); } catch(cause) { setError(cause instanceof Error?cause.message:"复盘状态更新失败"); } }
  if(!data) return <main className={styles.loading}>{error?<><CircleAlert size={19}/><p>{error}</p><Link href="/login">前往登录</Link></>:<p>正在整理学习记录...</p>}</main>;
  return <main className={styles.shell}><header><Link href="/" className={styles.back}><ChevronLeft size={16}/>返回概览</Link><span className={styles.eyebrow}>LEARNING / REVIEW</span></header><section className={styles.hero}><div><p className={styles.eyebrow}>学习中心</p><h1>把错误变成下一道题。</h1><p>每次失败提交都会留下可复盘的线索，推荐根据你的真实记录更新。</p></div><div className={styles.metrics}><div><strong>{data.openMistakes}</strong><span>待复盘</span></div><div><strong>{data.today.completedCount}/{data.today.targetCount}</strong><span>今日训练</span></div><div><strong>{data.totalEvents}</strong><span>学习事件</span></div></div></section><section className={styles.grid}><article className={styles.main}><div className={styles.sectionTitle}><div><p className={styles.eyebrow}>MISTAKE BOOK</p><h2>错题本</h2></div><span>{data.mistakes.length} 条记录</span></div>{data.mistakes.length?data.mistakes.map((item)=><article className={styles.mistake} key={item.id}><div className={styles.mistakeHead}><div><Link href={`/workbench/${item.slug}`}>{item.title}</Link><small>{labels[item.errorType]??item.errorType} · 提交 #{String(item.submissionId).slice(-6)}</small></div><b className={item.status==="REVIEWED"?styles.reviewed:styles.open}>{item.status==="REVIEWED"?"已复盘":"待复盘"}</b></div><p>{item.diagnosis}</p><div className={styles.review}><span><RotateCcw size={13}/>{item.reviewNote}</span>{item.status!=="REVIEWED"&&<button onClick={()=>review(item.id)}><Check size={13}/>标记已复盘</button>}</div></article>):<div className={styles.empty}><Check size={18}/><p>还没有错题记录。完成一次失败提交后，这里会自动生成归因。</p></div>}</article><aside className={styles.side}><section className={styles.plan}><div className={styles.sectionTitle}><div><p className={styles.eyebrow}>TODAY / {data.today.date}</p><h2>今日训练</h2></div><span>{data.today.completedCount}/{data.today.targetCount}</span></div><div className={styles.planMeter}><i style={{width:`${Math.min(100,data.today.completedCount/data.today.targetCount*100)}%`}}/></div>{data.today.recommendations.map((item)=><Link className={styles.recommend} key={item.slug} href={`/workbench/${item.slug}`}><span><strong>{item.title}</strong><small>{item.topic} · {item.reason}</small></span><b>→</b></Link>)}</section><section className={styles.note}><p className={styles.eyebrow}>LEARNING AGENT</p><h2>下一次提交后继续复盘</h2><p>Reviewer 关注复杂度、边界和代码质量，Learning Agent 根据错误标签安排下一题。</p></section></aside></section></main>;
}
