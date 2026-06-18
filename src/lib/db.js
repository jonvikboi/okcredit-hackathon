import { MongoClient } from 'mongodb';
import { MONGODB_URI } from '$env/static/private';

const uri = MONGODB_URI;
let client;
let clientPromise;

if (!uri) {
  throw new Error('Please define the MONGODB_URI environment variable inside your .env file or Vercel settings');
}

if (process.env.NODE_ENV === 'development') {
  // In development mode, use a global variable to preserve connection across HMR (Hot Module Replacement) reloads.
  if (!global._mongoClientPromise) {
    client = new MongoClient(uri);
    global._mongoClientPromise = client.connect();
  }
  clientPromise = global._mongoClientPromise;
} else {
  // In production mode (Vercel), create a standard client connection.
  client = new MongoClient(uri);
  clientPromise = client.connect();
}

export async function getDb() {
  const connection = await clientPromise;
  return connection.db('okcredit_inventory');
}
