"use client";

import { Fragment, ReactNode } from "react";

/**
 * 极简 Markdown 渲染器（不引第三方依赖，离线也能构建）。
 *
 * 为什么不用 react-markdown：本项目要能在无外网环境构建，而模型输出里最常见的就是
 * 标题 / 代码块 / 行内代码 / 粗体 / 列表 / 引用 / 链接 —— 这些用一个小解析器即可，
 * 且能完全控制样式（此前 CSS Module 类名失效过一次，所以这里全部用内联样式）。
 *
 * 支持：``` 围栏代码块、# 标题、- / * / 1. 列表、> 引用、--- 分隔线、
 *       **粗体**、*斜体*、`行内代码`、[文字](链接)。
 */
const mono = "Consolas,'SF Mono',Menlo,monospace";

function inline(source: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  const pattern = /(`[^`]+`|\*\*[^*]+\*\*|\*[^*\n]+\*|\[[^\]]+\]\([^)\s]+\))/g;
  let last = 0;
  let index = 0;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(source)) !== null) {
    if (match.index > last) nodes.push(source.slice(last, match.index));
    const token = match[0];
    const key = `i${index++}`;
    if (token.startsWith("`")) {
      nodes.push(<code key={key} style={{ padding: "1px 5px", background: "#ebe9e2", color: "#8a4a1e", font: `12px ${mono}`, borderRadius: 3 }}>{token.slice(1, -1)}</code>);
    } else if (token.startsWith("**")) {
      nodes.push(<strong key={key} style={{ fontWeight: 700, color: "#252a2d" }}>{token.slice(2, -2)}</strong>);
    } else if (token.startsWith("[")) {
      const link = /\[([^\]]+)\]\(([^)\s]+)\)/.exec(token);
      nodes.push(<a key={key} href={link?.[2] ?? "#"} target="_blank" rel="noreferrer" style={{ color: "#d37737", textDecoration: "underline" }}>{link?.[1] ?? token}</a>);
    } else {
      nodes.push(<em key={key} style={{ fontStyle: "italic" }}>{token.slice(1, -1)}</em>);
    }
    last = match.index + token.length;
  }
  if (last < source.length) nodes.push(source.slice(last));
  return nodes;
}

export default function Markdown({ text }: { text: string }) {
  const lines = (text ?? "").replace(/\r\n/g, "\n").split("\n");
  const blocks: ReactNode[] = [];
  let paragraph: string[] = [];
  let list: string[] = [];
  let ordered = false;
  let index = 0;

  const flushParagraph = () => {
    if (paragraph.length === 0) return;
    const body = paragraph.join(" ");
    blocks.push(<p key={`p${index++}`} style={{ margin: "0 0 10px" }}>{inline(body)}</p>);
    paragraph = [];
  };
  const flushList = () => {
    if (list.length === 0) return;
    const items = list.map((item, position) => <li key={position} style={{ margin: "0 0 4px" }}>{inline(item)}</li>);
    blocks.push(ordered
      ? <ol key={`l${index++}`} style={{ margin: "0 0 10px", paddingLeft: 22 }}>{items}</ol>
      : <ul key={`l${index++}`} style={{ margin: "0 0 10px", paddingLeft: 20, listStyle: "disc" }}>{items}</ul>);
    list = [];
  };

  for (let cursor = 0; cursor < lines.length; cursor += 1) {
    const line = lines[cursor];
    const fence = /^\s*```/.exec(line);
    if (fence) {
      flushParagraph(); flushList();
      const language = line.trim().replace(/^```/, "").trim();
      const body: string[] = [];
      cursor += 1;
      while (cursor < lines.length && !/^\s*```/.test(lines[cursor])) { body.push(lines[cursor]); cursor += 1; }
      blocks.push(
        <div key={`c${index++}`} style={{ margin: "0 0 12px" }}>
          {language && <div style={{ padding: "5px 12px", background: "#1f2426", color: "#9aa19b", font: `10px ${mono}`, letterSpacing: ".08em", textTransform: "uppercase", borderBottom: "1px solid #343b3d" }}>{language}</div>}
          <pre style={{ margin: 0, padding: "12px 14px", background: "#252a2d", color: "#e8e6df", font: `12px/1.65 ${mono}`, overflowX: "auto", whiteSpace: "pre" }}>
            <code>{body.join("\n")}</code>
          </pre>
        </div>,
      );
      continue;
    }

    const heading = /^(#{1,6})\s+(.*)$/.exec(line);
    if (heading) {
      flushParagraph(); flushList();
      const level = heading[1].length;
      const size = level === 1 ? 17 : level === 2 ? 15 : 13.5;
      blocks.push(
        <p key={`h${index++}`} style={{ margin: level <= 2 ? "14px 0 7px" : "11px 0 5px", fontSize: size, fontWeight: 700, color: "#252a2d", letterSpacing: "-.01em" }}>
          {inline(heading[2])}
        </p>,
      );
      continue;
    }

    if (/^\s*(-{3,}|\*{3,})\s*$/.test(line)) {
      flushParagraph(); flushList();
      blocks.push(<hr key={`r${index++}`} style={{ margin: "12px 0", border: 0, borderTop: "1px solid #ded9cf" }} />);
      continue;
    }

    const bullet = /^\s*[-*+]\s+(.*)$/.exec(line);
    const numbered = /^\s*\d+[.)]\s+(.*)$/.exec(line);
    if (bullet || numbered) {
      flushParagraph();
      if (list.length > 0 && ordered !== Boolean(numbered)) flushList();
      ordered = Boolean(numbered);
      list.push((bullet ?? numbered)![1]);
      continue;
    }

    const quote = /^\s*>\s?(.*)$/.exec(line);
    if (quote) {
      flushParagraph(); flushList();
      blocks.push(
        <p key={`q${index++}`} style={{ margin: "0 0 10px", padding: "7px 12px", borderLeft: "3px solid #d37737", background: "#f7efe2", color: "#6b5a45" }}>
          {inline(quote[1])}
        </p>,
      );
      continue;
    }

    if (line.trim() === "") { flushParagraph(); flushList(); continue; }
    paragraph.push(line.trim());
  }
  flushParagraph(); flushList();

  return <div style={{ fontSize: 13, lineHeight: 1.7, color: "#3f4540", wordBreak: "break-word" }}>{blocks.map((block, position) => <Fragment key={position}>{block}</Fragment>)}</div>;
}
