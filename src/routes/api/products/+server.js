import fs from 'fs';
import path from 'path';
import { getDb } from '$lib/db';

/** @type {import('./$types').RequestHandler} */
export async function POST({ request }) {
  try {
    const product = await request.json();
    
    let savedToDb = false;
    let dbErrorMsg = '';
    try {
      // Insert the product record into MongoDB
      const db = await getDb();
      const productsCollection = db.collection('products');
      await productsCollection.insertOne(product);
      savedToDb = true;
    } catch (dbError) {
      dbErrorMsg = dbError.message;
      console.warn('Failed to save product to MongoDB:', dbError.message);
    }
    
    // Only attempt local filesystem sync in local development
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
          throw new Error('Database insertion failed (' + dbErrorMsg + ') and local fallback write failed: ' + fileError.message);
        }
      }
    } else {
      // In production (Vercel), we must throw the database error directly if it failed,
      // since filesystem writes are not supported on serverless functions.
      if (!savedToDb) {
        throw new Error('Failed to save product to MongoDB. Please check your Vercel MONGODB_URI environment variable and MongoDB Atlas IP Network Access whitelist. Error: ' + dbErrorMsg);
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
