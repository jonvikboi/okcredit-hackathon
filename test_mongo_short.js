import { readFileSync } from 'fs';
import { MongoClient } from 'mongodb';
import { sanitizeMongoUri } from './src/lib/mongoUri.js';

function loadMongoUri() {
  try {
    const env = readFileSync('.env', 'utf8');
    const match = env.match(/^MONGODB_URI=(.+)$/m);
    if (match) return sanitizeMongoUri(match[1]);
  } catch (_) {
    // ignore
  }
  return sanitizeMongoUri(process.env.MONGODB_URI || '');
}

const uri = loadMongoUri();

async function run() {
  if (!uri) {
    console.error('No MONGODB_URI found in .env or environment');
    process.exit(1);
  }

  const client = new MongoClient(uri, { serverSelectionTimeoutMS: 15000 });
  try {
    await client.connect();
    const db = client.db('okcredit_inventory');
    const collection = db.collection('products');
    const docs = await collection.find({}).limit(5).toArray();
    console.log(`Connected. Found ${docs.length} sample product(s).`);
    docs.forEach((doc, index) => {
      console.log(`\nProduct ${index + 1}:`);
      console.log(JSON.stringify(doc, null, 2));
    });
  } finally {
    await client.close();
  }
}

run().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
