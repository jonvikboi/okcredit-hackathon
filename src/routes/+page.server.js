import { getDb } from '$lib/db';
import productsJson from '$lib/products.json';

/** @type {import('./$types').PageServerLoad} */
export async function load() {
  try {
    const db = await getDb();
    const productsCollection = db.collection('products');
    
    // Fetch all products from MongoDB
    const productsFromDb = await productsCollection.find({}).toArray();
    
    // MongoDB documents contain an ObjectId instance under '_id'.
    // SvelteKit requires loaded data to be fully JSON serializable, 
    // so we map documents to plain objects without the _id or convert it to a string.
    const products = productsFromDb.map(p => {
      const { _id, ...rest } = p;
      return { ...rest };
    });
    
    return {
      products
    };
  } catch (error) {
    console.error('Failed to load products from MongoDB, falling back to bundled products.json:', error);
    return {
      products: productsJson
    };
  }
}
