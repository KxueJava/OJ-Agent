"use client";
import { useLanguage } from "../../lib/i18n";
import { useProblemsMessages } from "../../lib/messages/problems";

import Link from "next/link";
import SiteNav from "../../components/site-nav";
import { Search, Star } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { apiRequest, publicApiRequest, useAuth } from "../../lib/auth";
import { BrandMark } from "../../components/brand-mark";

type Tag = { name:string; slug:string };
type Problem = { slug:string; title:string; difficulty:"EASY"|"MEDIUM"|"HARD"; tags:Tag[]; favorite:boolean };
type Result = { items:Problem[]; page:number; size:number; total:number };
const label = { EASY:"problems.difficulty.easy", MEDIUM:"problems.difficulty.medium", HARD:"problems.difficulty.hard" } as const;
const pageSize = 10;

export default function ProblemsPage() {
  const { t } = useLanguage();
  const tp = useProblemsMessages();
  const [items,setItems] = useState<Problem[]>([]); const [tags,setTags] = useState<Tag[]>([]); const [query,setQuery] = useState(""); const [difficulty,setDifficulty] = useState(""); const [tag,setTag] = useState(""); const [page,setPage] = useState(0); const [total,setTotal] = useState(0); const [loading,setLoading] = useState(false); const [error,setError] = useState("");
  /** 收藏页筛选：必须在 useEffect 里从 URL 读 —— 惰性初始化在这个页面不可用（SSR 时没有 window，
   *  水合会沿用服务端那份 false，初始化函数不会在浏览器重跑）。 */
  const [favoriteOnly,setFavoriteOnly] = useState(false);
  useEffect(() => {
    if (new URLSearchParams(window.location.search).get("favoriteOnly") === "true") setFavoriteOnly(true);
  }, []);
  const toggleFavorite = async (slug:string, current:boolean) => {
    // 乐观更新：星标立即变，失败再回滚 —— 收藏是高频轻操作，等网络来回会让手感很差
    setItems((list:Problem[]) => list.map((item) => item.slug===slug ? {...item, favorite:!current} : item));
    try { await apiRequest(`/api/problems/${slug}/favorite?enabled=${!current}`, { method: "PUT" }); }
    catch { setItems((list:Problem[]) => list.map((item) => item.slug===slug ? {...item, favorite:current} : item)); }
  };
  const pageCount = Math.max(1,Math.ceil(total/pageSize));
  /** 我已解出的题目 slug（后端 /api/submissions/solved 返回 AC 过的题），用于在列表里标「AC」。 */
  const [solved,setSolved] = useState<Set<string>>(new Set());
  const userId = useAuth((state)=>state.user?.id);
  useEffect(()=>{ if(!userId){setSolved(new Set());return;} apiRequest<string[]>("/api/submissions/solved").then((list)=>setSolved(new Set(list))).catch(()=>setSolved(new Set())); },[userId]);
  useEffect(()=>{publicApiRequest<Tag[]>("/api/tags").then(setTags).catch(()=>setError(tp("problems.error.tags")));},[]);
  useEffect(()=>{const params=new URLSearchParams({page:String(page),size:String(pageSize)});if(query)params.set("query",query);if(difficulty)params.set("difficulty",difficulty);if(tag)params.set("tag",tag);if(favoriteOnly)params.set("favoriteOnly","true");setLoading(true);apiRequest<Result>(`/api/problems?${params}`).then((data)=>{setItems(data.items);setTotal(data.total);setError("");}).catch((cause)=>setError(cause instanceof Error?cause.message:tp("problems.error.load"))).finally(()=>setLoading(false));},[query,difficulty,tag,page,favoriteOnly]);
  const setFilter=(setter:(value:string)=>void,value:string)=>{setter(value);setPage(0);};
  const pages=useMemo(()=>Array.from({length:pageCount},(_,index)=>index),[pageCount]);
  return <main className="catalog"><header className="app-topbar"><Link className="brand" href="/"><BrandMark />CodeAgent OJ</Link><SiteNav /><Link className="top-login" href="/workspace">{t("nav.workspace")}</Link></header><section className="catalog-heading"><div><p className="eyebrow">PROBLEM CATALOG</p><h1>{tp("problems.title")}</h1><p>{tp("problems.subtitle")}</p></div></section><section className="catalog-filters"><label className="catalog-search"><Search size={16}/><input value={query} onChange={(e)=>setFilter(setQuery,e.target.value)} placeholder={tp("problems.search")} /></label><select value={difficulty} onChange={(e)=>setFilter(setDifficulty,e.target.value)}><option value="">{tp("problems.difficulty.all")}</option><option value="EASY">{tp("problems.difficulty.easy")}</option><option value="MEDIUM">{tp("problems.difficulty.medium")}</option><option value="HARD">{tp("problems.difficulty.hard")}</option></select><select value={tag} onChange={(e)=>setFilter(setTag,e.target.value)}><option value="">{tp("problems.tag.all")}</option>{tags.map((item)=><option key={item.slug} value={item.slug}>{item.name}</option>)}</select></section>{error?<p className="catalog-error">{error}</p>:<><section className={`problem-table ${loading?"is-loading":""}`}><div className="problem-table-head"><span>{tp("problems.table.problem")}</span><span>{tp("problems.table.difficulty")}</span><span>{tp("problems.table.tags")}</span><span /></div>{items.map((item)=><Link className="catalog-row" key={item.slug} href={`/problems/${item.slug}`}><strong>{item.title}{solved.has(item.slug)&&<i style={{marginLeft:8,padding:"1px 6px",background:"#eaf3ee",border:"1px solid #9fc4b1",color:"#277456",font:"700 10px Consolas,monospace",fontStyle:"normal",verticalAlign:"middle"}}>AC</i>}</strong><span className={`difficulty ${item.difficulty.toLowerCase()}`}>{tp(label[item.difficulty])}</span><span className="catalog-tags">{item.tags.map((t)=><i key={t.slug}>{t.name}</i>)}</span><button type="button" onClick={(event)=>{event.preventDefault();event.stopPropagation();void toggleFavorite(item.slug,item.favorite);}} aria-label={item.favorite?"取消收藏":"收藏"} title={item.favorite?"取消收藏":"收藏"} style={{border:0,background:"transparent",padding:0,cursor:"pointer",color:item.favorite?"#d37737":"#a2a49d"}}><Star size={16} fill={item.favorite?"currentColor":"none"}/></button></Link>)}{!loading&&!items.length&&<p className="empty-state">{tp("problems.empty")}</p>}</section><div className="catalog-pagination"><span>{tp("problems.pagination.showing")} {total===0?0:page*pageSize+1}-{Math.min((page+1)*pageSize,total)} {tp("problems.pagination.of")} {total} {tp("problems.pagination.unit")}</span><div className="pagination-controls"><button type="button" onClick={()=>setPage((value)=>Math.max(0,value-1))} disabled={page===0||loading}>{tp("problems.pagination.prev")}</button>{pages.map((index)=><button type="button" key={index} className={index===page?"current":""} onClick={()=>setPage(index)} disabled={loading}>{index+1}</button>)}<button type="button" onClick={()=>setPage((value)=>Math.min(pageCount-1,value+1))} disabled={page>=pageCount-1||loading}>{tp("problems.pagination.next")}</button></div></div></>}</main>;
}
