import fs from 'fs';
import path from 'path';

/** @type {import('./$types').RequestHandler} */
export async function POST({ request }) {
  try {
    const product = await request.json();
    const jsonPath = path.resolve('src/lib/products.json');
    
    let products = [];
    if (fs.existsSync(jsonPath)) {
      const fileContent = fs.readFileSync(jsonPath, 'utf8');
      products = JSON.parse(fileContent);
    }
    
    // Append the new product
    products.push(product);
    
    // Write back to products.json on disk
    fs.writeFileSync(jsonPath, JSON.stringify(products, null, 2), 'utf8');
    
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
