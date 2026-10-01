import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "CodeAgent OJ",
  description: "AI-assisted online judge",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="zh-CN"><body>{children}</body></html>;
}
