/**
 * Offline / direct-fetch request executor.
 *
 * When the backend proxy is unreachable (offline mode, or the user is running
 * locally without Docker) this module executes HTTP requests directly from the
 * browser via the Fetch API.
 *
 * Limitations compared to the backend proxy:
 *  - Subject to CORS restrictions of the target server.
 *  - Cannot follow redirects across origins transparently.
 *  - Cannot access response headers blocked by the browser.
 *
 * For localhost targets the browser is typically on the same origin so CORS
 * is rarely an issue.
 */
import type { ExecuteRequest, ExecuteResponse } from '../types';

export async function executeRequestDirect(
  request: ExecuteRequest,
): Promise<ExecuteResponse> {
  const startTime = performance.now();

  // Build URL with query params
  let targetURL = request.url;
  if (!targetURL.match(/^https?:\/\//i)) {
    targetURL = 'http://' + targetURL;
  }

  const urlObj = new URL(targetURL);
  if (request.query_params) {
    for (const [key, value] of Object.entries(request.query_params)) {
      if (key) urlObj.searchParams.append(key, value);
    }
  }

  // Build headers
  const headers = new Headers();
  if (request.headers) {
    for (const [key, value] of Object.entries(request.headers)) {
      if (key) headers.set(key, value);
    }
  }

  // Build body
  let body: BodyInit | undefined;
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    if (request.body_type === 'form-data' && request.form_data) {
      const formData = new FormData();
      for (const item of request.form_data) {
        if (!item.key) continue;
        if (item.type === 'file' && item.file) {
          formData.append(item.key, item.file);
        } else {
          formData.append(item.key, item.value);
        }
      }
      body = formData;
      // Let the browser set Content-Type with boundary
      headers.delete('Content-Type');
    } else if (request.body_type === 'x-www-form-urlencoded' && request.form_data) {
      const params = new URLSearchParams();
      for (const item of request.form_data) {
        if (item.key) params.append(item.key, item.value);
      }
      body = params;
      if (!headers.has('Content-Type')) {
        headers.set('Content-Type', 'application/x-www-form-urlencoded');
      }
    } else if (request.body) {
      body = request.body;
    }
  }

  try {
    const resp = await fetch(urlObj.toString(), {
      method: request.method,
      headers,
      body,
      // Mode 'cors' is the default — will work for localhost and CORS-enabled
      // servers. For same-origin targets it is effectively 'same-origin'.
    });

    const elapsed = Math.round(performance.now() - startTime);

    // Collect accessible response headers
    const respHeaders: Record<string, string> = {};
    resp.headers.forEach((value, key) => {
      respHeaders[key] = value;
    });

    const respBody = await resp.text();

    return {
      status: resp.status,
      status_text: resp.statusText,
      headers: respHeaders,
      body: respBody,
      time: elapsed,
    };
  } catch (err: any) {
    // Network error — still return a structured response so the UI can show it
    const elapsed = Math.round(performance.now() - startTime);

    // Determine a helpful error message
    let errorMessage = err.message || 'Network error';
    if (err.name === 'TypeError' && errorMessage.includes('Failed to fetch')) {
      errorMessage =
        'Failed to fetch — the target server may be unreachable, or CORS is blocking the request. ' +
        'If this is a localhost service, make sure it is running and allows cross-origin requests.';
    }

    return {
      status: 0,
      status_text: 'Network Error',
      headers: {},
      body: JSON.stringify({ error: errorMessage }),
      time: elapsed,
    };
  }
}

/**
 * Check whether the backend API server is reachable.
 * Returns true if the health endpoint responds within the timeout.
 */
export async function isBackendReachable(apiBaseUrl: string): Promise<boolean> {
  try {
    const healthURL = apiBaseUrl.replace(/\/api\/?$/, '') + '/health';
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 3000);
    const resp = await fetch(healthURL, { signal: controller.signal });
    clearTimeout(timeout);
    return resp.ok;
  } catch {
    return false;
  }
}
