"use client";

import { useCallback, useEffect, useState } from "react";
import { apiRequest } from "../lib/auth";
import Markdown from "./markdown";

/**
 * G6：Agent 会话历史（每题一段对话）。
 *
 * 自包含：自己拉列表、自己拉某段消息、自己清空 —— 主面板只需要一个开关与一个按钮。
 * 数据来源是服务端的审计消息表（含"哪个 Agent 回答"与"是否进入模型上下文"），
 * 因此这里显示的就是用户当时真实看到的回答。
 */
type SessionRow = {
  id: string; problemVersionId: string; updatedAt: string;
  messageCount: string; lastPreview: string | null; problemSlug: string | null; problemTitle: string | null;
};
type Message = { role: string; agent: string; content: string; safetyStatus: string; inMemory: boolean; createdAt: string };

const mono = "Consolas,'SF Mono',Menlo,monospace";

export default function AgentHistory({ onBack }: { onBack: () => void }) {
  const [rows, setRows] = useState<SessionRow[] | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);
  const [messages, setMessages] = useState<Message[] | null>(null);
  const [error, setError] = useState("");

  const load = useCallback(() => {
    apiRequest<SessionRow[]>("/api/agent/sessions").then(setRows).catch((cause) => setError(cause instanceof Error ? cause.message : "加载失败"));
  }, []);
  useEffect(load, [load]);

  const open = async (id: string) => {
    setOpenId(id); setMessages(null); setError("");
    try { setMessages(await apiRequest<Message[]>(`/api/agent/sessions/${id}`)); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "加载失败"); }
  };

  const clear = async (id: string) => {
    if (!window.confirm("清空这段对话的消息？（已生成的历史诊断会保留）")) return;
    try { await apiRequest(`/api/agent/sessions/${id}`, { method: "DELETE" }); setOpenId(null); setMessages(null); load(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "清空失败"); }
  };

  const stamp = (value: string) => String(value).slice(5, 16).replace("T", " ");

  return (
    <div style={{ display: "grid", gap: 10, padding: "10px 0", minHeight: 0 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
        <button type="button" onClick={() => { setOpenId(null); setMessages(null); onBack(); }} style={{ border: "1px solid #c9c7bf", background: "transparent", color: "#4f554f", font: "700 11px inherit", padding: "4px 10px", cursor: "pointer" }}>← 返回对话</button>
        <b style={{ fontSize: 12, color: "#252a2d" }}>历史会话</b>
        <span style={{ color: "#858981", font: `10px ${mono}` }}>{rows ? `${rows.length} 段` : "加载中…"}</span>
      </div>

      {error && <p style={{ margin: 0, color: "#a74743", fontSize: 11 }}>{error}</p>}

      {!openId && rows && rows.length === 0 && <p style={{ margin: 0, color: "#858981", fontSize: 11 }}>还没有历史对话。在任意题目里问一句，这里就会出现记录。</p>}

      {!openId && rows && rows.map((row) => (
        <div key={row.id} style={{ border: "1px solid #e2e0d8", background: "#fff", padding: "8px 10px" }}>
          <button type="button" onClick={() => void open(row.id)} style={{ display: "block", width: "100%", border: 0, background: "transparent", textAlign: "left", padding: 0, cursor: "pointer" }}>
            <b style={{ fontSize: 12, color: "#252a2d" }}>{row.problemTitle ?? row.problemSlug ?? `#${row.problemVersionId}`}</b>
            <small style={{ display: "block", margin: "3px 0 0", color: "#858981", font: `10px ${mono}` }}>{stamp(row.updatedAt)} · {row.messageCount} 条消息</small>
            {row.lastPreview && <small style={{ display: "block", margin: "4px 0 0", color: "#6f736e", fontSize: 11, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{row.lastPreview}</small>}
          </button>
        </div>
      ))}

      {openId && (
        <div style={{ display: "grid", gap: 8 }}>
          <button type="button" onClick={() => { setOpenId(null); setMessages(null); }} style={{ justifySelf: "start", border: "1px solid #c9c7bf", background: "transparent", color: "#4f554f", font: "700 11px inherit", padding: "4px 10px", cursor: "pointer" }}>← 会话列表</button>
          {messages === null && <p style={{ margin: 0, color: "#858981", fontSize: 11 }}>加载中…</p>}
          {messages?.length === 0 && <p style={{ margin: 0, color: "#858981", fontSize: 11 }}>这段对话已被清空。</p>}
          {messages?.map((message, index) => (
            <div key={index} style={{ borderLeft: `3px solid ${message.role === "USER" ? "#c9c7bf" : "#d37737"}`, background: message.role === "USER" ? "#f7f5ef" : "#fff", padding: "8px 10px" }}>
              <small style={{ display: "flex", gap: 6, alignItems: "center", color: "#858981", font: `10px ${mono}` }}>
                <span>{message.role === "USER" ? "我" : (message.agent || "Agent")}</span>
                <span>{stamp(message.createdAt)}</span>
                {message.role !== "USER" && message.safetyStatus !== "PASSED" && <span style={{ color: "#95611e" }}>{message.safetyStatus}</span>}
                {message.role !== "USER" && !message.inMemory && <span style={{ color: "#a2a49d" }}>仅记录</span>}
              </small>
              <div style={{ marginTop: 5 }}>
                {message.role === "USER" ? <p style={{ margin: 0, fontSize: 13, color: "#3f4540" }}>{message.content}</p> : <Markdown text={message.content} />}
              </div>
            </div>
          ))}
          {openId && messages && messages.length > 0 && (
            <button type="button" onClick={() => void clear(openId)} style={{ justifySelf: "start", border: "1px solid #ddb3b0", background: "transparent", color: "#a74743", font: "700 11px inherit", padding: "4px 10px", cursor: "pointer" }}>清空这段对话</button>
          )}
        </div>
      )}
    </div>
  );
}
