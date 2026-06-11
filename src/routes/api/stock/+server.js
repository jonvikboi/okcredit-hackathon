import { getDb } from '$lib/db';
import { normalizeProducts } from '$lib/normalizeProduct';

/** Lightweight GET-only stock endpoint for Android sync fallback. */
export async function GET() {
	try {
		const db = await getDb();
		const productsFromDb = await db.collection('products').find({}).toArray();
		const products = normalizeProducts(productsFromDb);

		return new Response(JSON.stringify({ success: true, products }), {
			headers: { 'Content-Type': 'application/json' }
		});
	} catch (error) {
		return new Response(JSON.stringify({ success: false, error: error.message, products: [] }), {
			status: 500,
			headers: { 'Content-Type': 'application/json' }
		});
	}
}
