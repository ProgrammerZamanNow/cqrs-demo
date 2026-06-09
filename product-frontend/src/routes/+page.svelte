<script lang="ts">
	type Ref = { id: string; name: string };
	type Product = {
		id: string;
		sku: string;
		name: string;
		description: string;
		price: number;
		stock: number;
		imageUrl: string;
		category: Ref;
		brand: Ref;
	};
	type FacetItem = { id: string; name: string; count: number; selected: boolean };
	type PriceRange = { min: number; max: number | null; label: string; count: number };
	type Avail = { value: string; count: number };
	type Facets = {
		categories: FacetItem[];
		brands: FacetItem[];
		priceRanges: PriceRange[];
		availability: Avail[];
	};

	const SIZE = 60;
	const SORTS = [
		{ v: 'name,asc', l: 'Nama A–Z' },
		{ v: 'name,desc', l: 'Nama Z–A' },
		{ v: 'price,asc', l: 'Harga ↑' },
		{ v: 'price,desc', l: 'Harga ↓' },
		{ v: 'stock,desc', l: 'Stok terbanyak' }
	];

	// input vs applied keyword (search hanya jalan saat submit)
	let keyword = $state('');
	let appliedKeyword = $state('');

	let selectedCategories = $state<Ref[]>([]);
	let selectedBrands = $state<Ref[]>([]);
	let availability = $state<string | null>(null);
	let priceMin = $state<number | null>(null);
	let priceMax = $state<number | null>(null);
	let sort = $state('name,asc');
	let page = $state(0);

	let products = $state<Product[]>([]);
	let facets = $state<Facets | null>(null);
	let total = $state(0);
	let totalPages = $state(0);
	let took = $state(0);
	let loading = $state(true);
	let expanded = $state<{ categories: boolean; brands: boolean }>({
		categories: false,
		brands: false
	});

	let reqId = 0;

	async function load() {
		const my = ++reqId;
		loading = true;
		const p = new URLSearchParams();
		if (appliedKeyword.trim()) p.set('keyword', appliedKeyword.trim());
		for (const c of selectedCategories) p.append('categoryId', c.id);
		for (const b of selectedBrands) p.append('brandId', b.id);
		if (availability) p.set('availability', availability);
		if (priceMin != null) p.set('minPrice', String(priceMin));
		if (priceMax != null) p.set('maxPrice', String(priceMax));
		p.set('sort', sort);
		p.set('page', String(page));
		p.set('size', String(SIZE));

		try {
			const res = await fetch('/api/products?' + p.toString());
			const json = await res.json();
			if (my !== reqId) return;
			products = json.data ?? [];
			facets = json.facets ?? null;
			total = json.paging?.totalElement ?? 0;
			totalPages = json.paging?.totalPage ?? 0;
			took = json.metadata?.processTimeMs ?? 0;
		} catch {
			if (my !== reqId) return;
			products = [];
			facets = null;
			total = 0;
			totalPages = 0;
		} finally {
			if (my === reqId) loading = false;
		}
	}

	$effect(() => {
		// dependensi yang memicu reload
		void [
			appliedKeyword,
			selectedCategories,
			selectedBrands,
			availability,
			priceMin,
			priceMax,
			sort,
			page
		];
		load();
	});

	function applySearch(e: Event) {
		e.preventDefault();
		appliedKeyword = keyword.trim();
		page = 0;
	}
	function clearKeyword() {
		keyword = '';
		appliedKeyword = '';
		page = 0;
	}
	function toggleCategory(item: Ref) {
		selectedCategories = selectedCategories.some((c) => c.id === item.id)
			? selectedCategories.filter((c) => c.id !== item.id)
			: [...selectedCategories, { id: item.id, name: item.name }];
		page = 0;
	}
	function toggleBrand(item: Ref) {
		selectedBrands = selectedBrands.some((b) => b.id === item.id)
			? selectedBrands.filter((b) => b.id !== item.id)
			: [...selectedBrands, { id: item.id, name: item.name }];
		page = 0;
	}
	function selectPrice(min: number, max: number | null) {
		if (priceMin === min && priceMax === max) {
			priceMin = null;
			priceMax = null;
		} else {
			priceMin = min;
			priceMax = max;
		}
		page = 0;
	}
	function toggleAvailability(v: string) {
		availability = availability === v ? null : v;
		page = 0;
	}
	function clearAll() {
		keyword = '';
		appliedKeyword = '';
		selectedCategories = [];
		selectedBrands = [];
		availability = null;
		priceMin = null;
		priceMax = null;
		page = 0;
	}

	let hasFilters = $derived(
		appliedKeyword !== '' ||
			selectedCategories.length > 0 ||
			selectedBrands.length > 0 ||
			availability !== null ||
			priceMin != null ||
			priceMax != null
	);

	const fmt = (n: number) => n.toLocaleString('id-ID');
	const idr = (n: number) =>
		new Intl.NumberFormat('id-ID', {
			style: 'currency',
			currency: 'IDR',
			maximumFractionDigits: 0
		}).format(n);
	const availLabel = (v: string) => (v === 'IN_STOCK' ? 'Tersedia' : 'Stok habis');

	const SWATCHES = [
		'#e3d6bd',
		'#dcc6ad',
		'#cdd2bf',
		'#e1ccb4',
		'#d6c6cf',
		'#c7d0d2',
		'#e6d2bd',
		'#cfcbb8'
	];
	function swatch(s: string) {
		let h = 0;
		for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0;
		return SWATCHES[h % SWATCHES.length];
	}
	const fromIdx = (pg: number, i: number) => pg * SIZE + i + 1;
</script>

<svelte:head>
	<title>Arsip — Katalog Produk</title>
</svelte:head>

{#if loading}
	<div class="progress" aria-hidden="true"></div>
{/if}

<div class="ticker mono" aria-hidden="true">
	<span>KATALOG PRODUK</span><span class="dot">✦</span>
	<span>{fmt(total)} ITEM TERINDEKS</span><span class="dot">✦</span>
	<span>PENCARIAN LANGSUNG</span><span class="dot">✦</span>
	<span>POSTGRESQL · 100K DATA</span><span class="dot">✦</span>
	<span>FACET DRILL-DOWN</span>
</div>

<header class="masthead">
	<div class="brand">
		<span class="wordmark">Arsip<sup>№</sup></span>
		<span class="brand-sub mono">katalog&nbsp;produk</span>
	</div>

	<form class="search" onsubmit={applySearch}>
		<svg class="search-ico" viewBox="0 0 24 24" aria-hidden="true">
			<circle cx="11" cy="11" r="7" />
			<line x1="21" y1="21" x2="16.5" y2="16.5" />
		</svg>
		<input
			type="search"
			bind:value={keyword}
			placeholder="Cari produk…"
			aria-label="Cari produk"
			autocomplete="off"
		/>
		{#if keyword}
			<button type="button" class="search-clear" onclick={clearKeyword} aria-label="Hapus">✕</button>
		{/if}
		<button type="submit" class="search-go mono">Cari</button>
	</form>

	<div class="masthead-meta mono">
		<span class="took"><i class:live={!loading}></i>{took}ms</span>
	</div>
</header>

<section class="hero">
	<h1>Temukan <em>apa&nbsp;pun</em><span class="hero-mark">.</span></h1>
	<p class="hero-sub mono">
		{fmt(total)} produk terindeks — disaring &amp; difacet langsung dari PostgreSQL.
	</p>
</section>

<div class="shell">
	<!-- ============ FACETS ============ -->
	<aside class="facets">
		<div class="facets-head mono">
			<span>Saring</span>
			{#if hasFilters}
				<button class="reset" onclick={clearAll}>reset ✕</button>
			{/if}
		</div>

		{#snippet entityFacet(
			label: string,
			items: FacetItem[],
			selected: Ref[],
			toggle: (r: Ref) => void,
			key: 'categories' | 'brands'
		)}
			<section class="facet">
				<h2 class="facet-h mono">{label}</h2>
				{#if items.length === 0}
					<p class="facet-empty mono">tidak ada</p>
				{:else}
					{@const limit = expanded[key] ? items.length : 8}
					<ul class="facet-list">
						{#each items.slice(0, limit) as it (it.id)}
							{@const on = selected.some((s) => s.id === it.id)}
							<li>
								<button class="fitem" class:on aria-pressed={on} onclick={() => toggle(it)}>
									<span class="tick" aria-hidden="true"></span>
									<span class="fname">{it.name}</span>
									<span class="fcount mono">{fmt(it.count)}</span>
								</button>
							</li>
						{/each}
					</ul>
					{#if items.length > 8}
						<button class="fmore mono" onclick={() => (expanded[key] = !expanded[key])}>
							{expanded[key] ? '− lebih sedikit' : `+ ${items.length - 8} lainnya`}
						</button>
					{/if}
				{/if}
			</section>
		{/snippet}

		{@render entityFacet(
			'Kategori',
			facets?.categories ?? [],
			selectedCategories,
			toggleCategory,
			'categories'
		)}
		{@render entityFacet('Brand', facets?.brands ?? [], selectedBrands, toggleBrand, 'brands')}

		<section class="facet">
			<h2 class="facet-h mono">Harga</h2>
			<ul class="facet-list">
				{#each facets?.priceRanges ?? [] as pr}
					{@const on = priceMin === pr.min && priceMax === pr.max}
					<li>
						<button
							class="fitem"
							class:on
							aria-pressed={on}
							onclick={() => selectPrice(pr.min, pr.max)}
						>
							<span class="tick" aria-hidden="true"></span>
							<span class="fname">{pr.label}</span>
							<span class="fcount mono">{fmt(pr.count)}</span>
						</button>
					</li>
				{/each}
			</ul>
		</section>

		<section class="facet">
			<h2 class="facet-h mono">Ketersediaan</h2>
			<ul class="facet-list">
				{#each facets?.availability ?? [] as a}
					{@const on = availability === a.value}
					<li>
						<button
							class="fitem"
							class:on
							aria-pressed={on}
							onclick={() => toggleAvailability(a.value)}
						>
							<span class="tick" aria-hidden="true"></span>
							<span class="fname">{availLabel(a.value)}</span>
							<span class="fcount mono">{fmt(a.count)}</span>
						</button>
					</li>
				{/each}
			</ul>
		</section>
	</aside>

	<!-- ============ RESULTS ============ -->
	<section class="results">
		<div class="results-bar">
			<div class="rcount">
				<strong>{fmt(total)}</strong>
				<span class="mono">produk{hasFilters ? ' · tersaring' : ''}</span>
			</div>
			<label class="sortbox mono">
				urut
				<select bind:value={sort}>
					{#each SORTS as s}
						<option value={s.v}>{s.l}</option>
					{/each}
				</select>
			</label>
		</div>

		{#if hasFilters}
			<div class="chips">
				{#if appliedKeyword}
					<button class="chip" onclick={clearKeyword}>“{appliedKeyword}” <i>✕</i></button>
				{/if}
				{#each selectedCategories as c}
					<button class="chip" onclick={() => toggleCategory(c)}>{c.name} <i>✕</i></button>
				{/each}
				{#each selectedBrands as b}
					<button class="chip" onclick={() => toggleBrand(b)}>{b.name} <i>✕</i></button>
				{/each}
				{#if priceMin != null || priceMax != null}
					<button class="chip" onclick={() => selectPrice(priceMin ?? 0, priceMax)}>
						{priceMin != null ? idr(priceMin) : '0'}–{priceMax != null ? idr(priceMax) : '∞'}
						<i>✕</i>
					</button>
				{/if}
				{#if availability}
					<button class="chip" onclick={() => toggleAvailability(availability!)}>
						{availLabel(availability)} <i>✕</i>
					</button>
				{/if}
			</div>
		{/if}

		{#if products.length === 0 && !loading}
			<div class="empty">
				<p class="empty-big">Nihil.</p>
				<p class="mono">Tidak ada produk untuk saringan ini.</p>
				{#if hasFilters}
					<button class="empty-reset mono" onclick={clearAll}>reset saringan</button>
				{/if}
			</div>
		{:else}
			<div class="grid" class:dim={loading}>
				{#each products as p, i (p.id)}
					<article class="card" style="--i:{i}">
						<div class="thumb" style="background:{swatch(p.brand.name)}">
							<span class="thumb-idx mono">{String(fromIdx(page, i)).padStart(3, '0')}</span>
							<span class="thumb-letter">{p.name.charAt(0)}</span>
							<span class="thumb-sku mono">{p.sku}</span>
							<span class="thumb-stock" class:out={p.stock === 0}>
								{p.stock === 0 ? 'habis' : `${p.stock} stok`}
							</span>
						</div>
						<div class="card-body">
							<div class="eyebrow mono">{p.brand.name}<span>·</span>{p.category.name}</div>
							<h3 class="pname">{p.name}</h3>
							<div class="price-row">
								<span class="price mono">{idr(p.price)}</span>
								<span class="dotmark" aria-hidden="true"></span>
							</div>
						</div>
					</article>
				{/each}
			</div>

			{#if totalPages > 1}
				<nav class="pager mono">
					<button onclick={() => (page = Math.max(0, page - 1))} disabled={page <= 0}>
						← sebelumnya
					</button>
					<span class="pager-pos">hal. {page + 1} / {fmt(totalPages)}</span>
					<button
						onclick={() => (page = Math.min(totalPages - 1, page + 1))}
						disabled={page >= totalPages - 1}
					>
						berikutnya →
					</button>
				</nav>
			{/if}
		{/if}
	</section>
</div>

<footer class="foot mono">
	<span>Arsip № — demo facet search</span>
	<span>SvelteKit · Spring Boot · PostgreSQL</span>
</footer>

<style>
	/* ---------- progress / loading ---------- */
	.progress {
		position: fixed;
		inset: 0 0 auto 0;
		height: 2px;
		z-index: 10000;
		background: linear-gradient(90deg, transparent, var(--accent), transparent);
		background-size: 40% 100%;
		animation: slide 0.9s linear infinite;
	}
	@keyframes slide {
		from {
			background-position: -40% 0;
		}
		to {
			background-position: 140% 0;
		}
	}

	/* ---------- ticker ---------- */
	.ticker {
		display: flex;
		gap: 1.5rem;
		align-items: center;
		overflow: hidden;
		white-space: nowrap;
		font-size: 0.66rem;
		letter-spacing: 0.18em;
		color: var(--paper);
		background: var(--ink);
		padding: 0.45rem 0;
	}
	.ticker .dot {
		color: var(--accent);
	}
	.ticker > span {
		flex: none;
	}

	/* ---------- masthead ---------- */
	.masthead {
		position: sticky;
		top: 0;
		z-index: 50;
		display: grid;
		grid-template-columns: 1fr minmax(280px, 540px) 1fr;
		align-items: center;
		gap: 1.5rem;
		padding: 1rem clamp(1rem, 4vw, 3rem);
		background: color-mix(in srgb, var(--paper) 88%, transparent);
		backdrop-filter: blur(8px);
		border-bottom: 1.5px solid var(--ink);
	}
	.brand {
		display: flex;
		align-items: baseline;
		gap: 0.6rem;
	}
	.wordmark {
		font-family: var(--font-display);
		font-weight: 900;
		font-size: 1.7rem;
		letter-spacing: -0.02em;
		line-height: 1;
	}
	.wordmark sup {
		font-size: 0.5em;
		color: var(--accent);
		margin-left: 0.1em;
	}
	.brand-sub {
		font-size: 0.66rem;
		letter-spacing: 0.16em;
		text-transform: uppercase;
		color: var(--ink-soft);
	}

	.search {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		background: var(--card);
		border: 1.5px solid var(--ink);
		padding: 0.35rem 0.4rem 0.35rem 0.8rem;
		box-shadow: 4px 4px 0 var(--ink);
	}
	.search-ico {
		width: 18px;
		height: 18px;
		flex: none;
		fill: none;
		stroke: var(--ink-soft);
		stroke-width: 2;
		stroke-linecap: round;
	}
	.search input {
		flex: 1;
		border: 0;
		background: transparent;
		font-family: var(--font-body);
		font-size: 1rem;
		color: var(--ink);
		padding: 0.25rem 0;
		min-width: 0;
	}
	.search input:focus {
		outline: none;
	}
	.search input::placeholder {
		color: var(--ink-soft);
	}
	.search-clear {
		border: 0;
		background: transparent;
		color: var(--ink-soft);
		cursor: pointer;
		font-size: 0.8rem;
		padding: 0 0.2rem;
	}
	.search-go {
		border: 0;
		background: var(--accent);
		color: var(--paper);
		font-size: 0.74rem;
		letter-spacing: 0.12em;
		text-transform: uppercase;
		padding: 0.5rem 0.95rem;
		cursor: pointer;
		transition: background 0.15s;
	}
	.search-go:hover {
		background: var(--accent-deep);
	}

	.masthead-meta {
		justify-self: end;
		font-size: 0.72rem;
		color: var(--ink-soft);
	}
	.took {
		display: inline-flex;
		align-items: center;
		gap: 0.4rem;
	}
	.took i {
		width: 7px;
		height: 7px;
		border-radius: 50%;
		background: var(--ink-soft);
	}
	.took i.live {
		background: var(--good);
		box-shadow: 0 0 0 0 color-mix(in srgb, var(--good) 60%, transparent);
		animation: pulse 1.6s ease-out infinite;
	}
	@keyframes pulse {
		to {
			box-shadow: 0 0 0 6px transparent;
		}
	}

	/* ---------- hero ---------- */
	.hero {
		padding: clamp(2rem, 6vw, 4.5rem) clamp(1rem, 4vw, 3rem) 1.5rem;
		border-bottom: 1px solid var(--line);
	}
	.hero h1 {
		font-family: var(--font-display);
		font-weight: 500;
		font-size: clamp(2.6rem, 9vw, 6.5rem);
		line-height: 0.92;
		letter-spacing: -0.03em;
		margin: 0;
	}
	.hero h1 em {
		font-style: italic;
		font-weight: 600;
		color: var(--accent);
	}
	.hero-mark {
		color: var(--accent);
	}
	.hero-sub {
		margin: 1.1rem 0 0;
		font-size: 0.8rem;
		letter-spacing: 0.04em;
		color: var(--ink-2);
		max-width: 40ch;
	}

	/* ---------- shell ---------- */
	.shell {
		display: grid;
		grid-template-columns: 248px 1fr;
		gap: clamp(1.5rem, 4vw, 3.5rem);
		padding: 2rem clamp(1rem, 4vw, 3rem) 4rem;
		align-items: start;
	}

	/* ---------- facets ---------- */
	.facets {
		position: sticky;
		top: 84px;
		display: flex;
		flex-direction: column;
		gap: 1.6rem;
	}
	.facets-head {
		display: flex;
		justify-content: space-between;
		align-items: center;
		font-size: 0.7rem;
		letter-spacing: 0.2em;
		text-transform: uppercase;
		padding-bottom: 0.6rem;
		border-bottom: 1.5px solid var(--ink);
	}
	.reset {
		border: 0;
		background: transparent;
		color: var(--accent);
		cursor: pointer;
		font: inherit;
		letter-spacing: 0.1em;
	}
	.facet-h {
		font-size: 0.68rem;
		letter-spacing: 0.18em;
		text-transform: uppercase;
		color: var(--ink-soft);
		margin: 0 0 0.55rem;
	}
	.facet-empty {
		font-size: 0.72rem;
		color: var(--ink-soft);
		margin: 0;
	}
	.facet-list {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
	}
	.fitem {
		display: flex;
		align-items: center;
		gap: 0.55rem;
		width: 100%;
		border: 0;
		background: transparent;
		cursor: pointer;
		text-align: left;
		padding: 0.28rem 0;
		color: var(--ink-2);
		transition: color 0.12s;
	}
	.tick {
		flex: none;
		width: 12px;
		height: 12px;
		border: 1.5px solid var(--line-strong);
		background: var(--card);
		transition: all 0.12s;
	}
	.fname {
		flex: 1;
		font-size: 0.86rem;
		line-height: 1.25;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.fcount {
		font-size: 0.68rem;
		color: var(--ink-soft);
		font-variant-numeric: tabular-nums;
	}
	.fitem:hover {
		color: var(--ink);
	}
	.fitem:hover .tick {
		border-color: var(--ink);
	}
	.fitem.on {
		color: var(--ink);
		font-weight: 600;
	}
	.fitem.on .tick {
		background: var(--accent);
		border-color: var(--accent);
		box-shadow: inset 0 0 0 2px var(--card);
	}
	.fitem.on .fcount {
		color: var(--accent);
	}
	.fmore {
		margin-top: 0.5rem;
		border: 0;
		background: transparent;
		color: var(--ink-soft);
		cursor: pointer;
		font-size: 0.7rem;
		letter-spacing: 0.06em;
		padding: 0;
		align-self: flex-start;
		border-bottom: 1px solid var(--line-strong);
	}
	.fmore:hover {
		color: var(--accent);
		border-color: var(--accent);
	}

	/* ---------- results ---------- */
	.results-bar {
		display: flex;
		justify-content: space-between;
		align-items: baseline;
		padding-bottom: 0.8rem;
		border-bottom: 1.5px solid var(--ink);
	}
	.rcount strong {
		font-family: var(--font-display);
		font-size: 1.5rem;
		font-weight: 600;
	}
	.rcount span {
		font-size: 0.7rem;
		letter-spacing: 0.12em;
		text-transform: uppercase;
		color: var(--ink-soft);
		margin-left: 0.5rem;
	}
	.sortbox {
		font-size: 0.7rem;
		letter-spacing: 0.12em;
		text-transform: uppercase;
		color: var(--ink-soft);
		display: inline-flex;
		align-items: center;
		gap: 0.5rem;
	}
	.sortbox select {
		font-family: var(--font-mono);
		font-size: 0.78rem;
		color: var(--ink);
		background: var(--card);
		border: 1.5px solid var(--ink);
		padding: 0.3rem 0.5rem;
		cursor: pointer;
	}

	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: 0.5rem;
		margin-top: 1rem;
	}
	.chip {
		display: inline-flex;
		align-items: center;
		gap: 0.4rem;
		font-family: var(--font-mono);
		font-size: 0.72rem;
		background: var(--ink);
		color: var(--paper);
		border: 0;
		padding: 0.3rem 0.6rem;
		cursor: pointer;
	}
	.chip i {
		font-style: normal;
		color: var(--accent);
	}
	.chip:hover {
		background: var(--accent-deep);
	}

	/* ---------- grid ---------- */
	.grid {
		margin-top: 1.4rem;
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(208px, 1fr));
		gap: 1px;
		background: var(--line);
		border: 1px solid var(--line);
		transition: opacity 0.2s;
	}
	.grid.dim {
		opacity: 0.45;
	}
	.card {
		background: var(--card);
		display: flex;
		flex-direction: column;
		animation: rise 0.4s cubic-bezier(0.2, 0.7, 0.2, 1) both;
		animation-delay: calc(var(--i) * 28ms);
	}
	@keyframes rise {
		from {
			opacity: 0;
			transform: translateY(10px);
		}
	}
	.thumb {
		position: relative;
		aspect-ratio: 4 / 3;
		overflow: hidden;
		border-bottom: 1px solid var(--line);
	}
	.thumb-letter {
		position: absolute;
		right: -0.1em;
		bottom: -0.32em;
		font-family: var(--font-display);
		font-weight: 900;
		font-size: 6.5rem;
		line-height: 1;
		color: color-mix(in srgb, var(--ink) 14%, transparent);
		user-select: none;
	}
	.thumb-idx {
		position: absolute;
		top: 0.5rem;
		left: 0.6rem;
		font-size: 0.66rem;
		color: color-mix(in srgb, var(--ink) 45%, transparent);
		letter-spacing: 0.08em;
	}
	.thumb-sku {
		position: absolute;
		top: 0.5rem;
		right: 0.6rem;
		font-size: 0.6rem;
		color: color-mix(in srgb, var(--ink) 55%, transparent);
		letter-spacing: 0.04em;
	}
	.thumb-stock {
		position: absolute;
		left: 0.6rem;
		bottom: 0.55rem;
		font-family: var(--font-mono);
		font-size: 0.6rem;
		letter-spacing: 0.06em;
		text-transform: uppercase;
		background: var(--ink);
		color: var(--paper);
		padding: 0.18rem 0.4rem;
	}
	.thumb-stock.out {
		background: var(--accent);
	}
	.card-body {
		padding: 0.8rem 0.85rem 0.95rem;
		display: flex;
		flex-direction: column;
		gap: 0.45rem;
		flex: 1;
	}
	.eyebrow {
		display: flex;
		align-items: center;
		gap: 0.4rem;
		font-size: 0.6rem;
		letter-spacing: 0.1em;
		text-transform: uppercase;
		color: var(--accent);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.eyebrow span {
		color: var(--line-strong);
	}
	.pname {
		font-family: var(--font-display);
		font-weight: 500;
		font-size: 1.04rem;
		line-height: 1.16;
		letter-spacing: -0.01em;
		margin: 0;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
		min-height: 2.4em;
	}
	.price-row {
		margin-top: auto;
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding-top: 0.5rem;
		border-top: 1px solid var(--line);
	}
	.price {
		font-size: 0.92rem;
		font-weight: 500;
		font-variant-numeric: tabular-nums;
	}
	.dotmark {
		width: 9px;
		height: 9px;
		border-radius: 50%;
		border: 1.5px solid var(--ink);
		transition: background 0.15s;
	}
	.card:hover {
		box-shadow: inset 0 0 0 1.5px var(--ink);
	}
	.card:hover .dotmark {
		background: var(--accent);
		border-color: var(--accent);
	}

	/* ---------- pager ---------- */
	.pager {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-top: 2rem;
		padding-top: 1.2rem;
		border-top: 1.5px solid var(--ink);
		font-size: 0.76rem;
		letter-spacing: 0.06em;
	}
	.pager button {
		border: 1.5px solid var(--ink);
		background: var(--card);
		color: var(--ink);
		padding: 0.5rem 1rem;
		cursor: pointer;
		font: inherit;
		transition:
			background 0.12s,
			color 0.12s;
	}
	.pager button:hover:not(:disabled) {
		background: var(--ink);
		color: var(--paper);
	}
	.pager button:disabled {
		opacity: 0.35;
		cursor: not-allowed;
	}
	.pager-pos {
		color: var(--ink-soft);
	}

	/* ---------- empty ---------- */
	.empty {
		padding: 4rem 1rem;
		text-align: center;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 0.5rem;
	}
	.empty-big {
		font-family: var(--font-display);
		font-style: italic;
		font-size: 3rem;
		margin: 0;
		color: var(--ink);
	}
	.empty .mono {
		font-size: 0.78rem;
		color: var(--ink-soft);
		letter-spacing: 0.04em;
	}
	.empty-reset {
		margin-top: 1rem;
		border: 1.5px solid var(--ink);
		background: transparent;
		padding: 0.5rem 1rem;
		cursor: pointer;
		text-transform: uppercase;
		letter-spacing: 0.1em;
		font-size: 0.7rem;
	}

	/* ---------- footer ---------- */
	.foot {
		display: flex;
		justify-content: space-between;
		flex-wrap: wrap;
		gap: 1rem;
		padding: 1.4rem clamp(1rem, 4vw, 3rem);
		border-top: 1.5px solid var(--ink);
		font-size: 0.68rem;
		letter-spacing: 0.1em;
		text-transform: uppercase;
		color: var(--ink-soft);
	}

	/* ---------- responsive ---------- */
	@media (max-width: 860px) {
		.masthead {
			grid-template-columns: 1fr;
			gap: 0.8rem;
		}
		.masthead-meta {
			display: none;
		}
		.shell {
			grid-template-columns: 1fr;
		}
		.facets {
			position: static;
		}
	}

	.mono {
		font-family: var(--font-mono);
	}
</style>
