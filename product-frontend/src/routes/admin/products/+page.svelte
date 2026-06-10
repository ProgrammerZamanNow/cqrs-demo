<script lang="ts">
	type Ref = { id: string; name: string };
	type Product = {
		id: string;
		sku: string;
		name: string;
		description: string | null;
		price: number;
		stock: number;
		imageUrl: string;
		category: Ref;
		brand: Ref;
	};

	const SIZE = 20;
	const SORTS = [
		{ v: 'name,asc', l: 'Nama A–Z' },
		{ v: 'name,desc', l: 'Nama Z–A' },
		{ v: 'price,asc', l: 'Harga ↑' },
		{ v: 'price,desc', l: 'Harga ↓' },
		{ v: 'stock,desc', l: 'Stok terbanyak' }
	];

	// Engine baca (read model). facet=false → admin tak menampilkan facet, jadi
	// kita ukur biaya SEARCH murni untuk membandingkan ketiga engine.
	//   pg      → PostgreSQL naif      (LIKE '%..%' seq scan)
	//   pg-trgm → PostgreSQL + trigram (GIN pg_trgm)
	//   os      → OpenSearch           (n-gram inverted index)
	type EngineKey = 'pg' | 'pg-trgm' | 'os';
	const ENGINES: { v: EngineKey; l: string; read: string; q: Record<string, string> }[] = [
		{ v: 'pg', l: 'PostgreSQL', read: '/api/products', q: { facet: 'false' } },
		{ v: 'pg-trgm', l: 'PostgreSQL + trigram', read: '/api/products', q: { engine: 'trigram', facet: 'false' } },
		{ v: 'os', l: 'OpenSearch', read: '/api/products/_search', q: { facet: 'false' } }
	];
	// Tulis SELALU ke PostgreSQL (write model / source of truth).
	const WRITE = '/api/products';

	let engine = $state<EngineKey>('pg');
	let engineCfg = $derived(ENGINES.find((e) => e.v === engine)!);

	let products = $state<Product[]>([]);
	let total = $state(0);
	let totalPages = $state(0);
	let page = $state(0);
	let took = $state(0);
	let keyword = $state('');
	let appliedKeyword = $state('');
	let sortBy = $state('name,asc');
	let searchNonce = $state(0);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let notice = $state<string | null>(null);

	let categories = $state<Ref[]>([]);
	let brands = $state<Ref[]>([]);

	// dialog
	let showForm = $state(false);
	let saving = $state(false);
	let detailLoading = $state(false);
	let formError = $state<string | null>(null);
	let editingId = $state<string | null>(null);
	let f = $state({
		sku: '',
		name: '',
		description: '',
		price: 0,
		stock: 0,
		imageUrl: '',
		categoryId: '',
		brandId: ''
	});

	let reqId = 0;

	async function load() {
		const my = ++reqId;
		loading = true;
		error = null;
		try {
			const cfg = engineCfg;
			const params = new URLSearchParams();
			if (appliedKeyword.trim()) params.set('keyword', appliedKeyword.trim());
			params.set('page', String(page));
			params.set('size', String(SIZE));
			params.set('sort', sortBy);
			for (const [k, v] of Object.entries(cfg.q)) params.set(k, v);
			const res = await fetch(cfg.read + '?' + params.toString());
			const json = await res.json();
			if (my !== reqId) return;
			took = json.metadata?.processTimeMs ?? 0;
			if (res.ok) {
				products = json.data ?? [];
				total = json.paging?.totalElement ?? 0;
				totalPages = json.paging?.totalPage ?? 0;
			} else {
				error = json.error ?? `error ${res.status}`;
			}
		} catch {
			if (my !== reqId) return;
			error = 'gagal terhubung ke server';
		} finally {
			if (my === reqId) loading = false;
		}
	}

	async function loadRefs() {
		const [c, b] = await Promise.all([
			fetch('/api/categories?size=100&sort=name,asc').then((r) => r.json()),
			fetch('/api/brands?size=100&sort=name,asc').then((r) => r.json())
		]);
		categories = c.data ?? [];
		brands = b.data ?? [];
	}

	$effect(() => {
		void [engine, page, appliedKeyword, sortBy, searchNonce];
		load();
	});
	$effect(() => {
		loadRefs();
	});

	function pickEngine(v: EngineKey) {
		if (engine === v) {
			searchNonce++; // klik ulang engine yang sama → re-query (bandingkan angka)
		} else {
			engine = v;
			page = 0;
		}
	}

	function applySearch(e: Event) {
		e.preventDefault();
		appliedKeyword = keyword.trim();
		page = 0;
		searchNonce++; // selalu picu API call walau keyword sama
	}

	function flashNotice(msg: string) {
		notice = msg;
		setTimeout(() => (notice = null), 6000);
	}

	function openCreate() {
		editingId = null;
		formError = null;
		f = {
			sku: '',
			name: '',
			description: '',
			price: 0,
			stock: 0,
			imageUrl: '',
			categoryId: categories[0]?.id ?? '',
			brandId: brands[0]?.id ?? ''
		};
		showForm = true;
	}

	// Edit: selalu ambil data TERBARU dari write model (PostgreSQL) lewat GET detail.
	async function openEdit(p: Product) {
		editingId = p.id;
		formError = null;
		detailLoading = true;
		showForm = true;
		try {
			const res = await fetch(`${WRITE}/${p.id}`);
			const json = await res.json();
			if (res.ok) {
				const d = json.data;
				f = {
					sku: d.sku,
					name: d.name,
					description: d.description ?? '',
					price: d.price,
					stock: d.stock,
					imageUrl: d.imageUrl ?? '',
					categoryId: d.category.id,
					brandId: d.brand.id
				};
			} else {
				formError = json.error ?? `error ${res.status}`;
			}
		} catch {
			formError = 'gagal memuat detail';
		}
		detailLoading = false;
	}

	function closeForm() {
		showForm = false;
	}

	// Notice pasca-tulis: di OpenSearch ada jeda CDC; di PostgreSQL langsung tampil.
	function afterWrite(verb: string) {
		if (engine === 'os') {
			flashNotice(
				`${verb} ke PostgreSQL. Akan tampil di OpenSearch ~1 detik setelah CDC sync — klik ↻ Refresh.`
			);
		}
	}

	async function save(e: Event) {
		e.preventDefault();
		saving = true;
		formError = null;
		const body = JSON.stringify({
			sku: f.sku,
			name: f.name,
			description: f.description.trim() ? f.description : null,
			price: Number(f.price),
			stock: Number(f.stock),
			imageUrl: f.imageUrl.trim() ? f.imageUrl : null,
			categoryId: f.categoryId,
			brandId: f.brandId
		});
		const url = editingId ? `${WRITE}/${editingId}` : WRITE;
		try {
			const res = await fetch(url, {
				method: editingId ? 'PUT' : 'POST',
				headers: { 'Content-Type': 'application/json' },
				body
			});
			const json = await res.json().catch(() => ({}));
			if (res.ok) {
				showForm = false;
				afterWrite(editingId ? 'Tersimpan' : 'Dibuat');
				await load();
			} else {
				formError = json.error ?? `error ${res.status}`;
			}
		} catch {
			formError = 'gagal terhubung ke server';
		}
		saving = false;
	}

	async function remove(p: Product) {
		if (!confirm(`Hapus produk "${p.name}" (${p.sku})?\nTindakan ini tidak bisa dibatalkan.`)) return;
		error = null;
		try {
			const res = await fetch(`${WRITE}/${p.id}`, { method: 'DELETE' });
			const json = await res.json().catch(() => ({}));
			if (res.ok) {
				afterWrite('Dihapus');
				await load();
			} else error = json.error ?? `error ${res.status}`;
		} catch {
			error = 'gagal terhubung ke server';
		}
	}

	let isSlow = $derived(engine !== 'os' && took > 1000);
	const idr = (n: number) =>
		new Intl.NumberFormat('id-ID', { style: 'currency', currency: 'IDR', maximumFractionDigits: 0 }).format(n);
	const fmt = (n: number) => n.toLocaleString('id-ID');
</script>

<svelte:window onkeydown={(e) => e.key === 'Escape' && showForm && closeForm()} />

<div class="head">
	<h1>Products</h1>
	<span class="count mono">{fmt(total)} produk</span>
	<span class="took mono" class:slow={isSlow} class:os={engine === 'os'}>
		<i></i>{engineCfg.l} · {took} ms{isSlow ? ' ⚠' : ''}
	</span>
	<button class="ghost mono" onclick={load} title="Muat ulang">↻ Refresh</button>
	<button class="add mono" onclick={openCreate}>+ Tambah Produk</button>
</div>

<div class="engines mono" role="group" aria-label="Pilih engine baca">
	<span class="engines-label">baca dari</span>
	{#each ENGINES as e}
		<button
			class="eng"
			class:active={engine === e.v}
			class:os={e.v === 'os'}
			class:trgm={e.v === 'pg-trgm'}
			onclick={() => pickEngine(e.v)}
		>
			{e.l}
		</button>
	{/each}
</div>

<p class="banner mono">
	{#if engine === 'os'}
		📖 List dibaca dari <b>OpenSearch</b> (read model). Tambah/edit/hapus ditulis ke
		<b>PostgreSQL</b> (write model) → tersinkron ke OpenSearch via CDC (~1 dtk).
	{:else if engine === 'pg-trgm'}
		📖 List dibaca dari <b>PostgreSQL + trigram</b> (GIN <code>pg_trgm</code>) — keyword
		search pakai index. Tulis langsung ke PostgreSQL.
	{:else}
		📖 List dibaca dari <b>PostgreSQL naif</b> (<code>LIKE '%..%'</code> sequential scan).
		Tulis langsung ke PostgreSQL.
	{/if}
	<span class="hint">facet dimatikan (<code>facet=false</code>) → ukur biaya search murni.</span>
</p>

{#if notice}<p class="notice mono">{notice}</p>{/if}
{#if error}<p class="err mono">{error}</p>{/if}

<div class="card list">
	<div class="toolbar">
		<form class="search" onsubmit={applySearch}>
			<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7" /><line x1="21" y1="21" x2="16.5" y2="16.5" /></svg>
			<input bind:value={keyword} placeholder="Cari produk (nama / deskripsi)…" aria-label="Cari produk" />
			<button type="submit" class="go mono">Cari</button>
		</form>
		<label class="sortbox mono">
			urut
			<select bind:value={sortBy} onchange={() => (page = 0)}>
				{#each SORTS as s}<option value={s.v}>{s.l}</option>{/each}
			</select>
		</label>
	</div>

	{#if loading}
		<p class="muted mono">memuat…</p>
	{:else if products.length === 0}
		<p class="muted mono">Tidak ada produk.</p>
	{:else}
		<table>
			<thead class="mono">
				<tr><th>SKU</th><th>Nama</th><th>Kategori</th><th>Brand</th><th class="r">Harga</th><th class="r">Stok</th><th></th></tr>
			</thead>
			<tbody>
				{#each products as p (p.id)}
					<tr>
						<td class="mono sku">{p.sku}</td>
						<td class="name">{p.name}</td>
						<td>{p.category?.name ?? '—'}</td>
						<td>{p.brand?.name ?? '—'}</td>
						<td class="r mono">{idr(p.price)}</td>
						<td class="r mono" class:out={p.stock === 0}>{p.stock}</td>
						<td class="row-actions">
							<button class="link" onclick={() => openEdit(p)}>edit</button>
							<button class="link del" onclick={() => remove(p)}>hapus</button>
						</td>
					</tr>
				{/each}
			</tbody>
		</table>
	{/if}
</div>

{#if totalPages > 1}
	<nav class="pager mono">
		<button onclick={() => (page = Math.max(0, page - 1))} disabled={page <= 0}>← sebelumnya</button>
		<span>hal. {page + 1} / {fmt(totalPages)}</span>
		<button onclick={() => (page = Math.min(totalPages - 1, page + 1))} disabled={page >= totalPages - 1}>berikutnya →</button>
	</nav>
{/if}

{#if showForm}
	<div class="overlay">
		<form class="modal" onsubmit={save}>
			<h2 class="mono">{editingId ? 'Edit Produk' : 'Tambah Produk'}</h2>
			{#if formError}<p class="err mono">{formError}</p>{/if}
			{#if detailLoading}
				<p class="muted mono">memuat detail…</p>
			{:else}
				<div class="form-grid">
					<label>
						<span class="mono">SKU *</span>
						<input bind:value={f.sku} maxlength="50" required placeholder="SKU-0000001" />
					</label>
					<label>
						<span class="mono">Nama *</span>
						<input bind:value={f.name} maxlength="150" required />
					</label>
					<label class="span2">
						<span class="mono">Deskripsi</span>
						<textarea bind:value={f.description} rows="2"></textarea>
					</label>
					<label>
						<span class="mono">Harga (IDR) *</span>
						<input type="number" bind:value={f.price} min="0" step="1" required />
					</label>
					<label>
						<span class="mono">Stok</span>
						<input type="number" bind:value={f.stock} min="0" step="1" />
					</label>
					<label>
						<span class="mono">Kategori *</span>
						<select bind:value={f.categoryId} required>
							{#each categories as c}<option value={c.id}>{c.name}</option>{/each}
						</select>
					</label>
					<label>
						<span class="mono">Brand *</span>
						<select bind:value={f.brandId} required>
							{#each brands as b}<option value={b.id}>{b.name}</option>{/each}
						</select>
					</label>
					<label class="span2">
						<span class="mono">Image URL</span>
						<input bind:value={f.imageUrl} maxlength="500" placeholder="kosong = pakai dummy" />
					</label>
				</div>
			{/if}
			<div class="actions">
				<button type="submit" class="primary mono" disabled={saving || detailLoading}>
					{saving ? 'menyimpan…' : editingId ? 'Simpan' : 'Tambah'}
				</button>
				<button type="button" class="ghost mono" onclick={closeForm}>Batal</button>
			</div>
		</form>
	</div>
{/if}

<style>
	.head {
		display: flex;
		align-items: baseline;
		gap: 1rem;
		padding-bottom: 1rem;
		border-bottom: 1.5px solid var(--ink);
		margin-bottom: 1rem;
		flex-wrap: wrap;
	}
	.head h1 {
		font-family: var(--font-display);
		font-weight: 600;
		font-size: 2rem;
		margin: 0;
	}
	.count {
		font-size: 0.72rem;
		letter-spacing: 0.12em;
		text-transform: uppercase;
		color: var(--ink-soft);
	}
	.took {
		margin-left: auto;
		display: inline-flex;
		align-items: center;
		gap: 0.4rem;
		font-size: 0.82rem;
		font-weight: 600;
		font-variant-numeric: tabular-nums;
		color: var(--ink);
	}
	.took i {
		width: 7px;
		height: 7px;
		border-radius: 50%;
		background: var(--accent);
	}
	.took.os i {
		background: var(--good);
	}
	.took.slow {
		color: var(--accent);
	}
	.add {
		border: 0;
		background: var(--accent);
		color: var(--paper);
		padding: 0.55rem 1rem;
		cursor: pointer;
		font-size: 0.72rem;
		letter-spacing: 0.1em;
		text-transform: uppercase;
	}
	.add:hover {
		background: var(--accent-deep);
	}

	/* segmented engine selector */
	.engines {
		display: flex;
		align-items: stretch;
		gap: 0;
		flex-wrap: wrap;
		margin-bottom: 0.9rem;
	}
	.engines-label {
		display: inline-flex;
		align-items: center;
		font-size: 0.6rem;
		letter-spacing: 0.14em;
		text-transform: uppercase;
		color: var(--ink-soft);
		margin-right: 0.7rem;
	}
	.eng {
		border: 1.5px solid var(--ink);
		border-right-width: 0;
		background: var(--card);
		color: var(--ink-2);
		font-family: var(--font-mono);
		font-size: 0.72rem;
		letter-spacing: 0.06em;
		text-transform: uppercase;
		padding: 0.45rem 0.9rem;
		cursor: pointer;
	}
	.eng:first-of-type {
		border-left-width: 1.5px;
	}
	.eng:last-of-type {
		border-right-width: 1.5px;
	}
	.eng:hover {
		color: var(--ink);
		background: color-mix(in srgb, var(--ink) 6%, var(--card));
	}
	.eng.active {
		background: var(--ink);
		color: var(--paper);
		font-weight: 600;
	}
	.eng.active.os {
		background: var(--good);
	}
	.eng.active.trgm {
		background: #2e7d32;
		color: var(--paper);
	}
	.banner {
		font-size: 0.72rem;
		line-height: 1.5;
		color: var(--ink-2);
		background: color-mix(in srgb, var(--ink) 5%, transparent);
		border-left: 3px solid var(--ink);
		padding: 0.55rem 0.8rem;
		margin: 0 0 1rem;
	}
	.banner code {
		font-family: var(--font-mono);
		font-size: 0.92em;
		background: color-mix(in srgb, var(--ink) 8%, transparent);
		padding: 0 0.2rem;
	}
	.banner .hint {
		display: block;
		margin-top: 0.2rem;
		color: var(--ink-soft);
	}
	.notice {
		font-size: 0.74rem;
		color: var(--ink);
		background: color-mix(in srgb, var(--good) 22%, transparent);
		padding: 0.5rem 0.7rem;
		margin: 0 0 1rem;
	}
	.card {
		background: var(--card);
		border: 1.5px solid var(--ink);
		overflow-x: auto;
	}
	.toolbar {
		display: flex;
		align-items: center;
		gap: 0.8rem;
		padding: 0.7rem 0.9rem;
		border-bottom: 1.5px solid var(--ink);
	}
	.search {
		display: flex;
		align-items: center;
		gap: 0.45rem;
		flex: 1;
		border: 1.5px solid var(--line-strong);
		background: var(--paper);
		padding: 0.25rem 0.3rem 0.25rem 0.55rem;
	}
	.search svg {
		width: 15px;
		height: 15px;
		flex: none;
		fill: none;
		stroke: var(--ink-soft);
		stroke-width: 2;
		stroke-linecap: round;
	}
	.search input {
		border: 0;
		background: transparent;
		padding: 0.15rem 0;
		font-size: 0.86rem;
		flex: 1;
		min-width: 0;
		font-family: var(--font-body);
		color: var(--ink);
	}
	.search input:focus {
		outline: none;
	}
	.search .go {
		border: 0;
		background: var(--ink);
		color: var(--paper);
		font-size: 0.66rem;
		letter-spacing: 0.1em;
		text-transform: uppercase;
		padding: 0.4rem 0.7rem;
		cursor: pointer;
	}
	.search .go:hover {
		background: var(--accent);
	}
	.sortbox {
		display: inline-flex;
		align-items: center;
		gap: 0.45rem;
		font-size: 0.66rem;
		letter-spacing: 0.1em;
		text-transform: uppercase;
		color: var(--ink-soft);
	}
	.sortbox select {
		font-family: var(--font-mono);
		font-size: 0.76rem;
		padding: 0.3rem 0.45rem;
		border: 1.5px solid var(--ink);
		background: var(--card);
		color: var(--ink);
		cursor: pointer;
	}
	.muted {
		color: var(--ink-soft);
		font-size: 0.8rem;
		padding: 1.5rem;
		text-align: center;
	}
	.err {
		font-size: 0.74rem;
		color: var(--paper);
		background: var(--accent);
		padding: 0.4rem 0.6rem;
		margin: 0 0 1rem;
	}
	table {
		width: 100%;
		border-collapse: collapse;
		font-size: 0.86rem;
	}
	thead th {
		text-align: left;
		font-size: 0.6rem;
		letter-spacing: 0.12em;
		text-transform: uppercase;
		color: var(--ink-soft);
		font-weight: 600;
		padding: 0.6rem 0.9rem;
		border-bottom: 1.5px solid var(--ink);
	}
	th.r,
	td.r {
		text-align: right;
	}
	tbody td {
		padding: 0.55rem 0.9rem;
		border-bottom: 1px solid var(--line);
	}
	.sku {
		font-size: 0.74rem;
		color: var(--ink-soft);
		white-space: nowrap;
	}
	.name {
		font-weight: 600;
	}
	td.out {
		color: var(--accent);
	}
	.row-actions {
		white-space: nowrap;
		text-align: right;
	}
	.link {
		border: 0;
		background: transparent;
		cursor: pointer;
		font-family: var(--font-mono);
		font-size: 0.74rem;
		color: var(--ink-soft);
		padding: 0 0.3rem;
	}
	.link:hover {
		color: var(--ink);
		text-decoration: underline;
	}
	.link.del:hover {
		color: var(--accent);
	}
	.pager {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-top: 1.2rem;
		font-size: 0.76rem;
		color: var(--ink-soft);
	}
	.pager button {
		border: 1.5px solid var(--ink);
		background: var(--card);
		color: var(--ink);
		padding: 0.45rem 0.9rem;
		cursor: pointer;
		font: inherit;
	}
	.pager button:hover:not(:disabled) {
		background: var(--ink);
		color: var(--paper);
	}
	.pager button:disabled {
		opacity: 0.35;
		cursor: not-allowed;
	}

	/* modal */
	.overlay {
		position: fixed;
		inset: 0;
		z-index: 100;
		background: color-mix(in srgb, var(--ink) 45%, transparent);
		display: flex;
		align-items: flex-start;
		justify-content: center;
		padding: 6vh 1rem;
		overflow-y: auto;
	}
	.modal {
		background: var(--card);
		border: 1.5px solid var(--ink);
		box-shadow: 8px 8px 0 var(--ink);
		width: 100%;
		max-width: 560px;
		padding: 1.4rem;
		display: flex;
		flex-direction: column;
		gap: 1rem;
	}
	.modal h2 {
		margin: 0;
		font-size: 0.74rem;
		letter-spacing: 0.16em;
		text-transform: uppercase;
		color: var(--ink-soft);
	}
	.form-grid {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 0.8rem;
	}
	label {
		display: flex;
		flex-direction: column;
		gap: 0.3rem;
	}
	label.span2 {
		grid-column: 1 / -1;
	}
	label span {
		font-size: 0.62rem;
		letter-spacing: 0.1em;
		text-transform: uppercase;
		color: var(--ink-soft);
	}
	input,
	textarea,
	select {
		font-family: var(--font-body);
		font-size: 0.9rem;
		color: var(--ink);
		background: var(--paper);
		border: 1.5px solid var(--line-strong);
		padding: 0.5rem 0.6rem;
	}
	input:focus,
	textarea:focus,
	select:focus {
		outline: none;
		border-color: var(--ink);
	}
	.actions {
		display: flex;
		gap: 0.5rem;
	}
	.primary {
		border: 0;
		background: var(--accent);
		color: var(--paper);
		padding: 0.55rem 1.2rem;
		cursor: pointer;
		font-size: 0.72rem;
		letter-spacing: 0.1em;
		text-transform: uppercase;
	}
	.primary:hover {
		background: var(--accent-deep);
	}
	.primary:disabled {
		opacity: 0.5;
	}
	.ghost {
		border: 1.5px solid var(--line-strong);
		background: transparent;
		color: var(--ink-2);
		padding: 0.5rem 0.9rem;
		cursor: pointer;
		font-size: 0.72rem;
		letter-spacing: 0.1em;
		text-transform: uppercase;
	}
	.ghost:hover {
		border-color: var(--ink);
		color: var(--ink);
	}
	@media (max-width: 560px) {
		.form-grid {
			grid-template-columns: 1fr;
		}
	}
</style>
