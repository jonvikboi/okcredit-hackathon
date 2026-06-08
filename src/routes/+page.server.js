import fs from 'fs';
import path from 'path';
import { getDb } from '$lib/db';

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
    console.error('Failed to load products from MongoDB, falling back to local products.json:', error);
    
    try {
      const jsonPath = path.resolve('src/lib/products.json');
      if (fs.existsSync(jsonPath)) {
        const raw = fs.readFileSync(jsonPath, 'utf8');
        const products = JSON.parse(raw);
        return {
          products
        };
      }
    } catch (jsonError) {
      console.error('Failed to read local products.json fallback:', jsonError);
    }

    return {
      products: []
    };
  }
}
