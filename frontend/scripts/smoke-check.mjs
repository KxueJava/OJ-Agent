const base = process.env.API_BASE_URL ?? "http://localhost:8080";

async function json(path) {
  const response = await fetch(`${base}${path}`);
  if (!response.ok) throw new Error(`${path} returned ${response.status}`);
  return response.json();
}

const health = await json("/api/health");
if (health.data?.status !== "UP") throw new Error("health is not UP");
const first = await json("/api/problems?page=0&size=10");
const second = await json("/api/problems?page=1&size=10");
if (first.data.items.length !== 10 || second.data.items.length === 0) throw new Error("catalog pagination is incomplete");
if (first.data.total < 20) throw new Error(`expected at least 20 problems, got ${first.data.total}`);
const detail = await json("/api/workspace/problems/two-sum");
if (!detail.data.problemVersionId || !detail.data.examples?.length) throw new Error("workspace detail is incomplete");
console.log(`smoke ok: ${first.data.total} problems, pagination and workspace detail are available`);
