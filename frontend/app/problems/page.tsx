"use client";

import Link from "next/link";
import { Search, Star } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { publicApiRequest } from "../../lib/auth";
import { BrandMark } from "../../components/brand-mark";

type Tag = { name:string; slug:string };
type Problem = { slug:string; title:string; difficulty:"EASY"|"MEDIUM"|"HARD"; tags:Tag[]; favorite:boolean };
type Result = { items:Problem[]; page:number; size:number; total:number };
const label = { EASY:"简单", MEDIUM:"中等", HARD:"困难" };
const pageSize = 10;

export default function ProblemsPage() {
  const [items,setItems] = useState<Problem[]>([]); const [tags,setTags] = useState<Tag[]>([]); const [query,setQuery] = useState(""); const [difficulty,setDifficulty] = useState(""); const [tag,setTag] = useState(""); const [page,setPage] = useState(0); const [total,setTotal] = useState(0); const [loading,setLoading] = useState(false); const [error,setError] = useState("");
  const pageCount = Math.max(1,Math.ceil(total/pageSize));
  useEffect(()=>{publicApiRequest<Tag[]>("/api/tags").then(setTags).catch(()=>setError("题库服务暂不可用"));},[]);
  useEffect(()=>{const params=new URLSearchParams({page:String(page),size:String(pageSize)});if(query)params.set("query",query);if(difficulty)params.set("difficulty",difficulty);if(tag)params.set("tag",tag);setLoading(true);publicApiRequest<Result>(`/api/problems?${params}`).then((data)=>{setItems(data.items);setTotal(data.total);setError("");}).catch((cause)=>setError(cause instanceof Error?cause.message:"加载题库失败")).finally(()=>setLoading(false));},[query,difficulty,tag,page]);
  const setFilter=(setter:(value:string)=>void,value:string)=>{setter(value);setPage(0);};
  const pages=useMemo(()=>Array.from({length:pageCount},(_,index)=>index),[pageCount]);
  return <main className="catalog"><header className="app-topbar"><Link className="brand" href="/"><BrandMark />CodeAgent OJ</Link><nav><Link href="/">主页</Link><Link href="/problems">题库</Link><Link href="/leaderboard">排行榜</Link></nav><Link className="top-login" href="/workspace">我的工作台</Link></header><section className="catalog-heading"><div><p className="eyebrow">PROBLEM CATALOG</p><h1>题库</h1><p>选择一个明确的问题，开始今天的训练。</p></div></section><section className="catalog-filters"><label className="catalog-search"><Search size={16}/><input value={query} onChange={(e)=>setFilter(setQuery,e.target.value)} placeholder="搜索题目、标签" /></label><select value={difficulty} onChange={(e)=>setFilter(setDifficulty,e.target.value)}><option value="">全部难度</option><option value="EASY">简单</option><option value="MEDIUM">中等</option><option value="HARD">困难</option></select><select value={tag} onChange={(e)=>setFilter(setTag,e.target.value)}><option value="">全部标签</option>{tags.map((item)=><option key={item.slug} value={item.slug}>{item.name}</option>)}</select></section>{error?<p className="catalog-error">{error}</p>:<><section className={`problem-table ${loading?"is-loading":""}`}><div className="problem-table-head"><span>题目</span><span>难度</span><span>标签</span><span /></div>{items.map((item)=><Link className="catalog-row" key={item.slug} href={`/problems/${item.slug}`}><strong>{item.title}</strong><span className={`difficulty ${item.difficulty.toLowerCase()}`}>{label[item.difficulty]}</span><span className="catalog-tags">{item.tags.map((t)=><i key={t.slug}>{t.name}</i>)}</span><span className={item.favorite?"favorite-on":"favorite-off"}><Star size={16} fill={item.favorite?"currentColor":"none"}/></span></Link>)}{!loading&&!items.length&&<p className="empty-state">没有匹配的题目。</p>}</section><div className="catalog-pagination"><span>显示 {total===0?0:page*pageSize+1}-{Math.min((page+1)*pageSize,total)} / 共 {total} 道题</span><div className="pagination-controls"><button type="button" onClick={()=>setPage((value)=>Math.max(0,value-1))} disabled={page===0||loading}>上一页</button>{pages.map((index)=><button type="button" key={index} className={index===page?"current":""} onClick={()=>setPage(index)} disabled={loading}>{index+1}</button>)}<button type="button" onClick={()=>setPage((value)=>Math.min(pageCount-1,value+1))} disabled={page>=pageCount-1||loading}>下一页</button></div></div></>}</main>;
}
