/**
 * Normalize a MongoDB product document to the shape expected by the web UI.
 * Supports both legacy web fields (id, weight, makingCharge fraction)
 * and Android/production fields (itemCode, weightGrams, makingChargePercent).
 */
export function normalizeProduct(doc) {
	const id = doc.id ?? doc.itemCode ?? (doc._id ? String(doc._id) : '');
	const weight = Number(doc.weight ?? doc.weightGrams ?? 0);
	let makingCharge = Number(doc.makingCharge ?? doc.makingChargePercent ?? 0);
	if (makingCharge > 1) makingCharge = makingCharge / 100;

	const status = doc.status ?? 'available';

	return {
		id,
		_id: doc._id ? String(doc._id) : id,
		name: doc.name ?? id,
		purity: doc.purity ?? '22K',
		weight,
		makingCharge,
		fixedValue: Number(doc.fixedValue ?? 0),
		category: doc.category ?? 'Custom',
		description: doc.description ?? '',
		image: doc.image ?? doc.imageUrl ?? '',
		status,
		metal: doc.metal ?? 'Gold'
	};
}

export function normalizeProducts(docs) {
	return docs
		.map(normalizeProduct)
		.filter((p) => !p.status || p.status === 'available');
}
