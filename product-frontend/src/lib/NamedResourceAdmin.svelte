<script lang="ts">
	type Item = {
		id: string;
		name: string;
		description: string | null;
		createdAt: number;
		updatedAt: number;
	};

	let { title, apiPath, noun }: { title: string; apiPath: string; noun: string } = $props();

	let items = $state<Item[]>([]);
	let total = $state(0);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let saving = $state(false);

	let editingId = $state<string | null>(null);
	let name = $state('');
	let description = $state('');

	async function load() {
		loading = true;
		error = null;
		try {
			const res = await fetch(`${apiPath}?size=100&sort=name,asc`);
			const json = await res.json();
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

	function resetForm() {
		editingId = null;
		name = '';
		description = '';
		error = null;
	}

	function startEdit(it: Item) {
		editingId = it.id;
		name = it.name;
		description = it.description ?? '';
		error = null;
	}

	async function save(e: Event) {
		e.preventDefault();
		saving = true;
		error = null;
		const url = editingId ? `${apiPath}/${editingId}` : apiPath;
		try {
			const res = await fetch(url, {
				method: editingId ? 'PUT' : 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ name, description: description.trim() ? description : null })
			});
			const json = await res.json().catch(() => ({}));
			if (res.ok) {
				resetForm();
				await load();
			} else {
				error = json.error ?? `error ${res.status}`;
			}
		} catch {
			error = 'gagal terhubung ke server';
		}
		saving = false;
	}

	async function remove(it: Item) {
		if (!confirm(`Hapus ${noun} "${it.name}"?`)) return;
		error = null;
		try {
			const res = await fetch(`${apiPath}/${it.id}`, { method: 'DELETE' });
			const json = await res.json().catch(() => ({}));
			if (res.ok) {
				if (editingId === it.id) resetForm();
				await load();
			} else {
				error = json.error ?? `error ${res.status}`;
			}
		} catch {
			error = 'gagal terhubung ke server';
		}
	}

	const fmtDate = (ms: number) =>
		new Date(ms).toLocaleDateString('id-ID', { day: '2-digit', month: 'short', year: 'numeric' });
</script>

<div class="head">
	<h1>{title}</h1>
	<span class="count mono">{total} {noun}</span>
</div>

<div class="grid">
	<!-- form -->
	<form class="card form" onsubmit={save}>
		<h2 class="mono">{editingId ? `Edit ${noun}` : `Tambah ${noun}`}</h2>
		{#if error}
			<p class="err mono">{error}</p>
		{/if}
		<label>
			<span class="mono">Nama *</span>
			<input bind:value={name} maxlength="100" required placeholder="Nama {noun}" />
		</label>
		<label>
			<span class="mono">Deskripsi</span>
			<textarea bind:value={description} rows="3" placeholder="Opsional"></textarea>
		</label>
		<div class="actions">
			<button type="submit" class="primary mono" disabled={saving}>
				{saving ? 'menyimpan…' : editingId ? 'Simpan' : 'Tambah'}
			</button>
			{#if editingId}
				<button type="button" class="ghost mono" onclick={resetForm}>Batal</button>
			{/if}
		</div>
	</form>

	<!-- table -->
	<div class="card list">
		{#if loading}
			<p class="muted mono">memuat…</p>
		{:else if items.length === 0}
			<p class="muted mono">Belum ada {noun}.</p>
		{:else}
			<table>
				<thead class="mono">
					<tr><th>Nama</th><th>Deskripsi</th><th>Dibuat</th><th></th></tr>
				</thead>
				<tbody>
					{#each items as it (it.id)}
						<tr class:editing={editingId === it.id}>
							<td class="name">{it.name}</td>
							<td class="desc">{it.description ?? '—'}</td>
							<td class="mono date">{fmtDate(it.createdAt)}</td>
							<td class="row-actions">
								<button class="link" onclick={() => startEdit(it)}>edit</button>
								<button class="link del" onclick={() => remove(it)}>hapus</button>
							</td>
						</tr>
					{/each}
				</tbody>
			</table>
		{/if}
	</div>
</div>

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
	.grid {
		display: grid;
		grid-template-columns: 300px 1fr;
		gap: 1.5rem;
		align-items: start;
	}
	.card {
		background: var(--card);
		border: 1.5px solid var(--ink);
	}
	.form {
		padding: 1.1rem;
		display: flex;
		flex-direction: column;
		gap: 0.8rem;
		position: sticky;
		top: 84px;
	}
	.form h2 {
		margin: 0;
		font-size: 0.72rem;
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
		font-size: 0.64rem;
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
		margin-top: 0.2rem;
	}
	.primary {
		border: 0;
		background: var(--accent);
		color: var(--paper);
		padding: 0.55rem 1rem;
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
		padding: 0.55rem 0.9rem;
		cursor: pointer;
		font-size: 0.72rem;
		letter-spacing: 0.1em;
		text-transform: uppercase;
	}
	.ghost:hover {
		border-color: var(--ink);
		color: var(--ink);
	}
	.err {
		margin: 0;
		font-size: 0.74rem;
		color: var(--paper);
		background: var(--accent);
		padding: 0.4rem 0.6rem;
	}
	.list {
		padding: 0.3rem 0;
		overflow-x: auto;
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
	tr.editing {
		background: color-mix(in srgb, var(--accent) 8%, transparent);
	}
	.name {
		font-weight: 600;
	}
	.desc {
		color: var(--ink-2);
		max-width: 36ch;
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
	@media (max-width: 760px) {
		.grid {
			grid-template-columns: 1fr;
		}
		.form {
			position: static;
		}
	}
</style>
