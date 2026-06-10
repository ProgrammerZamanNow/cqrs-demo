<script lang="ts">
	import { goto } from '$app/navigation';

	$effect(() => {
		goto('/admin/products', { replaceState: true });
	});
</script>

<p style="padding:2rem;font-family:var(--font-mono);color:var(--ink-soft)">mengalihkan ke /admin/products…</p>
