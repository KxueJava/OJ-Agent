import { redirect } from "next/navigation";

/** The Phase 1 placeholder route remains a compatibility target for existing auth redirects. */
export default function WorkspaceLayout({ children: _children }: { children: React.ReactNode }) {
  redirect("/");
}
