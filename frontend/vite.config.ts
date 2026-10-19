import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Em desenvolvimento, as chamadas para /api são repassadas ao back-end (porta 8080).
// Assim o navegador só fala com o mesmo endereço do front e não há problema de CORS.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
});
