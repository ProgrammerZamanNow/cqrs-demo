<script lang="ts">
	type Item = {
		id: string;
		name: string;
		description: string | null;
		createdAt: number;
		updatedAt: number;
	};

	let { title, apiPath, noun }: { title: string; apiPath: string; noun: string } = $props();

	const SORTS = [
		{ v: 'name-asc', l: 'Nama A–Z' },
		{ v: 'name-desc', l: 'Nama Z–A' },
		{ v: 'new', l: 'Terbaru' },
		{ v: 'old', l: 'Terlama' }
	];

	let items = $state<Item[]>([]);
	let total = $state(0);
	let took = $state(0);
	let loading = $state(true);
	let error = $state<string | null>(null);

	let query = $state('');
	let sortBy = $state('name-asc');

	// dialog
	let showForm = $state(false);
	let saving = $state(false);
	let detailLoading = $state(false);
	let formError = $state<string | null>(null);
	let editingId = $state<string | null>(null);
	let name = $state('');
	let description = $state('');

	let displayed = $derived.by(() => {
		const q = query.trim().toLowerCase();
		const list = q ? items.filter((it) => it.name.toLowerCase().includes(q)) : items.slice();
		list.sort((a, b) => {
			switch (sortBy) {
				case 'name-desc':
					return b.name.localeCompare(a.name);
				case 'new':
					return b.createdAt - a.createdAt;
				case 'old':
					return a.createdAt - b.createdAt;
				default:
					return a.name.localeCompare(b.name);
			}
		});
		return list;
	});

	async function load() {
		loading = true;
		error = null;
		try {
			const res = await fetch(`${apiPath}?size=100&sort=name,asc`);
			const json = await res.json();
			took = json.metadata?.processTimeMs ?? 0;
			if (res.ok) {
				items = json.data ?? [];
				total = json.paging?.totalElement ?? items.length;
			} else {
				error = json.error ?? `error ${res.status}`;
			}
		} catch {
			error = 'gagal terhubung ke server';
		}
		loading = false;
	}

	$effect(() => {
		void apiPath;
		load();
	});

	function openCreate() {
		editingId = null;
		name = '';
		description = '';
		formError = null;
		showForm = true;
	}

	// Edit: ambil data TERBARU dari API get detail dulu sebelum buka dialog.
	async function openEdit(it: Item) {
		editingId = it.id;
		formError = null;
		detailLoading = true;
		showForm = true;
		try {
			const res = await fetch(`${apiPath}/${it.id}`);
			const json = await res.json();
			took = json.metadata?.processTimeMs ?? took;
			if (res.ok) {
				name = json.data.name;
				description = json.data.description ?? '';
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

	async function save(e: Event) {
		e.preventDefault();
		saving = true;
		formError = null;
		const url = editingId ? `${apiPath}/${editingId}` : apiPath;
		try {
			const res = await fetch(url, {
				method: editingId ? 'PUT' : 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ name, description: description.trim() ? description : null })
			});
			const json = await res.json().catch(() => ({}));
			took = json.metadata?.processTimeMs ?? took;
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

	async function remove(it: Item) {
		if (!confirm(`Hapus ${noun} "${it.name}"?\nTindakan ini tidak bisa dibatalkan.`)) return;
		error = null;
		try {
			const res = await fetch(`${apiPath}/${it.id}`, { method: 'DELETE' });
			const json = await res.json().catch(() => ({}));
			took = json.metadata?.processTimeMs ?? took;
			if (res.ok) await load();
			else error = json.error ?? `error ${res.status}`;
		} catch {
			error = 'gagal terhubung ke server';
		}
	}

	const fmtDate = (ms: number) =>
		new Date(ms).toLocaleDateString('id-ID', { day: '2-digit', month: 'short', year: 'numeric' });
</script>

<svelte:window onkeydown={(e) => e.key === 'Escape' && showForm && closeForm()} />

<div class="head">
	<h1>{title}</h1>
	<span class="count mono">{total} {noun}</span>
	<span class="took mono"><i></i>API {took} ms</span>
	<button class="add mono" onclick={openCreate}>+ Tambah {noun}</button>
</div>

{#if error}<p class="err mono">{error}</p>{/if}

<div class="card list">
	<div class="toolbar">
		<div class="search">
			<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7" /><line x1="21" y1="21" x2="16.5" y2="16.5" /></svg>
			<input bind:value={query} placeholder="Cari {noun}…" aria-label="Cari {noun}" />
		</div>
		<label class="sortbox mono">
			urut
			<select bind:value={sortBy}>
				{#each SORTS as s}<option value={s.v}>{s.l}</option>{/each}
			</select>
		</label>
	</div>

	{#if loading}
		<p class="muted mono">memuat…</p>
	{:else if displayed.length === 0}
		<p class="muted mono">{query ? `Tidak ada ${noun} cocok.` : `Belum ada ${noun}.`}</p>
	{:else}
		<table>
			<thead class="mono">
				<tr><th>Nama</th><th>Deskripsi</th><th>Dibuat</th><th></th></tr>
			</thead>
			<tbody>
				{#each displayed as it (it.id)}
					<tr>
						<td class="name">{it.name}</td>
						<td class="desc">{it.description ?? '—'}</td>
						<td class="mono date">{fmtDate(it.createdAt)}</td>
						<td class="row-actions">
							<button class="link" onclick={() => openEdit(it)}>edit</button>
							<button class="link del" onclick={() => remove(it)}>hapus</button>
						</td>
					</tr>
				{/each}
			</tbody>
		</table>
	{/if}
</div>

{#if showForm}
	<div class="overlay">
		<form class="modal" onsubmit={save}>
			<h2 class="mono">{editingId ? `Edit ${noun}` : `Tambah ${noun}`}</h2>
			{#if formError}<p class="err mono">{formError}</p>{/if}
			{#if detailLoading}
				<p class="muted mono">memuat detail…</p>
			{:else}
				<label>
					<span class="mono">Nama *</span>
					<input bind:value={name} maxlength="100" required placeholder="Nama {noun}" />
				</label>
				<label>
					<span class="mono">Deskripsi</span>
					<textarea bind:value={description} rows="3" placeholder="Opsional"></textarea>
				</label>
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
		background: var(--good);
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
		padding: 0.3rem 0.55rem;
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
	.err {
		margin: 0 0 1rem;
		font-size: 0.74rem;
		color: var(--paper);
		background: var(--accent);
		padding: 0.4rem 0.6rem;
	}
	.muted {
		color: var(--ink-soft);
		font-size: 0.8rem;
		padding: 1.5rem;
		text-align: center;
	}
	table {
		width: 100%;
		border-collapse: collapse;
		font-size: 0.88rem;
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
	tbody td {
		padding: 0.6rem 0.9rem;
		border-bottom: 1px solid var(--line);
		vertical-align: top;
	}
	.name {
		font-weight: 600;
	}
	.desc {
		color: var(--ink-2);
		max-width: 40ch;
	}
	.date {
		color: var(--ink-soft);
		font-size: 0.72rem;
		white-space: nowrap;
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

	/* modal */
	.overlay {
		position: fixed;
		inset: 0;
		z-index: 100;
		background: color-mix(in srgb, var(--ink) 45%, transparent);
		display: flex;
		align-items: flex-start;
		justify-content: center;
		padding: 8vh 1rem;
		overflow-y: auto;
	}
	.modal {
		background: var(--card);
		border: 1.5px solid var(--ink);
		box-shadow: 8px 8px 0 var(--ink);
		width: 100%;
		max-width: 460px;
		padding: 1.4rem;
		display: flex;
		flex-direction: column;
		gap: 0.9rem;
	}
	.modal h2 {
		margin: 0;
		font-size: 0.74rem;
		letter-spacing: 0.16em;
		text-transform: uppercase;
		color: var(--ink-soft);
	}
	label {
		display: flex;
		flex-direction: column;
		gap: 0.3rem;
	}
	label span {
		font-size: 0.62rem;
		letter-spacing: 0.1em;
		text-transform: uppercase;
		color: var(--ink-soft);
	}
	input,
	textarea {
		font-family: var(--font-body);
		font-size: 0.9rem;
		color: var(--ink);
		background: var(--paper);
		border: 1.5px solid var(--line-strong);
		padding: 0.5rem 0.6rem;
		resize: vertical;
	}
	input:focus,
	textarea:focus {
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
		cursor: default;
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
</style>
