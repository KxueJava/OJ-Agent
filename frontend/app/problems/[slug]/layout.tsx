import Link from "next/link";
import { Code2 } from "lucide-react";
import styles from "./solve-entry.module.css";

export default async function ProblemLayout({ children, params }: { children: React.ReactNode; params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  return <>{children}<Link href={`/solve/${slug}`} className={styles.entry}><Code2 size={16}/>进入工作台</Link></>;
}
