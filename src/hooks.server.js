/** @type {import('@sveltejs/kit').Handle} */
export async function handle({ event, resolve }) {
	if (!event.url.pathname.startsWith('/api/')) {
		return resolve(event);
	}

	if (event.request.method === 'OPTIONS') {
		return new Response(null, {
			status: 204,
			headers: corsHeaders()
		});
	}

	const response = await resolve(event);
	for (const [key, value] of Object.entries(corsHeaders())) {
		response.headers.set(key, value);
	}
	return response;
}

function corsHeaders() {
	return {
		'Access-Control-Allow-Origin': '*',
		'Access-Control-Allow-Methods': 'GET, POST, DELETE, OPTIONS',
		'Access-Control-Allow-Headers': 'Content-Type, Authorization'
	};
}
