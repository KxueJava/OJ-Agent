"use client";

import { PointerEvent, useEffect, useRef, useState } from "react";
import { usePathname } from "next/navigation";
import AgentPanel from "./agent-panel";
import styles from "./desktop-pet.module.css";

type Props = { problemVersion?: number; sourceCode?: string; verdict?: string };
type Position = { x: number; y: number };
const positionKey = "codeagent-oj:desktop-pet:position";
const margin = 16;

function clampPosition(position: Position): Position {
  return {
    x: Math.min(Math.max(margin, position.x), Math.max(margin, window.innerWidth - 92)),
    y: Math.min(Math.max(margin, position.y), Math.max(margin, window.innerHeight - 80)),
  };
}

function PetMark() {
  return <svg viewBox="0 0 104 64" aria-hidden="true"><path fill="#df7657" d="M16 8h72v48H16zM8 20h88v24H8zM0 24h104v16H0zM28 56h8v8h-8zM44 56h8v8h-8zM68 56h8v8h-8zM84 56h8v8h-8z"/><path fill="#202936" d="M34 24h8v12h-8zM62 24h8v12h-8z"/></svg>;
}

export default function DesktopPet({ problemVersion, sourceCode = "", verdict }: Props) {
  const [open, setOpen] = useState(false);
  const [hidden, setHidden] = useState(false);
  const [activeVersion, setActiveVersion] = useState(problemVersion ?? 0);
  const [activeSource, setActiveSource] = useState(sourceCode);
  const [position, setPosition] = useState<Position | null>(null);
  const drag = useRef<{ offsetX: number; offsetY: number; moved: boolean } | null>(null);
  const suppressClick = useRef(false);
  const pathname = usePathname();

  useEffect(() => {
    if (problemVersion) {
      setActiveVersion(problemVersion);
      setActiveSource(sourceCode);
      return;
    }
    const match = pathname.match(/^\/workbench\/([^/]+)/);
    if (!match) {
      setActiveVersion(0);
      setActiveSource("");
      return;
    }
    const slug = decodeURIComponent(match[1]);
    const base = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
    fetch(`${base}/api/workspace/problems/${slug}`)
      .then((response) => response.ok ? response.json() : null)
      .then((payload) => {
        const problem = payload?.data;
        if (!problem) return;
        setActiveVersion(problem.problemVersionId ?? 0);
        setActiveSource(window.localStorage.getItem(`codeagent-oj:draft:${slug}:JAVA_21`) ?? problem.javaTemplate ?? "");
      })
      .catch(() => setActiveVersion(0));
  }, [pathname, problemVersion, sourceCode]);

  useEffect(() => {
    setHidden(window.localStorage.getItem("codeagent-oj:desktop-pet:hidden") === "1");
    const saved = window.localStorage.getItem(positionKey);
    try {
      setPosition(clampPosition(saved ? JSON.parse(saved) as Position : { x: window.innerWidth - 82, y: window.innerHeight - 82 }));
    } catch {
      setPosition({ x: window.innerWidth - 82, y: window.innerHeight - 82 });
    }
    const onResize = () => setPosition((current) => current ? clampPosition(current) : current);
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  function hide() {
    setHidden(true);
    window.localStorage.setItem("codeagent-oj:desktop-pet:hidden", "1");
  }

  function show() {
    setHidden(false);
    window.localStorage.removeItem("codeagent-oj:desktop-pet:hidden");
  }

  function startDrag(event: PointerEvent<HTMLButtonElement>) {
    if (!position) return;
    event.currentTarget.setPointerCapture(event.pointerId);
    drag.current = { offsetX: event.clientX - position.x, offsetY: event.clientY - position.y, moved: false };
  }

  function moveDrag(event: PointerEvent<HTMLButtonElement>) {
    if (!drag.current) return;
    const next = clampPosition({ x: event.clientX - drag.current.offsetX, y: event.clientY - drag.current.offsetY });
    if (Math.abs(next.x - position!.x) > 3 || Math.abs(next.y - position!.y) > 3) drag.current.moved = true;
    setPosition(next);
  }

  function endDrag(event: PointerEvent<HTMLButtonElement>) {
    const current = drag.current;
    if (!current || !position) return;
    const snapped = { ...clampPosition(position), x: position.x + 32 < window.innerWidth / 2 ? margin : Math.max(margin, window.innerWidth - 82) };
    setPosition(snapped);
    window.localStorage.setItem(positionKey, JSON.stringify(snapped));
    drag.current = null;
    suppressClick.current = current.moved;
    if (current.moved) event.preventDefault();
  }

  if (hidden) return <button className={styles.show} onClick={show}>显示学习伙伴</button>;

  return <div className={styles.root} style={position ? { left: position.x, top: position.y } : undefined}>
    {open && <div className={styles.drawer}>{activeVersion > 0 ? <AgentPanel problemVersion={activeVersion} sourceCode={activeSource} verdict={verdict} /> : <div className={styles.contextEmpty}>进入题目工作台后，可以使用 Agent 学习助手。</div>}</div>}
    <button className={styles.pet} onPointerDown={startDrag} onPointerMove={moveDrag} onPointerUp={endDrag} onPointerCancel={endDrag} onClick={() => { if (suppressClick.current) { suppressClick.current = false; return; } setOpen((value) => !value); }} aria-label="打开 Agent 助手" aria-expanded={open} title={open ? "收回学习伙伴" : "打开学习伙伴"}>
      <span className={styles.pulse} />
      <PetMark />
    </button>
    <button className={styles.hide} onClick={hide} aria-label="隐藏学习伙伴" title="隐藏学习伙伴">隐藏</button>
  </div>;
}
