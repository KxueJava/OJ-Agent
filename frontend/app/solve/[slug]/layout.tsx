import { redirect } from "next/navigation";

export default async function SolveCompatibilityLayout({ children: _children, params }: { children: React.ReactNode; params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  redirect(`/workbench/${slug}`);
}
