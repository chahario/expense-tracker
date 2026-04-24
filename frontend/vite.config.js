import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // In dev, proxy /expenses → backend so VITE_API_BASE_URL can stay empty
    proxy: {
      '/expenses': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      output: {
        // Stable chunk names for better CDN caching
        manualChunks: { react: ['react', 'react-dom'] },
      },
    },
  },
});
