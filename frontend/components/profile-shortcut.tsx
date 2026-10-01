"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { useAuth } from "../lib/auth";
import styles from "./profile-shortcut.module.css";

export default function ProfileShortcut() {
  const pathname = usePathname();
  const { user, accessToken } = useAuth();
  const [profile, setProfile] = useState<{ displayName: string; avatarUrl?: string } | null>(null);
  const base = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

  useEffect(() => {
    if (!accessToken) return;
    fetch(`${base}/api/profile`, { headers: { Authorization: `Bearer ${accessToken}` } })
      .then((response) => response.ok ? response.json() : null)
      .then((payload) => setProfile(payload?.data ?? null))
      .catch(() => setProfile(null));
  }, [accessToken, base]);

  if (pathname !== "/" || !user?.id) return null;
  const displayName = profile?.displayName || user.displayName;
  const initial = Array.from(displayName.trim())[0]?.toUpperCase() || "U";

  return <Link className={styles.link} href="/profile" aria-label="打开个人资料" title="个人资料">
    <span className={styles.avatar}>{profile?.avatarUrl ? <img src={`${base}${profile.avatarUrl}`} alt="" /> : initial}</span>
    <span className={styles.copy}>{displayName}<small>@{user.username}</small></span>
  </Link>;
}
