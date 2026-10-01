"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiRequest, useAuth } from "../../lib/auth";
import { BrandMark } from "../../components/brand-mark";
import styles from "./page.module.css";

type Item = {
  id: number; slug: string; title: string; language: string; status: string;
  verdictMessage?: string; runtimeMs?: number; memoryKb?: number;
  createdAt: string; finishedAt?: string; diagnosed: boolean;
};
type Page = { items: Item[]; page: number; size: number; total: number };

const statusText: Record<string, string> = {
  PENDING: "排队中", RUNNING: "判题中", AC: "通过", WA: "答案错误",
  CE: "编译错误", RE: "运行错误", TLE: "超出时间", MLE: "超出内存",
};
const terminal = new Set(["AC", "WA", "CE", "RE", "TLE", "MLE"]);
const filters = [
  { value: "", label: "全部" },
  { value: "AC", label: "通过" },
  { value: "WA", label: "答案错误" },
  { value: "CE", label: "编译错误" },
  { value: "RE", label: "运行错误" },
  { value: "TLE", label: "超出时间" },
];

function time(value?: string) {
  if (!value) return "--";
  const date = new Date(value);
  return `${date.getMonth() + 1}/${date.getDate()} ${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

export default function SubmissionsPage() {
  const token = useAuth((state) => state.accessToken);
  const [data, setData] = useState<Page | null>(null);
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    if (!token) return;
    const query = new URLSearchParams({ page: String(page), size: "20" });
    if (status) query.set("status", status);
    setError("");
    apiRequest<Page>(`/api/submissions?${query.toString()}`)
      .then(setData)
      .catch((cause) => setError(cause instanceof Error ? cause.message : "加载提交记录失败"));
  }, [token, page, status]);

  const pageCount = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1;

  return (
    <main className={styles.page}>
      <header className="app-topbar">
        <Link className="brand" href="/"><BrandMark />CodeAgent OJ</Link>
        <nav aria-label="主导航">
          <Link href="/">主页</Link>
          <Link href="/problems">题库</Link>
          <Link href="/leaderboard">排行榜</Link>
          <Link className="active" href="/submissions">提交记录</Link>
        </nav>
      </header>

      <section className={styles.wrap}>
        <div className={styles.heading}>
          <div>
            <p className={styles.eyebrow}>SUBMISSIONS</p>
            <h1>提交记录</h1>
            <p>每一次提交的判题结果、用时与自动诊断都在这里，只显示你自己的记录。</p>
          </div>
          <div className={styles.filters}>
            {filters.map((item) => (
              <button key={item.value} type="button"
                className={status === item.value ? styles.filterActive : styles.filter}
                onClick={() => { setStatus(item.value); setPage(0); }}>{item.label}</button>
            ))}
          </div>
        </div>

        {!token && <p className={styles.state}><Link href="/login">登录</Link>后查看你的提交记录。</p>}
        {error && <p className={styles.state}>{error}</p>}
        {token && !data && !error && <p className={styles.state}>正在加载…</p>}
        {data && data.items.length === 0 && !error && <p className={styles.state}>还没有提交记录，去<Link href="/problems">题库</Link>挑一道题开始练习。</p>}

        {data && data.items.length > 0 && <>
          <div className={styles.table}>
            <div className={styles.head}>
              <span>题目</span><span>语言</span><span>状态</span><span>用时</span><span>内存</span><span>提交时间</span><span>诊断</span>
            </div>
            {data.items.map((item) => (
              <Link className={styles.row} key={item.id} href={`/submissions/${item.id}`}>
                <span className={styles.title}>{item.title}</span>
                <span className={styles.mono}>{item.language.replace("_", " ")}</span>
                <span className={styles[`s_${terminal.has(item.status) ? item.status : "PENDING"}`] ?? styles.mono}>
                  {statusText[item.status] ?? item.status}
                </span>
                <span className={styles.mono}>{item.runtimeMs == null ? "--" : `${item.runtimeMs} ms`}</span>
                <span className={styles.mono}>{item.memoryKb == null ? "--" : `${item.memoryKb} KB`}</span>
                <span className={styles.mono}>{time(item.createdAt)}</span>
                <span className={styles.diagnosed}>{item.diagnosed ? "有" : "—"}</span>
              </Link>
            ))}
          </div>

          <div className={styles.pager}>
            <span>共 {data.total} 条 · 第 {data.page + 1}/{pageCount} 页</span>
            <div>
              <button type="button" disabled={data.page <= 0} onClick={() => setPage((value) => Math.max(0, value - 1))}>上一页</button>
              <button type="button" disabled={data.page + 1 >= pageCount} onClick={() => setPage((value) => value + 1)}>下一页</button>
            </div>
          </div>
        </>}
      </section>
    </main>
  );
}
