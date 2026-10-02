"use client";

import { useCallback, useEffect, useState } from "react";
import { publicApiRequest } from "../lib/auth";

type Item = { slug: string; title: string; difficulty: string; tags?: { slug: string; name: string }[] };

type Labels = {
  trigger: string; search: string; empty: string; loading: string; end: string;
  confirm: (count: number) => string; already: string; selected: (count: number) => string; close: string;
};

const PAGE_SIZE = 10;
const difficultyText: Record<string, string> = { EASY: "简单", MEDIUM: "中等", HARD: "困难" };

/**
 * 题库多选 select：原生 <select multiple size=10> + 滚动到底自动加载下一页。
 *
 * 刻意全部使用**内联样式**而不是 CSS Module：上一版用 Module 时类名没生效，
 * 行元素退化成内联布局导致版面混乱；原生 select 的盒子模型由浏览器保证，最稳。
 * 多选靠原生行为（Windows/Linux 用 Ctrl+点击，macOS 用 ⌘+点击），旁边有文字提示。
 */
export default function ProblemPicker({ addedSlugs, disabled, labels, onConfirm }: {
  addedSlugs: string[];
  disabled?: boolean;
  labels: Labels;
  onConfirm: (slugs: string[]) => Promise<void>;
}) {
  const [query, setQuery] = useState("");
  const [items, setItems] = useState<Item[]>([]);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async (nextPage: number, replace: boolean) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(nextPage), size: String(PAGE_SIZE) });
      if (query.trim()) params.set("query", query.trim());
      const data = await publicApiRequest<{ items: Item[]; total: number }>(`/api/problems?${params.toString()}`);
      setTotal(data.total ?? 0);
      setItems((current) => (replace ? data.items ?? [] : [...current, ...(data.items ?? [])]));
      setPage(nextPage);
    } catch {
      if (replace) { setItems([]); setTotal(0); }
    } finally {
      setLoading(false);
    }
  }, [query]);

  useEffect(() => { void load(0, true); }, [load]);

  const hasMore = items.length < total;

  const onScroll = (event: React.UIEvent<HTMLSelectElement>) => {
    const node = event.currentTarget;
    if (loading || !hasMore) return;
    if (node.scrollTop + node.clientHeight >= node.scrollHeight - 24) void load(page + 1, false);
  };

  const confirm = async () => {
    if (selected.length === 0) return;
    setBusy(true);
    try { await onConfirm(selected); setSelected([]); }
    finally { setBusy(false); }
  };

  return (
    <div style={{ display: "grid", gap: 8, marginTop: 10, maxWidth: 460 }}>
      <input
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder={labels.search}
        style={{ height: 32, padding: "0 10px", border: "1px solid #c9c7bf", background: "#fff", color: "#252a2d", font: "12px inherit" }}
      />

      <select
        multiple
        size={10}
        value={selected}
        disabled={disabled}
        onChange={(event) => setSelected(Array.from(event.target.selectedOptions).map((option) => option.value).filter((slug) => !addedSlugs.includes(slug)))}
        onScroll={onScroll}
        style={{ width: "100%", border: "1px solid #c9c7bf", background: "#fff", color: "#252a2d", font: "12px/1.5 Consolas,monospace", padding: 4 }}
      >
        {items.map((item) => {
          const added = addedSlugs.includes(item.slug);
          return (
            <option key={item.slug} value={item.slug} disabled={added} style={{ padding: "2px 4px", color: added ? "#a9a7a0" : "#252a2d" }}>
              {item.title}（{item.slug} · {difficultyText[item.difficulty] ?? item.difficulty}）{added ? ` · ${labels.already}` : ""}
            </option>
          );
        })}
      </select>

      <div style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: 10 }}>
        <button
          type="button"
          onClick={() => void confirm()}
          disabled={busy || selected.length === 0}
          style={{
            height: 32, padding: "0 14px", border: "1px solid " + (busy || selected.length === 0 ? "#d8d5ce" : "#d37737"),
            color: busy || selected.length === 0 ? "#a9a7a0" : "#fff",
            background: busy || selected.length === 0 ? "transparent" : "#d37737",
            font: "700 12px inherit", cursor: busy || selected.length === 0 ? "default" : "pointer",
          }}
        >
          {labels.confirm(selected.length)}
        </button>
        <span style={{ color: "#858981", font: "10px Consolas,monospace" }}>
          {loading ? labels.loading : `${items.length} / ${total}${hasMore ? "" : " · " + labels.end}`}
        </span>
        <span style={{ color: "#858981", fontSize: 11 }}>{labels.close}</span>
      </div>
    </div>
  );
}
