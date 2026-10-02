"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import Workbench from "../../../../workbench/[slug]/page";
import { publicApiRequest } from "../../../../../lib/auth";

/**
 * 竞赛内工作台：/contests/<竞赛slug>/workbench/<题目slug>
 *
 * 直接复用普通工作台组件（它只接收 params），不改造它 —— 出问题时删掉这个路由即可回退。
 * 这一层只负责"竞赛上下文"：顶部横幅说明提交会计入本场比赛，并给出返回竞赛/榜单的入口。
 * 注意：Agent 锁与提交归属都由后端按"题目是否属于进行中的比赛"判定，不依赖这里的 URL。
 */
export default function ContestWorkbench({ params }: { params: Promise<{ slug: string; problem: string }> }) {
  const [contestSlug, setContestSlug] = useState("");
  const [problemSlug, setProblemSlug] = useState("");
  const [title, setTitle] = useState("");
  const [status, setStatus] = useState("");

  useEffect(() => {
    params.then((resolved) => { setContestSlug(resolved.slug); setProblemSlug(resolved.problem); });
  }, [params]);

  useEffect(() => {
    if (!contestSlug) return;
    publicApiRequest<{ contest: { title: string; status: string } }>(`/api/contests/${contestSlug}`)
      .then((detail) => { setTitle(detail.contest.title); setStatus(detail.contest.status); })
      .catch(() => setTitle(""));
  }, [contestSlug]);

  return (
    <div>
      <div style={{ position: "sticky", top: 0, zIndex: 60, display: "flex", flexWrap: "wrap", alignItems: "center", gap: 12, padding: "8px 18px", background: "#252a2d", color: "#f3f1eb", fontSize: 12 }}>
        <span style={{ padding: "2px 8px", border: "1px solid #d37737", color: "#e2a078", font: "700 10px Consolas,monospace", letterSpacing: ".08em" }}>CONTEST</span>
        <strong style={{ fontWeight: 700 }}>{title || contestSlug}</strong>
        {status && <span style={{ color: "#9aa19b" }}>{status}</span>}
        <span style={{ color: "#e2a078" }}>本次提交会计入本场竞赛</span>
        <span style={{ flex: 1 }} />
        <Link href={`/contests/${contestSlug}`} style={{ color: "#f3f1eb", textDecoration: "underline" }}>返回竞赛</Link>
        <Link href={`/contests/${contestSlug}#standings`} style={{ color: "#f3f1eb", textDecoration: "underline" }}>实时榜单</Link>
      </div>
      {problemSlug && <Workbench params={Promise.resolve({ slug: problemSlug })} />}
    </div>
  );
}
