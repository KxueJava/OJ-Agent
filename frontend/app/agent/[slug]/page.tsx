"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import AgentPanel from "../../../components/agent-panel";
import { useAuth } from "../../../lib/auth";
import styles from "./agent-page.module.css";
type Problem={problemVersionId:number;title:string;statementMd:string};
export default function AgentPage({params}:{params:Promise<{slug:string}>}){const [slug,setSlug]=useState("");const [problem,setProblem]=useState<Problem|null>(null);const [code,setCode]=useState("");const token=useAuth((s)=>s.accessToken);useEffect(()=>{params.then(({slug:value})=>setSlug(value));},[params]);useEffect(()=>{if(!slug)return;const base=process.env.NEXT_PUBLIC_API_BASE_URL??"http://localhost:8080";fetch(`${base}/api/workspace/problems/${slug}`).then(r=>r.json()).then(p=>setProblem(p.data));setCode(localStorage.getItem(`codeagent-oj:draft:${slug}:JAVA_21`)??"");},[slug]);if(!problem)return <main className={styles.loading}>正在载入 Agent...</main>;if(!token)return <main className={styles.loading}><p>请先登录后使用 Agent。</p><Link href="/login">前往登录</Link></main>;return <main className={styles.page}><header><Link href={`/workbench/${slug}`}>返回工作台</Link><b>{problem.title} · Agent</b></header><section><article><p>题目上下文</p><h1>{problem.title}</h1><div>{problem.statementMd}</div></article><AgentPanel problemVersion={problem.problemVersionId} sourceCode={code}/></section></main>}
