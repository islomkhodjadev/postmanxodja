import { useState, useEffect, useRef } from 'react';
import { executeRequest } from '../services/api';
import VariableInput from './VariableInput';
import JsonTreeEditor from './JsonTreeEditor';
import AuthorizationPanel from './AuthorizationPanel';
import { SaveButton } from './ui/save-button';
import { parseCurl, generateCurl } from '../utils/curlParser';
import type { ExecuteRequest, ExecuteResponse, Environment, RequestTab, BodyType, FormDataItem, SentRequest, Authorization } from '../types';

interface Props {
    environments: Environment[];
    onResponse: (response: ExecuteResponse, sentRequest: SentRequest) => void;
    initialMethod?: string;
    initialUrl?: string;
    initialHeaders?: Record<string, string>;
    initialBody?: string;
    initialQueryParams?: Record<string, string>;
    initialName?: string;
    initialEnvId?: number;
    onUpdate?: (updates: Partial<RequestTab>) => void;
    onEnvironmentChange?: (envId: number | undefined) => void;
    hasCollectionSource?: boolean;
    onSaveToCollection?: () => Promise<void> | void;
}

export default function RequestBuilder({
                                           environments,
                                           onResponse,
                                           initialMethod = 'GET',
                                           initialUrl = '',
                                           initialHeaders = {},
                                           initialBody = '',
                                           initialQueryParams = {},
                                           initialName = 'Untitled',
                                           initialEnvId,
                                           onUpdate,
                                           onEnvironmentChange,
                                           onSaveToCollection,
                                       }: Props) {
    const [method, setMethod] = useState(initialMethod);
    const [url, setUrl] = useState(initialUrl);
    const [headers, setHeaders] = useState<Array<{ key: string; value: string }>>(() =>
        Object.entries(initialHeaders).map(([key, value]) => ({ key, value }))
    );
    const [body, setBody] = useState(initialBody);
    const [bodyType, setBodyType] = useState<BodyType>('raw');
    const [formData, setFormData] = useState<FormDataItem[]>([]);
    const [queryParams, setQueryParams] = useState<Array<{ key: string; value: string }>>(() =>
        Object.entries(initialQueryParams).map(([key, value]) => ({ key, value }))
    );
    const [selectedEnvId, setSelectedEnvId] = useState<number | undefined>(initialEnvId);
    const [loading, setLoading] = useState(false);
    const [activeSection, setActiveSection] = useState<'params' | 'headers' | 'body' | 'auth'>('params');
    const [curlCopied, setCurlCopied] = useState(false);
    const [bodyViewMode, setBodyViewMode] = useState<'raw' | 'tree'>('raw');
    const [auth, setAuth] = useState<Authorization | undefined>();

    // Track if this is the first render to skip initial onUpdate call
    const isInitialMount = useRef(true);
    const onUpdateRef = useRef(onUpdate);
    onUpdateRef.current = onUpdate;

    // Ref to prevent infinite loops when syncing URL and query params
    const isUpdatingFromParams = useRef(false);

    // Ref to prevent infinite loops when syncing auth and headers
    const isSyncingAuth = useRef(false);

    // Helper to generate auth from headers
    const getAuthFromHeaders = (currentHeaders: Array<{ key: string; value: string }>): Authorization | undefined => {
        const authHeader = currentHeaders.find(h => h.key.toLowerCase() === 'authorization')?.value;
        if (!authHeader) {
            // Check for other types of auth headers
            const digestHeader = currentHeaders.find(h => h.key.toLowerCase() === 'x-digest-auth')?.value;
            if (digestHeader) {
                try {
                    return { type: 'digest', digest: JSON.parse(digestHeader) };
                } catch { /* ignore */ }
            }

            const apiKeyHeader = currentHeaders.find(h => h.key.toLowerCase() === 'x-api-key')?.value;
            if (apiKeyHeader) {
                return { type: 'apikey', apikey: { key: 'X-API-Key', value: apiKeyHeader, addTo: 'header' } };
            }

            return undefined;
        }

        if (authHeader.startsWith('Basic ')) {
            try {
                const credentials = atob(authHeader.substring(6));
                const [username, password] = credentials.split(':');
                return { type: 'basic', basic: { username, password } };
            } catch { /* ignore */ }
        }

        if (authHeader.startsWith('Bearer ')) {
            const token = authHeader.substring(7);
            return { type: 'bearer', bearer: { token } };
        }

        // Try to detect JWT
        if (authHeader.split('.').length === 3) {
            return { type: 'jwt', jwt: { token: authHeader } };
        }

        return undefined;
    };

    // Helper to apply authorization to headers
    const applyAuthToHeaders = (currentHeaders: Record<string, string>): Record<string, string> => {
        const headersWithAuth = { ...currentHeaders };

        if (!auth || auth.type === 'noauth' || auth.type === 'inherit') {
            return headersWithAuth;
        }

        if (auth.type === 'basic' && auth.basic?.username) {
            const credentials = btoa(`${auth.basic.username}:${auth.basic.password || ''}`);
            headersWithAuth['Authorization'] = `Basic ${credentials}`;
        }

        if (auth.type === 'bearer' && auth.bearer?.token) {
            headersWithAuth['Authorization'] = `Bearer ${auth.bearer.token}`;
        }

        if (auth.type === 'jwt' && auth.jwt?.token) {
            headersWithAuth['Authorization'] = `Bearer ${auth.jwt.token}`;
        }

        if (auth.type === 'digest' && auth.digest?.username) {
            // Digest auth is typically handled by the backend
            // For now, we'll store digest params as a header to be processed
            headersWithAuth['X-Digest-Auth'] = JSON.stringify(auth.digest);
        }

        if (auth.type === 'oauth1' && auth.oauth1?.accessToken) {
            // OAuth 1.0 requires complex signing - typically handled by backend
            headersWithAuth['X-OAuth1-Auth'] = JSON.stringify(auth.oauth1);
        }

        if (auth.type === 'oauth2' && auth.oauth2?.accessToken) {
            const tokenType = auth.oauth2.tokenType || 'Bearer';
            headersWithAuth['Authorization'] = `${tokenType} ${auth.oauth2.accessToken}`;
        }

        if (auth.type === 'hawk' && auth.hawk?.authId) {
            // Hawk auth requires complex header generation - typically handled by backend
            headersWithAuth['X-Hawk-Auth'] = JSON.stringify(auth.hawk);
        }

        if (auth.type === 'awssig' && auth.awssig?.accessKey) {
            // AWS Signature requires complex signing - typically handled by backend
            headersWithAuth['X-AWS-Signature'] = JSON.stringify(auth.awssig);
        }

        if (auth.type === 'ntlm' && auth.ntlm?.username) {
            // NTLM requires complex negotiation - typically handled by backend
            headersWithAuth['X-NTLM-Auth'] = JSON.stringify(auth.ntlm);
        }

        if (auth.type === 'apikey' && auth.apikey?.key && auth.apikey?.value) {
            if (auth.apikey.addTo === 'header') {
                headersWithAuth[auth.apikey.key] = auth.apikey.value;
            }
        }

        if (auth.type === 'akamai' && auth.akamai?.clientToken) {
            // Akamai EdgeGrid requires complex header generation - typically handled by backend
            headersWithAuth['X-Akamai-Auth'] = JSON.stringify(auth.akamai);
        }

        if (auth.type === 'asap' && auth.asap?.issuer) {
            // ASAP requires JWT generation - typically handled by backend
            headersWithAuth['X-ASAP-Auth'] = JSON.stringify(auth.asap);
        }

        return headersWithAuth;
    };

    // Helper to parse query params from URL
    const parseQueryParamsFromUrl = (urlString: string): Array<{ key: string; value: string }> => {
        try {
            const urlObj = new URL(urlString.startsWith('http') ? urlString : `http://${urlString}`);
            const params: Array<{ key: string; value: string }> = [];
            urlObj.searchParams.forEach((value, key) => {
                params.push({ key, value });
            });
            return params;
        } catch {
            // Try to parse query string manually if URL is invalid
            const queryIndex = urlString.indexOf('?');
            if (queryIndex === -1) return [];
            const queryString = urlString.slice(queryIndex + 1);
            const params: Array<{ key: string; value: string }> = [];
            queryString.split('&').forEach(part => {
                const [key, value = ''] = part.split('=');
                if (key) {
                    params.push({ key: decodeURIComponent(key), value: decodeURIComponent(value) });
                }
            });
            return params;
        }
    };

    // Helper to build URL with query params
    const buildUrlWithParams = (baseUrl: string, params: Array<{ key: string; value: string }>): string => {
        // Remove existing query params from URL
        let cleanUrl = baseUrl;
        const queryIndex = baseUrl.indexOf('?');
        if (queryIndex !== -1) {
            cleanUrl = baseUrl.slice(0, queryIndex);
        }

        // Build new query string from params
        const validParams = params.filter(p => p.key);
        if (validParams.length === 0) return cleanUrl;

        const queryString = validParams
            .map(p => `${encodeURIComponent(p.key)}=${encodeURIComponent(p.value)}`)
            .join('&');
        return `${cleanUrl}?${queryString}`;
    };


    // Helper to notify parent of changes
    const notifyUpdate = (updates: {
        method?: string;
        url?: string;
        headers?: Array<{ key: string; value: string }>;
        body?: string;
        queryParams?: Array<{ key: string; value: string }>;
        auth?: Authorization;
    }) => {
        if (isInitialMount.current) return;
        if (!onUpdateRef.current) return;

        const currentHeaders = updates.headers ?? headers;
        const currentParams = updates.queryParams ?? queryParams;
        const currentUrl = updates.url ?? url;

        const result: Partial<RequestTab> = {
            method: updates.method ?? method,
            url: currentUrl,
            headers: Object.fromEntries(currentHeaders.filter(h => h.key).map(h => [h.key, h.value])),
            body: updates.body ?? body,
            queryParams: Object.fromEntries(currentParams.filter(q => q.key).map(q => [q.key, q.value])),
            auth: updates.auth !== undefined ? updates.auth : auth,
        };

        // Only auto-generate tab name for fresh "Untitled" tabs when the URL changes.
        // Existing requests (from collections, imports, or user renames) keep their name.
        if (updates.url !== undefined && initialName === 'Untitled') {
            let name = 'Untitled';
            if (currentUrl) {
                try {
                    const urlObj = new URL(currentUrl.startsWith('http') ? currentUrl : `http://${currentUrl}`);
                    name = urlObj.pathname === '/' ? urlObj.hostname : urlObj.pathname.split('/').pop() || urlObj.hostname;
                } catch {
                    const parts = currentUrl.split('/');
                    name = parts[parts.length - 1] || parts[parts.length - 2] || 'Untitled';
                }
            }
            result.name = name;
        }

        onUpdateRef.current(result);
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
            const headersObj = Object.fromEntries(headers.filter(h => h.key).map(h => [h.key.trim(), h.value]));
            const headersWithAuth = applyAuthToHeaders(headersObj);

            // Handle API key in query params if needed
            let finalUrl = url;
            let finalQueryParams = { ...Object.fromEntries(queryParams.filter(q => q.key).map(q => [q.key.trim(), q.value])) };

            if (auth?.type === 'apikey' && auth.apikey?.key && auth.apikey?.value && auth.apikey?.addTo === 'query') {
                finalQueryParams[auth.apikey.key] = auth.apikey.value;
            }

            // url already contains query params (synced by buildUrlWithParams),
            // so pass empty query_params to avoid duplication
            const request: ExecuteRequest = {
                method,
                url: finalUrl,
                headers: headersWithAuth,
                body: bodyType === 'raw' ? body : '',
                body_type: bodyType,
                form_data: bodyType === 'form-data' ? formData.filter(f => f.key) : undefined,
                query_params: {},
                environment_id: selectedEnvId
            };

            const response = await executeRequest(request);
            console.log('Response received:', response);

            // Debug: Log the body value
            console.log('Body state value:', body);
            console.log('Body type:', bodyType);
            console.log('Body to send:', bodyType === 'raw' ? body : '');

            // Create sent request info for Swagger-like display
            // Always capture the body for raw type, don't check if empty
            const sentRequest: SentRequest = {
                method,
                url,
                headers: headersWithAuth,
                body: body, // Always pass the body, let the viewer handle display
                bodyType,
                queryParams: finalQueryParams,
                timestamp: Date.now(),
            };

            console.log('Sent request object:', sentRequest);

            onResponse(response, sentRequest);
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
        newHeaders[index] = { ...newHeaders[index], [field]: value };
        setHeaders(newHeaders);
        notifyUpdate({ headers: newHeaders });

        // Sync to auth if this looks like an authorization header
        if (!isSyncingAuth.current) {
            const detectedAuth = getAuthFromHeaders(newHeaders);
            if (detectedAuth) {
                isSyncingAuth.current = true;
                setAuth(detectedAuth);
                // Important: use the NEW headers here
                notifyUpdate({ headers: newHeaders, auth: detectedAuth });
                isSyncingAuth.current = false;
            }
        }
    };
    const removeHeader = (index: number) => {
        const newHeaders = headers.filter((_, i) => i !== index);
        setHeaders(newHeaders);
        notifyUpdate({ headers: newHeaders });

        // If we removed the auth header, maybe reset auth?
        if (!isSyncingAuth.current) {
            const detectedAuth = getAuthFromHeaders(newHeaders);
            if (!detectedAuth && auth && auth.type !== 'noauth' && auth.type !== 'inherit') {
                isSyncingAuth.current = true;
                setAuth({ type: 'noauth' });
                notifyUpdate({ headers: newHeaders, auth: { type: 'noauth' } });
                isSyncingAuth.current = false;
            }
        }
    };

    const addQueryParam = () => {
        const newParams = [...queryParams, { key: '', value: '' }];
        setQueryParams(newParams);
        // Update URL with new params
        isUpdatingFromParams.current = true;
        const newUrl = buildUrlWithParams(url, newParams);
        setUrl(newUrl);
        notifyUpdate({ queryParams: newParams, url: newUrl });
        isUpdatingFromParams.current = false;
    };
    const updateQueryParam = (index: number, field: 'key' | 'value', value: string) => {
        const newParams = [...queryParams];
        newParams[index][field] = value;
        setQueryParams(newParams);
        // Update URL with new params
        isUpdatingFromParams.current = true;
        const newUrl = buildUrlWithParams(url, newParams);
        setUrl(newUrl);
        notifyUpdate({ queryParams: newParams, url: newUrl });
        isUpdatingFromParams.current = false;
    };
    const removeQueryParam = (index: number) => {
        const newParams = queryParams.filter((_, i) => i !== index);
        setQueryParams(newParams);
        // Update URL with remaining params
        isUpdatingFromParams.current = true;
        const newUrl = buildUrlWithParams(url, newParams);
        setUrl(newUrl);
        notifyUpdate({ queryParams: newParams, url: newUrl });
        isUpdatingFromParams.current = false;
    };

    // Form data management
    const addFormDataItem = () => {
        setFormData([...formData, { key: '', value: '', type: 'text' }]);
    };

    const updateFormDataItem = (index: number, field: keyof FormDataItem, value: string | File) => {
        const newFormData = [...formData];
        if (field === 'file' && value instanceof File) {
            newFormData[index] = { ...newFormData[index], file: value };
        } else if (field === 'type') {
            newFormData[index] = { ...newFormData[index], type: value as 'text' | 'file', file: undefined };
        } else {
            newFormData[index] = { ...newFormData[index], [field]: value as string };
        }
        setFormData(newFormData);
    };

    const removeFormDataItem = (index: number) => {
        setFormData(formData.filter((_, i) => i !== index));
    };

    return (
        <div className="p-2 md:p-3 h-full overflow-auto">
            {selectedEnvId && (
                <div className="p-3 mb-4 bg-primary/10 border-l-4 border-primary rounded-r-lg">
                    <p className="text-sm text-primary">
                        Use <code className="bg-card px-2 py-0.5 rounded text-xs font-mono">{'{{variableName}}'}</code> to insert environment variables in URL, headers, params, or body
                    </p>
                </div>
            )}
            <div className="mb-2 md:mb-3 space-y-2 md:space-y-0 md:flex md:gap-2 md:items-center">
                <div className="flex gap-2 items-center md:flex-1 md:min-w-0">
                    <select
                        value={method}
                        onChange={(e) => {
                            setMethod(e.target.value);
                            notifyUpdate({ method: e.target.value });
                        }}
                        className="border border-border rounded px-2 py-1.5 text-sm font-medium focus:ring-2 focus:ring-ring focus:border-ring outline-none bg-card text-foreground shadow-sm flex-shrink-0"
                    >
                        <option>GET</option>
                        <option>POST</option>
                        <option>PUT</option>
                        <option>DELETE</option>
                        <option>PATCH</option>
                    </select>

                    <div className="flex-1 min-w-0">
                        <VariableInput
                            value={url}
                            onChange={(newUrl) => {
                                // Detect pasted cURL command
                                const trimmed = newUrl.trim();
                                if (/^curl\s/i.test(trimmed)) {
                                    try {
                                        const parsed = parseCurl(trimmed);
                                        setUrl(parsed.url);
                                        setMethod(parsed.method);
                                        // Always reset body/bodyType — if curl has no body, clear it
                                        setBody(parsed.body || '');
                                        setBodyType(parsed.body ? 'raw' : 'none');
                                        // Always reset headers — if curl has no headers, clear them
                                        const parsedHeaders = Object.entries(parsed.headers).map(([key, value]) => ({ key, value }));
                                        setHeaders(parsedHeaders);
                                        const parsedParams = parseQueryParamsFromUrl(parsed.url);
                                        setQueryParams(parsedParams.length > 0 ? parsedParams : []);
                                        notifyUpdate({
                                            method: parsed.method,
                                            url: parsed.url,
                                            headers: parsedHeaders,
                                            body: parsed.body || '',
                                            queryParams: parsedParams,
                                        });
                                        return;
                                    } catch {
                                        // Not a valid curl — fall through to normal URL handling
                                    }
                                }

                                setUrl(newUrl);
                                // Parse query params from URL and update params list
                                if (!isUpdatingFromParams.current) {
                                    const parsedParams = parseQueryParamsFromUrl(newUrl);
                                    // Merge with existing params that have keys not in URL
                                    // This preserves params with empty keys being edited
                                    const existingEmptyKeyParams = queryParams.filter(p => !p.key);
                                    const newParams = [...parsedParams, ...existingEmptyKeyParams];
                                    setQueryParams(newParams.length > 0 ? newParams : []);
                                    notifyUpdate({ url: newUrl, queryParams: newParams });
                                } else {
                                    notifyUpdate({ url: newUrl });
                                }
                            }}
                            placeholder="Enter request URL or paste cURL"
                            environments={environments}
                            selectedEnvId={selectedEnvId}
                            className="shadow-sm"
                        />
                    </div>
                </div>

                <div className="flex gap-2 items-center flex-wrap min-w-0">
                    <select
                        value={selectedEnvId || ''}
                        onChange={(e) => {
                            const newEnvId = e.target.value ? Number(e.target.value) : undefined;
                            setSelectedEnvId(newEnvId);
                            onEnvironmentChange?.(newEnvId);
                        }}
                        className="border border-border rounded-lg px-2 sm:px-3 py-2 text-sm focus:ring-2 focus:ring-ring focus:border-ring outline-none bg-card text-foreground shadow-sm flex-1 md:flex-initial min-w-0 max-w-[140px] sm:max-w-none"
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
              px-4 md:px-6 py-2 rounded-lg shadow-sm font-medium text-sm transition-colors duration-150
              ${loading
                            ? 'bg-muted text-muted-foreground cursor-not-allowed'
                            : 'bg-primary hover:bg-primary/90 text-primary-foreground'
                        }
            `}
                    >
                        {loading ? 'Sending...' : 'Send'}
                    </button>
                    <button
                        onClick={() => {
                            const headersObj = Object.fromEntries(headers.filter(h => h.key).map(h => [h.key.trim(), h.value]));
                            const curl = generateCurl({
                                method,
                                url,
                                headers: headersObj,
                                body: bodyType === 'raw' ? body : undefined,
                            });
                            navigator.clipboard.writeText(curl);
                            setCurlCopied(true);
                            setTimeout(() => setCurlCopied(false), 2000);
                        }}
                        className="px-3 md:px-4 py-2 rounded-lg shadow-sm font-medium text-sm transition-colors duration-150 bg-muted-foreground hover:bg-muted-foreground/80 text-background"
                        title="Copy as cURL"
                    >
                        {curlCopied ? 'Copied!' : 'cURL'}
                    </button>
                    <SaveButton
                        text={{ idle: "Save", saving: "Saving...", saved: "Saved!" }}
                        onSave={async () => {
                            notifyUpdate({});
                            if (onSaveToCollection) {
                                await onSaveToCollection();
                            }
                        }}
                    />
                </div>
            </div>

            {/* Tab Bar */}
            <div className="bg-card rounded-lg border border-border shadow-sm overflow-hidden">
                <div className="flex border-b border-border">
                    <button
                        onClick={() => setActiveSection('params')}
                        className={`relative px-3 py-1.5 text-xs font-medium transition-colors ${
                            activeSection === 'params'
                                ? 'text-primary'
                                : 'text-muted-foreground hover:text-foreground'
                        }`}
                    >
            <span className="flex items-center gap-1.5">
              Params
                {queryParams.filter(p => p.key).length > 0 && (
                    <span className="text-[10px] min-w-[18px] h-[18px] flex items-center justify-center rounded-full bg-primary/10 text-primary font-semibold">
                  {queryParams.filter(p => p.key).length}
                </span>
                )}
            </span>
                        {activeSection === 'params' && (
                            <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-primary" />
                        )}
                    </button>
                    <button
                        onClick={() => setActiveSection('headers')}
                        className={`relative px-3 py-1.5 text-xs font-medium transition-colors ${
                            activeSection === 'headers'
                                ? 'text-primary'
                                : 'text-muted-foreground hover:text-foreground'
                        }`}
                    >
            <span className="flex items-center gap-1.5">
              Headers
                {headers.filter(h => h.key).length > 0 && (
                    <span className="text-[10px] min-w-[18px] h-[18px] flex items-center justify-center rounded-full bg-primary/10 text-primary font-semibold">
                  {headers.filter(h => h.key).length}
                </span>
                )}
            </span>
                        {activeSection === 'headers' && (
                            <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-primary" />
                        )}
                    </button>
                    <button
                        onClick={() => setActiveSection('body')}
                        className={`relative px-3 py-1.5 text-xs font-medium transition-colors ${
                            activeSection === 'body'
                                ? 'text-primary'
                                : 'text-muted-foreground hover:text-foreground'
                        }`}
                    >
            <span className="flex items-center gap-1.5">
              Body
                {bodyType !== 'none' && (
                    <span className={`w-2 h-2 rounded-full ${
                        bodyType === 'raw' && body ? 'bg-primary' : 'bg-muted-foreground'
                    }`} />
                )}
            </span>
                        {activeSection === 'body' && (
                            <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-primary" />
                        )}
                    </button>
                    <button
                        onClick={() => setActiveSection('auth')}
                        className={`relative px-3 py-1.5 text-xs font-medium transition-colors ${
                            activeSection === 'auth'
                                ? 'text-primary'
                                : 'text-muted-foreground hover:text-foreground'
                        }`}
                    >
            <span className="flex items-center gap-1.5">
              Authorization
                {auth && auth.type !== 'noauth' && auth.type !== 'inherit' && (
                    <span className="w-2 h-2 rounded-full bg-primary" />
                )}
            </span>
                        {activeSection === 'auth' && (
                            <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-primary" />
                        )}
                    </button>
                </div>

                {/* Tab Content */}
                <div className="p-2.5">
                    {/* Params Tab */}
                    {activeSection === 'params' && (
                        <div>
                            {queryParams.map((param, index) => (
                                <div key={index} className="flex flex-col gap-1 mb-3 md:flex-row md:gap-3 md:mb-2">
                                    <div className="w-full md:flex-1">
                                        <label htmlFor={`param-key-${index}`} className="sr-only">Parameter key</label>
                                        <input
                                            id={`param-key-${index}`}
                                            type="text"
                                            value={param.key}
                                            onChange={(e) => updateQueryParam(index, 'key', e.target.value)}
                                            placeholder="Key"
                                            className="w-full border border-border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-ring focus:border-ring outline-none bg-card text-foreground"
                                        />
                                    </div>
                                    <div className="flex gap-2 md:flex-1 md:min-w-0">
                                        <div className="flex-1 min-w-0">
                                            <VariableInput
                                                value={param.value}
                                                onChange={(value) => updateQueryParam(index, 'value', value)}
                                                placeholder="Value"
                                                environments={environments}
                                                selectedEnvId={selectedEnvId}
                                            />
                                        </div>
                                        <button
                                            onClick={() => removeQueryParam(index)}
                                            className="p-2 md:px-3 md:py-2 bg-destructive hover:bg-destructive/90 text-destructive-foreground rounded-lg text-sm transition-colors duration-150 shrink-0"
                                        >
                                            <svg className="w-4 h-4 md:hidden" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                                            </svg>
                                            <span className="hidden md:inline">Remove</span>
                                        </button>
                                    </div>
                                </div>
                            ))}
                            <button
                                onClick={addQueryParam}
                                className="mt-2 px-4 py-2 bg-primary hover:bg-primary/90 text-primary-foreground rounded-lg text-sm shadow-sm transition-colors duration-150"
                            >
                                Add Param
                            </button>
                        </div>
                    )}

                    {/* Headers Tab */}
                    {activeSection === 'headers' && (
                        <div>
                            {headers.map((header, index) => (
                                <div key={index} className="flex flex-col gap-1 mb-3 md:flex-row md:gap-3 md:mb-2">
                                    <div className="w-full md:flex-1">
                                        <label htmlFor={`header-key-${index}`} className="sr-only">Header key</label>
                                        <input
                                            id={`header-key-${index}`}
                                            type="text"
                                            value={header.key}
                                            onChange={(e) => updateHeader(index, 'key', e.target.value)}
                                            placeholder="Key"
                                            className="w-full border border-border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-ring focus:border-ring outline-none bg-card text-foreground"
                                        />
                                    </div>
                                    <div className="flex gap-2 md:flex-1 md:min-w-0">
                                        <div className="flex-1 min-w-0">
                                            <VariableInput
                                                value={header.value}
                                                onChange={(value) => updateHeader(index, 'value', value)}
                                                placeholder="Value"
                                                environments={environments}
                                                selectedEnvId={selectedEnvId}
                                            />
                                        </div>
                                        <button
                                            onClick={() => removeHeader(index)}
                                            className="p-2 md:px-3 md:py-2 bg-destructive hover:bg-destructive/90 text-destructive-foreground rounded-lg text-sm transition-colors duration-150 shrink-0"
                                        >
                                            <svg className="w-4 h-4 md:hidden" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                                            </svg>
                                            <span className="hidden md:inline">Remove</span>
                                        </button>
                                    </div>
                                </div>
                            ))}
                            <button
                                onClick={addHeader}
                                className="mt-2 px-4 py-2 bg-primary hover:bg-primary/90 text-primary-foreground rounded-lg text-sm shadow-sm transition-colors duration-150"
                            >
                                Add Header
                            </button>
                        </div>
                    )}

                    {/* Body Tab */}
                    {activeSection === 'body' && (
                        <div>
                            <div className="flex flex-wrap gap-1 bg-muted rounded-lg p-1 mb-3 w-fit">
                                <button
                                    onClick={() => setBodyType('none')}
                                    className={`px-3 py-1 text-xs font-medium rounded-md transition-colors ${
                                        bodyType === 'none' ? 'bg-card text-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground'
                                    }`}
                                >
                                    none
                                </button>
                                <button
                                    onClick={() => setBodyType('raw')}
                                    className={`px-3 py-1 text-xs font-medium rounded-md transition-colors ${
                                        bodyType === 'raw' ? 'bg-card text-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground'
                                    }`}
                                >
                                    raw
                                </button>
                                <button
                                    onClick={() => setBodyType('form-data')}
                                    className={`px-3 py-1 text-xs font-medium rounded-md transition-colors ${
                                        bodyType === 'form-data' ? 'bg-card text-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground'
                                    }`}
                                >
                                    form-data
                                </button>
                                <button
                                    onClick={() => setBodyType('x-www-form-urlencoded')}
                                    className={`px-3 py-1 text-xs font-medium rounded-md transition-colors ${
                                        bodyType === 'x-www-form-urlencoded' ? 'bg-card text-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground'
                                    }`}
                                >
                                    x-www-form-urlencoded
                                </button>
                            </div>

                            {bodyType === 'none' && (
                                <p className="text-muted-foreground text-sm italic">This request does not have a body</p>
                            )}

                            {bodyType === 'raw' && (
                                <div>
                                    <div className="flex justify-end mb-2 gap-2">
                                        <div className="flex bg-muted rounded-md p-0.5">
                                            <button
                                                onClick={() => setBodyViewMode('raw')}
                                                className={`px-2.5 py-1 text-xs font-medium rounded transition-colors ${
                                                    bodyViewMode === 'raw'
                                                        ? 'bg-card text-foreground shadow-sm'
                                                        : 'text-muted-foreground hover:text-foreground'
                                                }`}
                                            >
                                                Raw
                                            </button>
                                            <button
                                                onClick={() => setBodyViewMode('tree')}
                                                className={`px-2.5 py-1 text-xs font-medium rounded transition-colors ${
                                                    bodyViewMode === 'tree'
                                                        ? 'bg-card text-foreground shadow-sm'
                                                        : 'text-muted-foreground hover:text-foreground'
                                                }`}
                                            >
                                                Tree
                                            </button>
                                        </div>
                                        {bodyViewMode === 'raw' && (
                                            <button
                                                onClick={() => {
                                                    try {
                                                        const placeholders: string[] = [];
                                                        const safeBody = body.replace(/\{\{([^}]+)\}\}/g, (m) => {
                                                            const idx = placeholders.length;
                                                            placeholders.push(m);
                                                            return `"__VAR_${idx}__"`;
                                                        });

                                                        const parsed = JSON.parse(safeBody);
                                                        let formatted = JSON.stringify(parsed, null, 2);

                                                        placeholders.forEach((original, idx) => {
                                                            formatted = formatted.replace(`"__VAR_${idx}__"`, original);
                                                        });

                                                        setBody(formatted);
                                                        notifyUpdate({ body: formatted });
                                                    } catch {
                                                        // not valid JSON — ignore
                                                    }
                                                }}
                                                className="px-3 py-1 text-xs font-medium rounded-md bg-muted text-foreground hover:bg-accent transition-colors"
                                            >
                                                Beautify JSON
                                            </button>
                                        )}
                                    </div>
                                    {bodyViewMode === 'raw' ? (
                                        <VariableInput
                                            value={body}
                                            onChange={(value) => {
                                                setBody(value);
                                                notifyUpdate({ body: value });
                                            }}
                                            placeholder="Request body (JSON, text, etc.)"
                                            environments={environments}
                                            selectedEnvId={selectedEnvId}
                                            multiline
                                            jsonHighlight
                                        />
                                    ) : (
                                        <JsonTreeEditor
                                            value={body}
                                            onChange={(value) => {
                                                setBody(value);
                                                notifyUpdate({ body: value });
                                            }}
                                        />
                                    )}
                                </div>
                            )}

                            {bodyType === 'form-data' && (
                                <div>
                                    {formData.map((item, index) => (
                                        <div key={index} className="flex flex-col gap-1 mb-3 md:flex-row md:gap-3 md:mb-2 md:items-center">
                                            <div className="flex gap-2">
                                                <div className="flex-1 md:w-auto">
                                                    <label htmlFor={`formdata-key-${index}`} className="sr-only">Field key</label>
                                                    <input
                                                        id={`formdata-key-${index}`}
                                                        type="text"
                                                        value={item.key}
                                                        onChange={(e) => updateFormDataItem(index, 'key', e.target.value)}
                                                        placeholder="Key"
                                                        className="w-full border border-border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-ring focus:border-ring outline-none bg-card text-foreground"
                                                    />
                                                </div>
                                                <select
                                                    value={item.type}
                                                    onChange={(e) => updateFormDataItem(index, 'type', e.target.value)}
                                                    className="border border-border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-ring focus:border-ring outline-none bg-card text-foreground"
                                                >
                                                    <option value="text">Text</option>
                                                    <option value="file">File</option>
                                                </select>
                                            </div>
                                            <div className="flex gap-2 md:flex-1 md:min-w-0">
                                                {item.type === 'text' ? (
                                                    <div className="flex-1 min-w-0">
                                                        <VariableInput
                                                            value={item.value}
                                                            onChange={(value) => updateFormDataItem(index, 'value', value)}
                                                            placeholder="Value"
                                                            environments={environments}
                                                            selectedEnvId={selectedEnvId}
                                                        />
                                                    </div>
                                                ) : (
                                                    <div className="flex-1 min-w-0">
                                                        <label className="flex items-center gap-2 cursor-pointer border border-border rounded-lg px-3 py-2 text-sm hover:bg-accent transition-colors text-foreground">
                                                            <svg className="w-4 h-4 text-muted-foreground" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                                                            </svg>
                                                            <span className="text-muted-foreground truncate">
                                {item.file ? item.file.name : 'Choose file...'}
                              </span>
                                                            <input
                                                                type="file"
                                                                onChange={(e) => {
                                                                    const file = e.target.files?.[0];
                                                                    if (file) updateFormDataItem(index, 'file', file);
                                                                }}
                                                                className="hidden"
                                                            />
                                                        </label>
                                                    </div>
                                                )}
                                                <button
                                                    onClick={() => removeFormDataItem(index)}
                                                    className="p-2 md:px-3 md:py-2 bg-destructive hover:bg-destructive/90 text-destructive-foreground rounded-lg text-sm transition-colors duration-150 shrink-0"
                                                >
                                                    <svg className="w-4 h-4 md:hidden" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                                                    </svg>
                                                    <span className="hidden md:inline">Remove</span>
                                                </button>
                                            </div>
                                        </div>
                                    ))}
                                    <button
                                        onClick={addFormDataItem}
                                        className="mt-2 px-4 py-2 bg-primary hover:bg-primary/90 text-primary-foreground rounded-lg text-sm shadow-sm transition-colors duration-150"
                                    >
                                        Add Field
                                    </button>
                                </div>
                            )}

                            {bodyType === 'x-www-form-urlencoded' && (
                                <div>
                                    {formData.map((item, index) => (
                                        <div key={index} className="flex flex-col gap-1 mb-3 md:flex-row md:gap-3 md:mb-2">
                                            <div className="w-full md:flex-1">
                                                <label htmlFor={`urlencoded-key-${index}`} className="sr-only">Field key</label>
                                                <input
                                                    id={`urlencoded-key-${index}`}
                                                    type="text"
                                                    value={item.key}
                                                    onChange={(e) => updateFormDataItem(index, 'key', e.target.value)}
                                                    placeholder="Key"
                                                    className="w-full border border-border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-ring focus:border-ring outline-none bg-card text-foreground"
                                                />
                                            </div>
                                            <div className="flex gap-2 md:flex-1 md:min-w-0">
                                                <div className="flex-1 min-w-0">
                                                    <VariableInput
                                                        value={item.value}
                                                        onChange={(value) => updateFormDataItem(index, 'value', value)}
                                                        placeholder="Value"
                                                        environments={environments}
                                                        selectedEnvId={selectedEnvId}
                                                    />
                                                </div>
                                                <button
                                                    onClick={() => removeFormDataItem(index)}
                                                    className="p-2 md:px-3 md:py-2 bg-destructive hover:bg-destructive/90 text-destructive-foreground rounded-lg text-sm transition-colors duration-150 shrink-0"
                                                >
                                                    <svg className="w-4 h-4 md:hidden" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                                                    </svg>
                                                    <span className="hidden md:inline">Remove</span>
                                                </button>
                                            </div>
                                        </div>
                                    ))}
                                    <button
                                        onClick={addFormDataItem}
                                        className="mt-2 px-4 py-2 bg-primary hover:bg-primary/90 text-primary-foreground rounded-lg text-sm shadow-sm transition-colors duration-150"
                                    >
                                        Add Field
                                    </button>
                                </div>
                            )}
                        </div>
                    )}

                    {/* Authorization Tab */}
                    {activeSection === 'auth' && (
                        <AuthorizationPanel
                            auth={auth}
                            onAuthChange={(newAuth) => {
                                setAuth(newAuth);
                                notifyUpdate({ auth: newAuth });

                                // Sync to headers
                                if (!isSyncingAuth.current) {
                                    isSyncingAuth.current = true;
                                    const newHeaders = [...headers];

                                    // Keys that are managed by Authorization
                                    const authKeys = [
                                        'Authorization',
                                        'X-Digest-Auth',
                                        'X-OAuth1-Auth',
                                        'X-Hawk-Auth',
                                        'X-AWS-Signature',
                                        'X-NTLM-Auth',
                                        'X-Akamai-Auth',
                                        'X-ASAP-Auth'
                                    ];

                                    // If it's a new apikey, we need to add its key to managed keys
                                    if (newAuth?.type === 'apikey' && newAuth.apikey?.key) {
                                        authKeys.push(newAuth.apikey.key);
                                    }

                                    // Remove existing auth headers
                                    let filteredHeaders = newHeaders.filter(h => !authKeys.some(ak => ak.toLowerCase() === h.key.toLowerCase()));

                                    if (newAuth && newAuth.type !== 'noauth' && newAuth.type !== 'inherit') {
                                        // Generate headers for the NEW auth, not the state which might not be updated yet
                                        const tempHeadersWithAuth: Record<string, string> = {};

                                        if (newAuth.type === 'basic' && newAuth.basic?.username) {
                                            const credentials = btoa(`${newAuth.basic.username}:${newAuth.basic.password || ''}`);
                                            tempHeadersWithAuth['Authorization'] = `Basic ${credentials}`;
                                        } else if (newAuth.type === 'bearer' && newAuth.bearer?.token) {
                                            tempHeadersWithAuth['Authorization'] = `Bearer ${newAuth.bearer.token}`;
                                        } else if (newAuth.type === 'jwt' && newAuth.jwt?.token) {
                                            tempHeadersWithAuth['Authorization'] = `Bearer ${newAuth.jwt.token}`;
                                        } else if (newAuth.type === 'digest' && newAuth.digest?.username) {
                                            tempHeadersWithAuth['X-Digest-Auth'] = JSON.stringify(newAuth.digest);
                                        } else if (newAuth.type === 'oauth1' && newAuth.oauth1?.accessToken) {
                                            tempHeadersWithAuth['X-OAuth1-Auth'] = JSON.stringify(newAuth.oauth1);
                                        } else if (newAuth.type === 'oauth2' && newAuth.oauth2?.accessToken) {
                                            const tokenType = newAuth.oauth2.tokenType || 'Bearer';
                                            tempHeadersWithAuth['Authorization'] = `${tokenType} ${newAuth.oauth2.accessToken}`;
                                        } else if (newAuth.type === 'hawk' && newAuth.hawk?.authId) {
                                            tempHeadersWithAuth['X-Hawk-Auth'] = JSON.stringify(newAuth.hawk);
                                        } else if (newAuth.type === 'awssig' && newAuth.awssig?.accessKey) {
                                            tempHeadersWithAuth['X-AWS-Signature'] = JSON.stringify(newAuth.awssig);
                                        } else if (newAuth.type === 'ntlm' && newAuth.ntlm?.username) {
                                            tempHeadersWithAuth['X-NTLM-Auth'] = JSON.stringify(newAuth.ntlm);
                                        } else if (newAuth.type === 'apikey' && newAuth.apikey?.key && newAuth.apikey?.value && newAuth.apikey?.addTo === 'header') {
                                            tempHeadersWithAuth[newAuth.apikey.key] = newAuth.apikey.value;
                                        } else if (newAuth.type === 'akamai' && newAuth.akamai?.clientToken) {
                                            tempHeadersWithAuth['X-Akamai-Auth'] = JSON.stringify(newAuth.akamai);
                                        } else if (newAuth.type === 'asap' && newAuth.asap?.issuer) {
                                            tempHeadersWithAuth['X-ASAP-Auth'] = JSON.stringify(newAuth.asap);
                                        }

                                        // Add new auth headers
                                        Object.entries(tempHeadersWithAuth).forEach(([key, value]) => {
                                            filteredHeaders.push({ key, value });
                                        });
                                    }

                                    setHeaders(filteredHeaders);
                                    notifyUpdate({ auth: newAuth, headers: filteredHeaders });
                                    isSyncingAuth.current = false;
                                }
                            }}
                            environments={environments}
                            selectedEnvId={selectedEnvId}
                        />
                    )}
                </div>
            </div>
        </div>
    );
}
