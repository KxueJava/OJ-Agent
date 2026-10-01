"use client";

import { Bot, PanelRightClose, Send, ShieldCheck } from "lucide-react";
import { useState } from "react";
import { useAuth } from "../lib/auth";
import styles from "./agent-panel.module.css";

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
  const token=useAuth((state)=>state.accessToken);
  const [message,setMessage]=useState(""); const [reply,setReply]=useState<Reply|null>(null); const [busy,setBusy]=useState(false); const [error,setError]=useState(""); const [live,setLive]=useState("");
  async function ask(text=message) {
    if(!text.trim()) return;
    if(!token){setError("请先登录后使用 Agent");return;}
    setBusy(true);setError("");setReply(null);setLive("");
    try {
      const base=process.env.NEXT_PUBLIC_API_BASE_URL??"http://localhost:8080";
      const response=await fetch(`${base}/api/agent/stream`,{method:"POST",headers:{"Content-Type":"application/json",Authorization:`Bearer ${token}`},body:JSON.stringify({problemVersion,message:text,sourceCode,verdict})});
      if(!response.ok||!response.body){const body=await response.json().catch(()=>null);throw new Error(body?.detail??"Agent 请求失败");}
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
    catch(cause){setError(cause instanceof Error?cause.message:"Agent 暂不可用");}
    finally{setBusy(false);setLive("");}
  }
  function closePanel(){document.querySelector<HTMLButtonElement>('[aria-label="打开 Agent 助手"]')?.click();}
  return <aside className={styles.panel}><div className={styles.head}><button type="button" className={styles.closeButton} onClick={closePanel} aria-label="收回 Agent 侧栏" title="收回侧栏"><PanelRightClose size={16}/></button><div><p>学习助手</p><h2><Bot size={17}/>Tutor / Debugger</h2></div><span><ShieldCheck size={14}/>Safety</span></div><div className={styles.quick}>{["解释题意","提示 1","提示 2","提示 3","分析错误"].map((item)=><button key={item} onClick={()=>ask(item)} disabled={busy}>{item}</button>)}</div>{live&&<div className={styles.reply}><div className={styles.replyContent}>{renderMarkdown(live)}</div></div>}{reply?<div className={styles.reply}><div className={styles.trace}>{reply.trace.map((step)=><span key={step}>{step}</span>)}</div><div className={styles.replyContent}>{renderMarkdown(reply.content)}</div><small>{reply.safety==="PASSED"?"已通过 Safety 审核":"请求已拦截"} · {reply.route}</small></div>:<p className={styles.empty}>可以解释题意、给分级提示，或分析你的公开判题结果。</p>}{error&&<p className={styles.error}>{error}</p>}<div className={styles.input}><input value={message} onChange={(event)=>setMessage(event.target.value)} onKeyDown={(event)=>{if(event.key==="Enter")ask();}} placeholder="问 Agent 一个具体问题"/><button onClick={()=>ask()} disabled={busy||!message.trim()} aria-label="发送"><Send size={15}/></button></div></aside>;
}
