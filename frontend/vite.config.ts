import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Relative base so the same build works on GitHub Pages (/request-desk/) and in Docker (/).
export default defineConfig({
  plugins: [react()],
  base: './',
})
