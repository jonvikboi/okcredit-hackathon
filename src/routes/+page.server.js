import { getDb } from '$lib/db';
import { normalizeProducts } from '$lib/normalizeProduct';
import productsJson from '$lib/products.json';

/** @type {import('./$types').PageServerLoad} */
export async function load() {
  try {
    const db = await getDb();
    const productsCollection = db.collection('products');

    const productsFromDb = await productsCollection.find({}).toArray();
    const products = normalizeProducts(productsFromDb);

    return {
      products,
      source: 'mongodb'
    };
  } catch (error) {
    console.error('Failed to load products from MongoDB, falling back to bundled products.json:', error);
    return {
      products: normalizeProducts(productsJson),
      source: 'fallback'
    };
  }
}
