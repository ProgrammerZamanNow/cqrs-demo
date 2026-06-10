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

	let products = $state<Product[]>([]);
	let total = $state(0);
	let totalPages = $state(0);
	let page = $state(0);
	let loading = $state(true);
	let error = $state<string | null>(null);

	let categories = $state<Ref[]>([]);
	let brands = $state<Ref[]>([]);

	// form
	let showForm = $state(false);
	let saving = $state(false);
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

	async function load() {
		loading = true;
		error = null;
		try {
			const res = await fetch(`/api/products?page=${page}&size=${SIZE}&sort=name,asc`);
			const json = await res.json();
			if (res.ok) {
				products = json.data ?? [];
				total = json.paging?.totalElement ?? 0;
				totalPages = json.paging?.totalPage ?? 0;
			} else {
				error = json.error ?? `error ${res.status}`;
			}
		} catch {
			error = 'gagal terhubung ke server';
		}
		loading = false;
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
		void page;
		load();
	});
	$effect(() => {
		loadRefs();
	});

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

	function openEdit(p: Product) {
		editingId = p.id;
		formError = null;
		f = {
			sku: p.sku,
			name: p.name,
			description: p.description ?? '',
			price: p.price,
			stock: p.stock,
			imageUrl: p.imageUrl ?? '',
			categoryId: p.category.id,
			brandId: p.brand.id
		};
		showForm = true;
	}

	function closeForm() {
		showForm = false;
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
		const url = editingId ? `/api/products/${editingId}` : '/api/products';
		try {
			const res = await fetch(url, {
				method: editingId ? 'PUT' : 'POST',
				headers: { 'Content-Type': 'application/json' },
				body
			});
			const json = await res.json().catch(() => ({}));
			if (res.ok) {
				showForm = false;
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
		if (!confirm(`Hapus produk "${p.name}" (${p.sku})?`)) return;
		error = null;
		try {
			const res = await fetch(`/api/products/${p.id}`, { method: 'DELETE' });
			const json = await res.json().catch(() => ({}));
			if (res.ok) await load();
			else error = json.error ?? `error ${res.status}`;
		} catch {
			error = 'gagal terhubung ke server';
		}
	}

	const idr = (n: number) =>
		new Intl.NumberFormat('id-ID', { style: 'currency', currency: 'IDR', maximumFractionDigits: 0 }).format(n);
	const fmt = (n: number) => n.toLocaleString('id-ID');
</script>

<div class="head">
	<h1>Products</h1>
	<span class="count mono">{fmt(total)} produk</span>
	<button class="add mono" onclick={openCreate}>+ Tambah Produk</button>
</div>

{#if error}<p class="err mono">{error}</p>{/if}

<div class="card list">
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

<svelte:window onkeydown={(e) => e.key === 'Escape' && showForm && closeForm()} />

{#if showForm}
	<div class="overlay">
		<form class="modal" onsubmit={save}>
			<h2 class="mono">{editingId ? 'Edit Produk' : 'Tambah Produk'}</h2>
			{#if formError}<p class="err mono">{formError}</p>{/if}

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

			<div class="actions">
				<button type="submit" class="primary mono" disabled={saving}>
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
		margin-bottom: 1.5rem;
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
	.add {
		margin-left: auto;
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
	.card {
		background: var(--card);
		border: 1.5px solid var(--ink);
		overflow-x: auto;
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
		padding: 0.55rem 1rem;
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
