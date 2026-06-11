import fs from 'fs';
import path from 'path';
import { getDb } from '$lib/db';
import { normalizeProducts } from '$lib/normalizeProduct';

function jsonResponse(body, status = 200) {
	return new Response(JSON.stringify(body), {
		status,
		headers: { 'Content-Type': 'application/json' }
	});
}

async function listProducts() {
	const db = await getDb();
	const productsCollection = db.collection('products');
	const productsFromDb = await productsCollection.find({}).toArray();
	return normalizeProducts(productsFromDb);
}

/** @type {import('./$types').RequestHandler} */
export async function GET() {
	try {
		const products = await listProducts();
		return jsonResponse({ success: true, products });
	} catch (error) {
		return jsonResponse({ success: false, error: error.message, products: [] }, 500);
	}
}

/** @type {import('./$types').RequestHandler} */
export async function POST({ request }) {
	try {
		const payload = await request.json();

		// Fallback for clients that cannot use GET (older Vercel deploys / strict proxies).
		if (payload?.action === 'list') {
			const products = await listProducts();
			return jsonResponse({ success: true, products });
		}

		const product = payload;
		let savedToDb = false;
		let dbErrorMsg = '';
		try {
			const db = await getDb();
			const productsCollection = db.collection('products');
			await productsCollection.insertOne(product);
			savedToDb = true;
		} catch (dbError) {
			dbErrorMsg = dbError.message;
			console.warn('Failed to save product to MongoDB:', dbError.message);
		}

		if (process.env.NODE_ENV === 'development' || !process.env.VERCEL) {
			try {
				const jsonPath = path.resolve('src/lib/products.json');
				let productsList = [];
				if (fs.existsSync(jsonPath)) {
					const raw = fs.readFileSync(jsonPath, 'utf8');
					productsList = JSON.parse(raw);
				}
				productsList.push(product);
				fs.writeFileSync(jsonPath, JSON.stringify(productsList, null, 2), 'utf8');
			} catch (fileError) {
				console.error('Failed to sync product to products.json:', fileError);
				if (!savedToDb) {
					throw new Error(
						'Database insertion failed (' +
							dbErrorMsg +
							') and local fallback write failed: ' +
							fileError.message
					);
				}
			}
		} else if (!savedToDb) {
			throw new Error(
				'Failed to save product to MongoDB. Please check your Vercel MONGODB_URI environment variable and MongoDB Atlas IP Network Access whitelist. Error: ' +
					dbErrorMsg
			);
		}

		return jsonResponse({ success: true, product });
	} catch (error) {
		return jsonResponse({ success: false, error: error.message }, 500);
	}
}

/** @type {import('./$types').RequestHandler} */
export async function DELETE({ request }) {
	try {
		const { ids } = await request.json();
		if (!ids || !Array.isArray(ids)) {
			return jsonResponse({ success: false, error: 'Invalid product IDs list' }, 400);
		}

		let deletedFromDb = false;
		let dbErrorMsg = '';
		try {
			const db = await getDb();
			const productsCollection = db.collection('products');
			await productsCollection.deleteMany({
				$or: [{ id: { $in: ids } }, { itemCode: { $in: ids } }]
			});
			deletedFromDb = true;
		} catch (dbError) {
			dbErrorMsg = dbError.message;
			console.warn('Failed to delete products from MongoDB:', dbError.message);
		}

		if (process.env.NODE_ENV === 'development' || !process.env.VERCEL) {
			try {
				const jsonPath = path.resolve('src/lib/products.json');
				if (fs.existsSync(jsonPath)) {
					const raw = fs.readFileSync(jsonPath, 'utf8');
					let productsList = JSON.parse(raw);
					productsList = productsList.filter((p) => !ids.includes(p.id));
					fs.writeFileSync(jsonPath, JSON.stringify(productsList, null, 2), 'utf8');
				}
			} catch (fileError) {
				console.error('Failed to sync deleted products to products.json:', fileError);
			}
		}

		if (!deletedFromDb && process.env.VERCEL) {
			throw new Error(
				'Failed to delete products from MongoDB. Check Vercel MONGODB_URI and Atlas Network Access. Error: ' +
					dbErrorMsg
			);
		}

		return jsonResponse({ success: true });
	} catch (error) {
		return jsonResponse({ success: false, error: error.message }, 500);
	}
}
