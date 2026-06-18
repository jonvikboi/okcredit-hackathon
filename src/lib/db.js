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

async function seedUsers(db) {
  try {
    const usersCollection = db.collection('users');
    console.log('Ensuring default admin and user credentials are up to date...');

    // Upsert Admin
    await usersCollection.updateOne(
      { username: 'admin' },
      {
        $set: {
          password: 'sunrise@213',
          role: 'admin',
          updatedAt: new Date()
        },
        $setOnInsert: { createdAt: new Date() }
      },
      { upsert: true }
    );

    // Upsert User
    await usersCollection.updateOne(
      { username: 'user' },
      {
        $set: {
          password: 'user123',
          role: 'user',
          updatedAt: new Date()
        },
        $setOnInsert: { createdAt: new Date() }
      },
      { upsert: true }
    );

    console.log('Credentials update/seeding completed successfully.');
  } catch (error) {
    console.error('Error during users seeding:', error);
  }
}

export async function getDb() {
  const connection = await clientPromise;
  const db = connection.db('okcredit_inventory');

  if (!global._usersSeeded) {
    global._usersSeeded = (async () => {
      await seedUsers(db);
      return true;
    })();
  }

  return db;
}
