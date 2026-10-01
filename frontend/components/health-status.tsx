"use client";

import { useEffect, useState } from "react";

type HealthState = "checking" | "online" | "offline";

export function HealthStatus() {
  const [state, setState] = useState<HealthState>("checking");

  useEffect(() => {
    const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
    fetch(`${baseUrl}/api/health`)
      .then((response) => {
        if (!response.ok) throw new Error("Health request failed");
        setState("online");
      })
      .catch(() => setState("offline"));
  }, []);

  const labels = { checking: "后端检查中", online: "后端已连接", offline: "后端未连接" };
  return <span className={`health health--${state}`}><span />{labels[state]}</span>;
}
