import adapter from '@sveltejs/adapter-auto';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

export default defineConfig({
	plugins: [
		sveltekit({
			compilerOptions: {
				// Force runes mode for the project, except for libraries. Can be removed in svelte 6.
				runes: ({ filename }) =>
					filename.split(/[/\\]/).includes('node_modules') ? undefined : true
			},

			// adapter-auto only supports some environments, see https://svelte.dev/docs/kit/adapter-auto for a list.
			// If your environment is not supported, or you settled on a specific environment, switch out the adapter.
			// See https://svelte.dev/docs/kit/adapters for more information about adapters.
			adapter: adapter()
		})
	],
	server: {
		// Routing dev (mirror nginx di produksi):
		//   /api/products/_search -> product-search (OpenSearch)
		//   /api/*                -> product-backend (PostgreSQL)
		proxy: {
			'/api/products/_search': {
				target: process.env.SEARCH_URL ?? 'http://localhost:8081',
				changeOrigin: true
			},
			'/api': {
				target: process.env.BACKEND_URL ?? 'http://localhost:8080',
				changeOrigin: true
			}
		}
	}
});
