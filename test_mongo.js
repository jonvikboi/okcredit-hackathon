import { MongoClient } from 'mongodb';

const uri = "mongodb://jonathan:Jackie213!@ac-zr0jraa-shard-00-00.lcgmcnv.mongodb.net:27017,ac-zr0jraa-shard-00-01.lcgmcnv.mongodb.net:27017,ac-zr0jraa-shard-00-02.lcgmcnv.mongodb.net:27017/okcredit_inventory?ssl=true&replicaSet=atlas-63zx6d-shard-0&authSource=admin&appName=Cluster0";

async function run() {
  const client = new MongoClient(uri);
  try {
    await client.connect();
    console.log("Connected successfully to server");
    const db = client.db("okcredit_inventory");
    const collection = db.collection("products");
    const docs = await collection.find({}).toArray();
    console.log("Total products:", docs.length);
    if (docs.length > 0) {
      console.log("First product status:", docs[0].status);
      console.log("First product:", JSON.stringify(docs[0], null, 2));
    }
  } catch (err) {
    console.error("Error connecting or fetching:", err);
  } finally {
    await client.close();
  }
}
run();
