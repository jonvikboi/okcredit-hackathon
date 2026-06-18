import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

export default defineConfig({
	plugins: [sveltekit()],
	server: {
		proxy: {
			'/ws-bullion': {
				target: 'ws://ambicaaspot.com:1001',
				ws: true,
				changeOrigin: true,
				rewrite: (path) => path.replace(/^\/ws-bullion/, '/bullion')
			}
		}
	}
});
