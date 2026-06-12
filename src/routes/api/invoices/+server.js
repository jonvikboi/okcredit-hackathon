import { getDb } from '$lib/db';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  });
}

/** @type {import('./$types').RequestHandler} */
export async function GET({ url }) {
  try {
    const db = await getDb();
    const query = {};

    const customerName = url.searchParams.get('customerName');
    if (customerName) {
      query.customerName = { $regex: customerName, $options: 'i' };
    }

    const customerPhone = url.searchParams.get('customerPhone');
    if (customerPhone) {
      query.customerPhone = { $regex: customerPhone, $options: 'i' };
    }

    const startDate = url.searchParams.get('startDate');
    const endDate = url.searchParams.get('endDate');
    if (startDate || endDate) {
      query.createdAt = {};
      if (startDate) {
        query.createdAt.$gte = new Date(startDate);
      }
      if (endDate) {
        // Set end date to end of day to make date range filters intuitive
        const end = new Date(endDate);
        end.setHours(23, 59, 59, 999);
        query.createdAt.$lte = end;
      }
    }

    const minAmount = url.searchParams.get('minAmount');
    const maxAmount = url.searchParams.get('maxAmount');
    if (minAmount || maxAmount) {
      query.total = {};
      if (minAmount) {
        query.total.$gte = Number(minAmount);
      }
      if (maxAmount) {
        query.total.$lte = Number(maxAmount);
      }
    }

    const invoices = await db
      .collection('invoice')
      .find(query)
      .sort({ createdAt: -1 })
      .toArray();

    return jsonResponse({ success: true, invoices });
  } catch (error) {
    return jsonResponse({ success: false, error: error.message }, 500);
  }
}

/** @type {import('./$types').RequestHandler} */
export async function POST({ request }) {
  try {
    const db = await getDb();
    const invoice = await request.json();

    // Ensure invoice has a standard createdAt date
    invoice.createdAt = invoice.createdAt ? new Date(invoice.createdAt) : new Date();

    const result = await db.collection('invoice').insertOne(invoice);
    return jsonResponse({ success: true, id: result.insertedId });
  } catch (error) {
    return jsonResponse({ success: false, error: error.message }, 500);
  }
}
