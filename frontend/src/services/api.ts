import axios from 'axios';
import type { Collection, ExecuteRequest, ExecuteResponse, Environment } from '../types';
import { executeRequestDirect, isBackendReachable } from './offlineExecutor';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor to add auth header
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor for handling auth errors
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      // Token expired or invalid
      localStorage.removeItem('access_token');
      localStorage.removeItem('refresh_token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// Collection APIs (team-scoped)
export const createCollection = async (teamId: number, name: string, description?: string): Promise<Collection> => {
  const response = await api.post(`/teams/${teamId}/collections`, { name, description: description || '' });
  return response.data;
};

export const importCollection = async (teamId: number, collectionJSON: string): Promise<Collection> => {
  const response = await api.post(`/teams/${teamId}/collections/import`, { collection_json: collectionJSON });
  return response.data;
};

export const getCollections = async (teamId: number): Promise<Collection[]> => {
  const response = await api.get(`/teams/${teamId}/collections`);
  return response.data;
};

export const getCollection = async (teamId: number, id: number): Promise<any> => {
  const response = await api.get(`/teams/${teamId}/collections/${id}`);
  return response.data;
};

export const deleteCollection = async (teamId: number, id: number): Promise<void> => {
  await api.delete(`/teams/${teamId}/collections/${id}`);
};

export const updateCollection = async (teamId: number, id: number, data: { raw_json?: string; name?: string }): Promise<Collection> => {
  const response = await api.put(`/teams/${teamId}/collections/${id}`, data);
  return response.data;
};

export const exportCollection = async (teamId: number, id: number, collectionName: string): Promise<void> => {
  try {
    const response = await api.get(`/teams/${teamId}/collections/${id}/export`, {
      responseType: 'blob',
    });

    // Create download link
    const blob = new Blob([response.data], { type: 'application/json' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${collectionName}.postman_collection.json`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
  } catch (error) {
    console.error('Export failed:', error);
    throw error;
  }
};

// Request execution
export const executeRequest = async (request: ExecuteRequest): Promise<ExecuteResponse> => {
  // Check if the target is a localhost URL being accessed from a remote origin
  // (e.g. postbaby.uz → localhost:3000). In that case, direct fetch will always
  // fail due to mixed-content / CORS, so we MUST use the backend proxy.
  const isRemoteToLocal = (() => {
    try {
      const targetUrl = request.url.match(/^https?:\/\//i) ? request.url : 'http://' + request.url;
      const target = new URL(targetUrl);
      const tHost = target.hostname;
      const isLocalTarget = tHost === 'localhost' || tHost === '127.0.0.1' || tHost === '::1';
      const pageHost = window.location.hostname;
      const isLocalPage = pageHost === 'localhost' || pageHost === '127.0.0.1' || pageHost === '::1';
      return isLocalTarget && !isLocalPage;
    } catch { return false; }
  })();

  // Try the backend proxy first; fall back to direct browser fetch when
  // the backend is unreachable (offline / local-only mode).
  // But never fall back for remote→localhost requests (CORS will block).
  const backendAvailable = await isBackendReachable(API_BASE_URL);

  if (!backendAvailable && !isRemoteToLocal) {
    console.info('[offline] Backend unreachable — executing request directly from browser');
    return executeRequestDirect(request);
  }

  if (!backendAvailable && isRemoteToLocal) {
    // Backend is down and we can't direct-fetch localhost from a remote origin
    return {
      status: 0,
      status_text: 'Backend Unavailable',
      headers: {},
      body: JSON.stringify({
        error: 'The backend server is unreachable. Requests to localhost/private addresses require the backend proxy. Please make sure the backend is running.',
      }),
      time: 0,
    };
  }

  try {
    // For form-data with files, we need to use multipart/form-data
    if (request.body_type === 'form-data' && request.form_data?.some(item => item.type === 'file' && item.file)) {
      const formData = new FormData();

      // Add request metadata
      formData.append('_request_meta', JSON.stringify({
        method: request.method,
        url: request.url,
        headers: request.headers,
        query_params: request.query_params,
        environment_id: request.environment_id,
        body_type: request.body_type,
      }));

      // Add form data items
      request.form_data?.forEach((item, index) => {
        if (item.type === 'file' && item.file) {
          formData.append(`file_${index}`, item.file, item.file.name);
          formData.append(`file_${index}_key`, item.key);
        } else {
          formData.append(`text_${index}_key`, item.key);
          formData.append(`text_${index}_value`, item.value);
        }
      });

      const response = await api.post('/requests/execute-multipart', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });
      return response.data;
    }

    // For regular requests
    const response = await api.post('/requests/execute', request);
    return response.data;
  } catch (err: any) {
    // If the backend request itself failed (network error, not HTTP error from
    // the target), fall back to direct fetch as a last resort — but only when
    // the target isn't a localhost address accessed from a remote origin.
    if (!err.response && !isRemoteToLocal) {
      console.info('[offline] Backend request failed — falling back to direct fetch');
      return executeRequestDirect(request);
    }
    throw err;
  }
};

// Environment APIs (team-scoped)
export const getEnvironments = async (teamId: number): Promise<Environment[]> => {
  const response = await api.get(`/teams/${teamId}/environments`);
  return response.data;
};

export const createEnvironment = async (teamId: number, environment: Environment): Promise<Environment> => {
  const response = await api.post(`/teams/${teamId}/environments`, environment);
  return response.data;
};

export const updateEnvironment = async (teamId: number, id: number, environment: Environment): Promise<Environment> => {
  const response = await api.put(`/teams/${teamId}/environments/${id}`, environment);
  return response.data;
};

export const deleteEnvironment = async (teamId: number, id: number): Promise<void> => {
  await api.delete(`/teams/${teamId}/environments/${id}`);
};

// API Keys
export interface APIKey {
  id: number;
  team_id: number;
  name: string;
  key?: string; // Only returned on creation
  key_prefix: string;
  permissions: string;
  last_used_at: string | null;
  expires_at: string | null;
  created_at: string;
}

export interface CreateAPIKeyRequest {
  name: string;
  permissions?: string; // read, write, read_write
  expires_in?: number; // Days until expiration
}

export const getAPIKeys = async (teamId: number): Promise<APIKey[]> => {
  const response = await api.get(`/teams/${teamId}/api-keys`);
  return response.data;
};

export const createAPIKey = async (teamId: number, data: CreateAPIKeyRequest): Promise<APIKey> => {
  const response = await api.post(`/teams/${teamId}/api-keys`, data);
  return response.data;
};

export const deleteAPIKey = async (teamId: number, keyId: number): Promise<void> => {
  await api.delete(`/teams/${teamId}/api-keys/${keyId}`);
};

// Saved tabs APIs
export interface SavedTab {
  tab_id: string;
  name: string;
  method: string;
  url: string;
  headers: Record<string, string>;
  body: string;
  query_params: Record<string, string>;
  is_active: boolean;
  sort_order: number;
}

export const getSavedTabs = async (): Promise<SavedTab[]> => {
  const response = await api.get('/tabs');
  return response.data;
};

export const saveTabs = async (tabs: SavedTab[], activeTabId: string): Promise<void> => {
  await api.post('/tabs', { tabs, active_tab_id: activeTabId });
};

export default api;
