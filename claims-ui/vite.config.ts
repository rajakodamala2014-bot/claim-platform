/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In dev (`npm run dev`, http://localhost:5173) API calls are proxied to the gateway, so the browser
// sees one origin exactly as it does when the gateway serves the built app on http://localhost:8000.
const gateway = process.env.GATEWAY_URL ?? 'http://localhost:8000';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/claimant': { target: gateway, changeOrigin: true },
      '/dev/token': { target: gateway, changeOrigin: true },
    },
  },
  test: {
    environment: 'node',
  },
});
