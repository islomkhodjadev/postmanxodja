export interface ParsedCurl {
  method: string;
  url: string;
  headers: Record<string, string>;
  body: string;
}

export function parseCurl(curlCommand: string): ParsedCurl {
  let method = 'GET';
  let url = '';
  const headers: Record<string, string> = {};
  let body = '';

  // Remove line breaks and extra spaces
  let normalized = curlCommand
    .replace(/\\\r?\n/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();

  // Remove 'curl' prefix
  normalized = normalized.replace(/^curl\s+/i, '');

  // Extract method (-X or --request)
  const methodMatch = normalized.match(/(?:-X|--request)\s+['"]?(\w+)['"]?/i);
  if (methodMatch) {
    method = methodMatch[1].toUpperCase();
    normalized = normalized.replace(methodMatch[0], '');
  }

  // Extract headers (-H or --header)
  const headerRegex = /(?:-H|--header)\s+(['"])(.*?)\1/g;
  let headerMatch;
  while ((headerMatch = headerRegex.exec(normalized)) !== null) {
    const header = headerMatch[2];
    const colonIndex = header.indexOf(':');
    if (colonIndex > 0) {
      const key = header.substring(0, colonIndex).trim();
      const value = header.substring(colonIndex + 1).trim();
      headers[key] = value;
    }
    normalized = normalized.replace(headerMatch[0], '');
  }

  // Extract body (-d, --data, --data-raw, --data-binary)
  const dataMatch = normalized.match(/(?:-d|--data|--data-raw|--data-binary)\s+(['"])(.*?)\1/);
  if (dataMatch) {
    body = dataMatch[2]
      .replace(/\\"/g, '"')
      .replace(/\\'/g, "'")
      .replace(/\\\\/g, '\\');
    normalized = normalized.replace(dataMatch[0], '');
  }

  // Extract URL (remaining non-flag argument)
  normalized = normalized.trim();

  // Try to find URL in quotes first
  const quotedUrlMatch = normalized.match(/(['"])(.*?)\1/);
  if (quotedUrlMatch) {
    url = quotedUrlMatch[2];
  } else {
    // Otherwise, take the first word-like sequence
    const urlMatch = normalized.match(/([^\s]+)/);
    if (urlMatch) {
      url = urlMatch[1];
    }
  }

  // Clean up URL (remove trailing flags if any)
  url = url.replace(/\s+.*$/, '').trim();

  // If Content-Type not specified but we have body, try to detect type
  if (body && !headers['Content-Type'] && !headers['content-type']) {
    try {
      JSON.parse(body);
      headers['Content-Type'] = 'application/json';
    } catch {
      headers['Content-Type'] = 'application/x-www-form-urlencoded';
    }
  }

  return {
    method,
    url,
    headers,
    body,
  };
}

function shellEscape(str: string): string {
  if (/^[a-zA-Z0-9._\-/:@=&?%+,;~]+$/.test(str)) return str;
  return "'" + str.replace(/'/g, "'\\''") + "'";
}

export function generateCurl(opts: {
  method: string;
  url: string;
  headers?: Record<string, string>;
  body?: string;
  queryParams?: Record<string, string>;
}): string {
  const parts: string[] = ['curl'];

  // Method (skip -X for GET since it's the default)
  if (opts.method && opts.method !== 'GET') {
    parts.push(`-X ${opts.method}`);
  }

  // Build full URL with query params
  let fullUrl = opts.url;
  if (opts.queryParams && Object.keys(opts.queryParams).length > 0) {
    try {
      const urlObj = new URL(fullUrl.startsWith('http') ? fullUrl : `http://${fullUrl}`);
      for (const [key, value] of Object.entries(opts.queryParams)) {
        if (key) urlObj.searchParams.append(key, value);
      }
      fullUrl = urlObj.toString();
    } catch {
      // fallback: append manually
      const params = new URLSearchParams(opts.queryParams).toString();
      fullUrl += (fullUrl.includes('?') ? '&' : '?') + params;
    }
  }
  parts.push(shellEscape(fullUrl));

  // Headers
  if (opts.headers) {
    for (const [key, value] of Object.entries(opts.headers)) {
      if (!key.trim()) continue;
      parts.push(`-H ${shellEscape(`${key}: ${value}`)}`);
    }
  }

  // Body
  if (opts.body) {
    parts.push(`--data-raw ${shellEscape(opts.body)}`);
  }

  return parts.join(' \\\n  ');
}
