import { MongoClient } from 'mongodb';
import { MONGODB_URI } from '$env/static/private';
import { sanitizeMongoUri } from '$lib/mongoUri';

const uri = sanitizeMongoUri(MONGODB_URI);
let client;
let clientPromise;

if (!uri) {
  throw new Error('Please define the MONGODB_URI environment variable inside your .env file or Vercel settings');
}

function createClientPromise() {
  const mongoClient = new MongoClient(uri);
  return mongoClient.connect().catch((error) => {
    if (process.env.NODE_ENV === 'development') {
      global._mongoClientPromise = undefined;
    }
    throw error;
  });
}

// Reuse one client across warm serverless invocations (Vercel) and HMR reloads (dev).
if (!global._mongoClientPromise) {
  clientPromise = createClientPromise();
  global._mongoClientPromise = clientPromise;
} else {
  clientPromise = global._mongoClientPromise;
}

export async function getDb() {
  const connection = await clientPromise;
  return connection.db('okcredit_inventory');
}
