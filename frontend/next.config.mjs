/** @type {import('next').NextConfig} */
const nextConfig = {
  // 关掉左下角的 Next.js 开发工具指示器（深色 "N" 圆球）。
  // 它只在 `next dev` 下渲染、不属于本项目的组件，会压住页面右下角「API 在线」那一行；生产构建本来就没有。
  devIndicators: false,
};

export default nextConfig;
