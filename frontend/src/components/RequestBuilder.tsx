import { useState, useEffect, useRef } from 'react';
import { executeRequest } from '../services/api';
import type { ExecuteRequest, ExecuteResponse, Environment, RequestTab } from '../types';

interface Props {
  environments: Environment[];
  onResponse: (response: ExecuteResponse) => void;
  initialMethod?: string;
  initialUrl?: string;
  initialHeaders?: Record<string, string>;
  initialBody?: string;
  initialQueryParams?: Record<string, string>;
  onUpdate?: (updates: Partial<RequestTab>) => void;
  hasCollectionSource?: boolean;
  onSaveToCollection?: () => void;
}

export default function RequestBuilder({
  environments,
  onResponse,
  initialMethod = 'GET',
  initialUrl = '',
  initialHeaders = {},
  initialBody = '',
  initialQueryParams = {},
  onUpdate,
  hasCollectionSource = false,
  onSaveToCollection,
}: Props) {
  const [method, setMethod] = useState(initialMethod);
  const [url, setUrl] = useState(initialUrl);
  const [headers, setHeaders] = useState<Array<{ key: string; value: string }>>(() =>
    Object.entries(initialHeaders).map(([key, value]) => ({ key, value }))
  );
  const [body, setBody] = useState(initialBody);
  const [queryParams, setQueryParams] = useState<Array<{ key: string; value: string }>>(() =>
    Object.entries(initialQueryParams).map(([key, value]) => ({ key, value }))
  );
  const [selectedEnvId, setSelectedEnvId] = useState<number | undefined>();
  const [loading, setLoading] = useState(false);

  // Track if this is the first render to skip initial onUpdate call
  const isInitialMount = useRef(true);
  const onUpdateRef = useRef(onUpdate);
  onUpdateRef.current = onUpdate;

  // Helper to notify parent of changes
  const notifyUpdate = (updates: {
    method?: string;
    url?: string;
    headers?: Array<{ key: string; value: string }>;
    body?: string;
    queryParams?: Array<{ key: string; value: string }>;
  }) => {
    if (isInitialMount.current) return;
    if (!onUpdateRef.current) return;

    const currentHeaders = updates.headers ?? headers;
    const currentParams = updates.queryParams ?? queryParams;
    const currentUrl = updates.url ?? url;

    // Generate tab name from URL
    let name = 'Untitled';
    if (currentUrl) {
      try {
        const urlObj = new URL(currentUrl.startsWith('http') ? currentUrl : `http://${currentUrl}`);
        name = urlObj.pathname === '/' ? urlObj.hostname : urlObj.pathname.split('/').pop() || urlObj.hostname;
      } catch {
        // If URL parsing fails, use last part of the string
        const parts = currentUrl.split('/');
        name = parts[parts.length - 1] || parts[parts.length - 2] || 'Untitled';
      }
    }

    onUpdateRef.current({
      name,
      method: updates.method ?? method,
      url: currentUrl,
      headers: Object.fromEntries(currentHeaders.filter(h => h.key).map(h => [h.key, h.value])),
      body: updates.body ?? body,
      queryParams: Object.fromEntries(currentParams.filter(q => q.key).map(q => [q.key, q.value])),
    });
  };

  // Mark initial mount as complete after first render
  useEffect(() => {
    isInitialMount.current = false;
  }, []);

  // Note: We don't sync from selectedRequest here because:
  // 1. Initial values come from initial* props
  // 2. key={activeTabId} causes component remount on tab change
  // 3. Syncing here would overwrite user input on every render

  const handleSend = async () => {
    if (!url || url.trim() === '') {
      alert('Please enter a URL');
      return;
    }

    setLoading(true);

    try {
      const request: ExecuteRequest = {
        method,
        url,
        headers: Object.fromEntries(headers.filter(h => h.key).map(h => [h.key, h.value])),
        body,
        query_params: Object.fromEntries(queryParams.filter(q => q.key).map(q => [q.key, q.value])),
        environment_id: selectedEnvId
      };

      const response = await executeRequest(request);
      console.log('Response received:', response);
      onResponse(response);
    } catch (err: any) {
      console.error('Request failed:', err);
      console.error('Error details:', err.response);
      alert(err.response?.data?.error || err.message || 'Request failed');
    } finally {
      setLoading(false);
    }
  };

  const addHeader = () => {
    const newHeaders = [...headers, { key: '', value: '' }];
    setHeaders(newHeaders);
    notifyUpdate({ headers: newHeaders });
  };
  const updateHeader = (index: number, field: 'key' | 'value', value: string) => {
    const newHeaders = [...headers];
    newHeaders[index][field] = value;
    setHeaders(newHeaders);
    notifyUpdate({ headers: newHeaders });
  };
  const removeHeader = (index: number) => {
    const newHeaders = headers.filter((_, i) => i !== index);
    setHeaders(newHeaders);
    notifyUpdate({ headers: newHeaders });
  };

  const addQueryParam = () => {
    const newParams = [...queryParams, { key: '', value: '' }];
    setQueryParams(newParams);
    notifyUpdate({ queryParams: newParams });
  };
  const updateQueryParam = (index: number, field: 'key' | 'value', value: string) => {
    const newParams = [...queryParams];
    newParams[index][field] = value;
    setQueryParams(newParams);
    notifyUpdate({ queryParams: newParams });
  };
  const removeQueryParam = (index: number) => {
    const newParams = queryParams.filter((_, i) => i !== index);
    setQueryParams(newParams);
    notifyUpdate({ queryParams: newParams });
  };

  return (
    <div className="p-6 h-full overflow-auto">
      {selectedEnvId && (
        <div className="p-3 mb-4 bg-blue-50 border-l-4 border-blue-500 rounded-r-lg">
          <p className="text-sm text-blue-800">
            Use <code className="bg-white px-2 py-0.5 rounded text-xs font-mono">{'{{variableName}}'}</code> to insert environment variables in URL, headers, params, or body
          </p>
        </div>
      )}
      <div className="mb-6 flex gap-3 items-center">
        <select
          value={method}
          onChange={(e) => {
            setMethod(e.target.value);
            notifyUpdate({ method: e.target.value });
          }}
          className="border border-gray-300 rounded-lg px-3 py-2 text-sm font-medium focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none bg-white shadow-sm"
        >
          <option>GET</option>
          <option>POST</option>
          <option>PUT</option>
          <option>DELETE</option>
          <option>PATCH</option>
        </select>

        <input
          type="text"
          value={url}
          onChange={(e) => {
            setUrl(e.target.value);
            notifyUpdate({ url: e.target.value });
          }}
          placeholder="Enter request URL"
          className="flex-1 border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none shadow-sm"
        />

        <select
          value={selectedEnvId || ''}
          onChange={(e) => setSelectedEnvId(e.target.value ? Number(e.target.value) : undefined)}
          className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none bg-white shadow-sm"
        >
          <option value="">No Environment</option>
          {environments.map(env => (
            <option key={env.id} value={env.id}>{env.name}</option>
          ))}
        </select>

        <button
          onClick={handleSend}
          disabled={loading}
          className={`
            px-6 py-2 rounded-lg shadow-sm font-medium text-sm transition-colors duration-150
            ${loading
              ? 'bg-gray-400 text-white cursor-not-allowed'
              : 'bg-green-500 hover:bg-green-600 text-white'
            }
          `}
        >
          {loading ? 'Sending...' : 'Send'}
        </button>
        <button
          onClick={() => notifyUpdate({})}
          className="px-4 py-2 rounded-lg shadow-sm font-medium text-sm transition-colors duration-150 bg-blue-500 hover:bg-blue-600 text-white"
          title="Save tab (auto-saves after 1 second)"
        >
          Save
        </button>
        {hasCollectionSource && onSaveToCollection && (
          <button
            onClick={onSaveToCollection}
            className="px-4 py-2 rounded-lg shadow-sm font-medium text-sm transition-colors duration-150 bg-purple-500 hover:bg-purple-600 text-white"
            title="Save changes back to the collection"
          >
            Save to Collection
          </button>
        )}
      </div>

      <div className="mb-6 bg-white rounded-lg border border-gray-200 p-4 shadow-sm">
        <h4 className="font-semibold text-gray-800 mb-3 text-sm">Query Parameters</h4>
        {queryParams.map((param, index) => (
          <div key={index} className="flex gap-3 mb-2">
            <input
              type="text"
              value={param.key}
              onChange={(e) => updateQueryParam(index, 'key', e.target.value)}
              placeholder="Key"
              className="flex-1 border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none"
            />
            <input
              type="text"
              value={param.value}
              onChange={(e) => updateQueryParam(index, 'value', e.target.value)}
              placeholder="Value"
              className="flex-1 border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none"
            />
            <button
              onClick={() => removeQueryParam(index)}
              className="px-3 py-2 bg-red-500 hover:bg-red-600 text-white rounded-lg text-sm transition-colors duration-150"
            >
              Remove
            </button>
          </div>
        ))}
        <button
          onClick={addQueryParam}
          className="mt-2 px-4 py-2 bg-blue-500 hover:bg-blue-600 text-white rounded-lg text-sm shadow-sm transition-colors duration-150"
        >
          Add Param
        </button>
      </div>

      <div className="mb-6 bg-white rounded-lg border border-gray-200 p-4 shadow-sm">
        <h4 className="font-semibold text-gray-800 mb-3 text-sm">Headers</h4>
        {headers.map((header, index) => (
          <div key={index} className="flex gap-3 mb-2">
            <input
              type="text"
              value={header.key}
              onChange={(e) => updateHeader(index, 'key', e.target.value)}
              placeholder="Key"
              className="flex-1 border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none"
            />
            <input
              type="text"
              value={header.value}
              onChange={(e) => updateHeader(index, 'value', e.target.value)}
              placeholder="Value"
              className="flex-1 border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none"
            />
            <button
              onClick={() => removeHeader(index)}
              className="px-3 py-2 bg-red-500 hover:bg-red-600 text-white rounded-lg text-sm transition-colors duration-150"
            >
              Remove
            </button>
          </div>
        ))}
        <button
          onClick={addHeader}
          className="mt-2 px-4 py-2 bg-blue-500 hover:bg-blue-600 text-white rounded-lg text-sm shadow-sm transition-colors duration-150"
        >
          Add Header
        </button>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-4 shadow-sm">
        <h4 className="font-semibold text-gray-800 mb-3 text-sm">Body</h4>
        <textarea
          value={body}
          onChange={(e) => {
            setBody(e.target.value);
            notifyUpdate({ body: e.target.value });
          }}
          placeholder="Request body (JSON, text, etc.)"
          className="w-full min-h-[150px] max-h-[400px] border border-gray-300 rounded-lg px-3 py-2 text-sm font-mono focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none resize-y overflow-auto"
        />
      </div>
    </div>
  );
}
