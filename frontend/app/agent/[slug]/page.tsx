"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import AgentPanel from "../../../components/agent-panel";
import { useAuth } from "../../../lib/auth";
import { useAgentMessages } from "../../../lib/messages/agent";
import { useContestLock } from "../../../lib/contest-lock";
import styles from "./agent-page.module.css";
type Problem={problemVersionId:number;title:string;statementMd:string};
export default function AgentPage({params}:{params:Promise<{slug:string}>}){const t=useAgentMessages();const lock=useContestLock();const [slug,setSlug]=useState("");const [problem,setProblem]=useState<Problem|null>(null);const [code,setCode]=useState("");const token=useAuth((s)=>s.accessToken);useEffect(()=>{params.then(({slug:value})=>setSlug(value));},[params]);useEffect(()=>{if(!slug)return;const base=process.env.NEXT_PUBLIC_API_BASE_URL??"http://localhost:8080";fetch(`${base}/api/workspace/problems/${slug}`).then(r=>r.json()).then(p=>setProblem(p.data));setCode(localStorage.getItem(`codeagent-oj:draft:${slug}:JAVA_21`)??"");},[slug]);if(!problem)return <main className={styles.loading}>{t("agent.loading")}</main>;if(!token)return <main className={styles.loading}><p>{t("agent.signInPrompt")}</p><Link href="/login">{t("agent.goLogin")}</Link></main>;if(lock.locked)return <main className={styles.loading}><p>比赛进行中，禁止使用 Agent 助手{lock.slug?`（${lock.slug}）`:""} —— 请独立完成赛题，比赛结束后自动恢复。</p><Link href={`/contests/${lock.slug}`}>返回竞赛</Link></main>;return <main className={styles.page}><header><Link href={`/workbench/${slug}`}>{t("agent.backToWorkbench")}</Link><b>{problem.title} · Agent</b></header><section><article><p>{t("agent.problemContext")}</p><h1>{problem.title}</h1><div>{problem.statementMd}</div></article><AgentPanel problemVersion={problem.problemVersionId} sourceCode={code}/></section></main>}
