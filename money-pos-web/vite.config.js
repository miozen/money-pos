import { defineConfig } from "vite";
import { resolve } from 'path';
import { readFileSync } from 'node:fs';
import vue from "@vitejs/plugin-vue";
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { createSvgIconsPlugin } from 'vite-plugin-svg-icons'

const releaseVersion = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8')).version;

export default defineConfig(async ({mode}) => ({
  // 🌟 核心修复：前端页面基础路径改为相对路径 './'
  // 这样打包后 Electron 读本地 index.html 时，资源路径才会是 ./assets/... 从而精准找到 CSS/JS
  base: mode  === 'demo' ? '/money-pos-demo' : './',
  envPrefix: ["VITE_"],
  define: {
    __MONEY_POS_VERSION__: JSON.stringify(releaseVersion),
  },
  server: {
    port: 1520,
    strictPort: true,
    // 🌟 架构规范：开发环境的代理保持不变
    proxy: {
      '/api': {
        target: 'http://localhost:9101',
        changeOrigin: true,
        // 将前端发出的 /api/xxx 请求，在底层静默重写为后端的真实路径 /money-pos/xxx
        rewrite: (path) => path.replace(/^\/api/, '/money-pos')
      }
    }
  },

  plugins: [
    vue(),
    createSvgIconsPlugin({
      iconDirs: [resolve(__dirname, 'src/assets/icons')],
      symbolId: 'icon-[dir]-[name]'
    }),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],

  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },

  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: process.env.NODE_ENV === 'development',
    minify: 'esbuild',
  },
}))
