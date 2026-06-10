<script lang="ts">
	import { page } from '$app/state';

	let { children } = $props();

	const tabs = [
		{ href: '/admin/products', label: 'Products' },
		{ href: '/admin/categories', label: 'Categories' },
		{ href: '/admin/brands', label: 'Brands' }
	];
</script>

<div class="admin">
	<header class="bar">
		<a class="brand" href="/">
			<span class="wordmark">Arsip<sup>№</sup></span>
			<span class="tag mono">admin</span>
		</a>
		<nav class="tabs mono">
			{#each tabs as t}
				<a href={t.href} class:active={page.url.pathname === t.href}>{t.label}</a>
			{/each}
		</nav>
		<a class="store mono" href="/">← storefront</a>
	</header>

	<main class="body">
		{@render children()}
	</main>
</div>

<style>
	.admin {
		min-height: 100vh;
	}
	.bar {
		position: sticky;
		top: 0;
		z-index: 50;
		display: grid;
		grid-template-columns: 1fr auto 1fr;
		align-items: center;
		gap: 1.5rem;
		padding: 0.9rem clamp(1rem, 4vw, 2.5rem);
		background: color-mix(in srgb, var(--paper) 90%, transparent);
		backdrop-filter: blur(8px);
		border-bottom: 1.5px solid var(--ink);
	}
	.brand {
		display: flex;
		align-items: baseline;
		gap: 0.5rem;
		text-decoration: none;
		color: var(--ink);
	}
	.wordmark {
		font-family: var(--font-display);
		font-weight: 900;
		font-size: 1.45rem;
		letter-spacing: -0.02em;
	}
	.wordmark sup {
		font-size: 0.5em;
		color: var(--accent);
	}
	.tag {
		font-size: 0.6rem;
		letter-spacing: 0.2em;
		text-transform: uppercase;
		color: var(--paper);
		background: var(--ink);
		padding: 0.1rem 0.4rem;
	}
	.tabs {
		display: flex;
		gap: 0.4rem;
	}
	.tabs a {
		text-decoration: none;
		color: var(--ink-2);
		font-size: 0.74rem;
		letter-spacing: 0.1em;
		text-transform: uppercase;
		padding: 0.4rem 0.8rem;
		border: 1.5px solid transparent;
	}
	.tabs a:hover {
		color: var(--ink);
	}
	.tabs a.active {
		color: var(--ink);
		border-color: var(--ink);
		font-weight: 600;
	}
	.store {
		justify-self: end;
		text-decoration: none;
		color: var(--ink-soft);
		font-size: 0.72rem;
		letter-spacing: 0.06em;
	}
	.store:hover {
		color: var(--accent);
	}
	.body {
		padding: clamp(1.5rem, 4vw, 3rem);
		max-width: 1200px;
		margin: 0 auto;
	}
	@media (max-width: 720px) {
		.bar {
			grid-template-columns: 1fr;
			justify-items: center;
			gap: 0.6rem;
		}
		.store {
			justify-self: center;
		}
	}
</style>
