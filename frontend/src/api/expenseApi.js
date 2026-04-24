/**
 * Centralised API client.
 *
 * VITE_API_BASE_URL:
 *   - Dev with Vite proxy: leave empty — proxy handles /expenses → localhost:8080
 *   - Production: set to deployed backend URL in Vercel env vars
 */
const BASE = import.meta.env.VITE_API_BASE_URL ?? '';

/**
 * Parses an API error response into a user-facing message.
 * Reads ApiError.errors[] for field-level messages, falls back to ApiError.message.
 */
async function parseError(response) {
  try {
    const body = await response.json();
    if (Array.isArray(body.errors) && body.errors.length > 0) {
      return body.errors.join('\n');
    }
    return body.message ?? `Request failed (HTTP ${response.status})`;
  } catch {
    return `Request failed (HTTP ${response.status})`;
  }
}

/**
 * POST /expenses
 *
 * @param {object} data - { amount, category, description, date }
 * @param {string} idempotencyKey - stable UUID for this submission; reused on retry
 */
export async function createExpense(data, idempotencyKey) {
  const response = await fetch(`${BASE}/expenses`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
    },
    body: JSON.stringify(data),
  });

  if (!response.ok) {
    throw new Error(await parseError(response));
  }

  return response.json();
}

/**
 * GET /expenses
 *
 * @param {{ category?: string, sort?: string }} params
 */
export async function fetchExpenses({ category, sort } = {}) {
  const qs = new URLSearchParams();
  if (category) qs.set('category', category);
  if (sort) qs.set('sort', sort);

  const url = `${BASE}/expenses${qs.toString() ? `?${qs}` : ''}`;
  const response = await fetch(url);

  if (!response.ok) {
    throw new Error(await parseError(response));
  }

  return response.json();
}

/**
 * GET /expenses/summary
 */
export async function fetchSummary() {
  const response = await fetch(`${BASE}/expenses/summary`);

  if (!response.ok) {
    throw new Error(await parseError(response));
  }

  return response.json();
}
