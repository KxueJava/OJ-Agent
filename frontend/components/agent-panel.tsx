"use client";

import { Bot, History, PanelRightClose, Send, ShieldCheck } from "lucide-react";
import { useState } from "react";
import { useAuth } from "../lib/auth";
import styles from "./agent-panel.module.css";
import { useAgentMessages, type AgentKey } from "../lib/messages/agent";
import Markdown from "./markdown";
import AgentHistory from "./agent-history";

type Reply = { content:string; route:string; safety:string; trace:string[] };

function inlineMarkdown(value:string) {
  return value.split(/(`[^`]+`|\*\*[^*]+\*\*)/g).map((part,index)=>{
    if(part.startsWith("`")&&part.endsWith("`")) return <code key={index}>{part.slice(1,-1)}</code>;
    if(part.startsWith("**")&&part.endsWith("**")) return <strong key={index}>{part.slice(2,-2)}</strong>;
    return <span key={index}>{part}</span>;
  });
}

function renderMarkdown(content:string) {
  return content.split(/(```[\s\S]*?```)/g).map((block,index)=>{
    if(block.startsWith("```")) {
      const lines=block.slice(3,-3).replace(/^\w+\r?\n/,"").replace(/^\n/,"");
      return <pre className={styles.codeBlock} key={index}><code>{lines.trimEnd()}</code></pre>;
    }
    return <div className={styles.markdown} key={index}>{block.split(/\r?\n/).map((line,lineIndex)=>{
      if(!line.trim()) return <span className={styles.markdownSpace} key={lineIndex}/>;
      const heading=line.match(/^#{1,3}\s+(.+)/);
      if(heading) return <h3 key={lineIndex}>{inlineMarkdown(heading[1])}</h3>;
      const bullet=line.match(/^\s*[-*]\s+(.+)/);
      if(bullet) return <div className={styles.listItem} key={lineIndex}><i/>{inlineMarkdown(bullet[1])}</div>;
      return <p key={lineIndex}>{inlineMarkdown(line)}</p>;
    })}</div>;
  });
}

export default function AgentPanel({problemVersion,sourceCode,verdict}:{problemVersion:number;sourceCode:string;verdict?:string}) {
  const token=useAuth((state)=>state.accessToken); const t=useAgentMessages();
  const [message,setMessage]=useState(""); const [historyOpen,setHistoryOpen]=useState(false); const [reply,setReply]=useState<Reply|null>(null); const [busy,setBusy]=useState(false); const [error,setError]=useState(""); const [live,setLive]=useState("");
  async function ask(text=message) {
    if(!text.trim()) return;
    if(!token){setError(t("agent.loginRequired"));return;}
    setBusy(true);setError("");setReply(null);setLive("");
    try {
      const base=process.env.NEXT_PUBLIC_API_BASE_URL??"http://localhost:8080";
      const response=await fetch(`${base}/api/agent/stream`,{method:"POST",headers:{"Content-Type":"application/json",Authorization:`Bearer ${token}`},body:JSON.stringify({problemVersion,message:text,sourceCode,verdict})});
      if(!response.ok||!response.body){const body=await response.json().catch(()=>null);throw new Error(body?.detail??(response.status===403?"无法使用 Agent 助手（HTTP 403）：比赛进行中禁止使用，或当前账号无权限":t("agent.requestFailed")));}
      const reader=response.body.getReader();const decoder=new TextDecoder();let buffer="";let streamed="";
      for(;;){
        const {value,done}=await reader.read();if(done)break;
        buffer+=decoder.decode(value,{stream:true});
        const frames=buffer.split("\n\n");buffer=frames.pop()??"";
        for(const frame of frames){
          const name=/^event:\s*(.+)$/m.exec(frame)?.[1];
          const data=/^data:\s*(.+)$/m.exec(frame)?.[1];
          if(!data)continue;
          let payload:Record<string,unknown>;
          try{payload=JSON.parse(data);}catch{continue;}
          if(name==="message"){streamed+=String(payload.content??"");setLive(streamed);}
          else if(name==="replace"){streamed=String(payload.content??"");setLive(streamed);}
          else if(name==="done"){setReply({content:String(payload.content??streamed),route:String(payload.route??"Tutor"),safety:String(payload.safety??"PASSED"),trace:Array.isArray(payload.trace)?payload.trace.map(String):[]});setMessage("");setLive("");}
        }
      }
    }
    catch(cause){setError(cause instanceof Error?cause.message:t("agent.unavailable"));}
    finally{setBusy(false);setLive("");}
  }
  function closePanel(){document.querySelector<HTMLButtonElement>('[aria-label="打开 Agent 助手"]')?.click();}
  return <aside className={styles.panel}><div className={styles.head}><button type="button" className={styles.closeButton} onClick={closePanel} aria-label={t("agent.closeLabel")} title={t("agent.closeTitle")}><PanelRightClose size={16}/></button><button type="button" className={styles.closeButton} onClick={()=>setHistoryOpen((value)=>!value)} aria-label="历史会话" title="历史会话"><History size={16}/></button><div><p>{t("agent.tutor")}</p><h2><Bot size={17}/>Tutor / Debugger</h2></div><span><ShieldCheck size={14}/>Safety</span></div>{historyOpen?<AgentHistory onBack={()=>setHistoryOpen(false)}/>:<> <div className={styles.quick}>{(["agent.quickExplain","agent.quickHint1","agent.quickHint2","agent.quickHint3","agent.quickAnalyze"] as AgentKey[]).map((key)=><button key={key} onClick={()=>ask(t(key))} disabled={busy}>{t(key)}</button>)}</div>{live&&<div className={styles.reply}><div className={styles.replyContent}><Markdown text={live} /></div></div>}{reply?<div className={styles.reply}><div className={styles.trace}>{reply.trace.map((step)=><span key={step}>{step}</span>)}</div><div className={styles.replyContent}><Markdown text={reply.content} /></div><small>{reply.safety==="PASSED"?t("agent.safetyPassed"):t("agent.safetyBlocked")} · {reply.route}</small></div>:<p className={styles.empty}>{t("agent.emptyHint")}</p>}{error&&<p className={styles.error}>{error}</p>}<div className={styles.input}><input value={message} onChange={(event)=>setMessage(event.target.value)} onKeyDown={(event)=>{if(event.key==="Enter")ask();}} placeholder={t("agent.placeholder")}/><button onClick={()=>ask()} disabled={busy||!message.trim()} aria-label={t("agent.send")}><Send size={15}/></button></div></>}</aside>;
}
