import fs from 'fs';
import path from 'path';
import { getDb } from '$lib/db';

/** @type {import('./$types').RequestHandler} */
export async function POST({ request }) {
  try {
    const product = await request.json();
    
    let savedToDb = false;
    try {
      // Insert the product record into MongoDB
      const db = await getDb();
      const productsCollection = db.collection('products');
      await productsCollection.insertOne(product);
      savedToDb = true;
    } catch (dbError) {
      console.warn('Failed to save product to MongoDB, falling back to local products.json:', dbError.message);
    }
    
    // Always sync to products.json for local development fallback
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
        throw new Error('Could not save product to database or local file fallback: ' + fileError.message);
      }
    }
    
    return new Response(JSON.stringify({ success: true, product }), {
      headers: { 'Content-Type': 'application/json' }
    });
  } catch (error) {
    return new Response(JSON.stringify({ success: false, error: error.message }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' }
    });
  }
}
