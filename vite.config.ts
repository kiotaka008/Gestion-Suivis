/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  // Adresse du backend Spring Boot (port 3000 par défaut, comme backend/.env.example).
  const backend = env.VITE_BACKEND_URL || 'http://localhost:3000';

  return {
    plugins: [react(), tailwindcss()],
    server: {
      port: 5173,
      proxy: {
        // Même origine pour le navigateur : cookie de session et jeton CSRF fonctionnent sans CORS.
        '/api': {
          target: backend,
          changeOrigin: true,
        },
      },
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: './src/tests/setup.ts',
    },
  };
});
