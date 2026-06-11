/**
 * Prepare a MongoDB URI for use. Only strips wrapping quotes — do NOT
 * percent-encode the password (Atlas passwords may contain literal special chars).
 */
export function sanitizeMongoUri(uri) {
	if (!uri) return uri;
	return uri.trim().replace(/^["']|["']$/g, '');
}
