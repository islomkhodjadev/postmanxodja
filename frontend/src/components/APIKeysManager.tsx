import { useState, useEffect } from 'react';
import { getAPIKeys, createAPIKey, deleteAPIKey, APIKey, CreateAPIKeyRequest } from '../services/api';
import { useTeam } from '../contexts/TeamContext';
import ConfirmModal from './ConfirmModal';

export default function APIKeysManager() {
  const [apiKeys, setApiKeys] = useState<APIKey[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newKey, setNewKey] = useState<APIKey | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<APIKey | null>(null);
  const [formData, setFormData] = useState<CreateAPIKeyRequest>({
    name: '',
    permissions: 'read',
    expires_in: 0,
  });
  const { currentTeam } = useTeam();

  useEffect(() => {
    if (currentTeam) {
      loadAPIKeys();
    }
  }, [currentTeam?.id]);

  const loadAPIKeys = async () => {
    if (!currentTeam) return;
    try {
      setLoading(true);
      const keys = await getAPIKeys(currentTeam.id);
      setApiKeys(keys);
    } catch (err) {
      console.error('Failed to load API keys:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = async () => {
    if (!currentTeam || !formData.name.trim()) return;
    try {
      const key = await createAPIKey(currentTeam.id, {
        name: formData.name.trim(),
        permissions: formData.permissions,
        expires_in: formData.expires_in || undefined,
      });
      setNewKey(key);
      setApiKeys(prev => [...prev, key]);
      setFormData({ name: '', permissions: 'read', expires_in: 0 });
      setShowCreateModal(false);
    } catch (err) {
      console.error('Failed to create API key:', err);
      alert('Failed to create API key');
    }
  };

  const handleDelete = async () => {
    if (!currentTeam || !deleteTarget) return;
    try {
      await deleteAPIKey(currentTeam.id, deleteTarget.id);
      setApiKeys(prev => prev.filter(k => k.id !== deleteTarget.id));
      setDeleteTarget(null);
    } catch (err) {
      console.error('Failed to delete API key:', err);
      alert('Failed to delete API key');
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    alert('Copied to clipboard!');
  };

  const formatDate = (dateStr: string | null) => {
    if (!dateStr) return 'Never';
    return new Date(dateStr).toLocaleDateString();
  };

  const getPermissionBadgeColor = (permissions: string) => {
    switch (permissions) {
      case 'read': return 'bg-green-100 text-green-800';
      case 'write': return 'bg-yellow-100 text-yellow-800';
      case 'read_write': return 'bg-blue-100 text-blue-800';
      default: return 'bg-gray-100 text-gray-800';
    }
  };

  if (!currentTeam) {
    return (
      <div className="p-4 text-gray-500">
        Select a team to manage API keys
      </div>
    );
  }

  return (
    <div className="p-4">
      <div className="flex justify-between items-center mb-4">
        <h2 className="text-lg font-semibold text-gray-800">API Keys</h2>
        <button
          onClick={() => setShowCreateModal(true)}
          className="px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 text-sm"
        >
          Create API Key
        </button>
      </div>

      <p className="text-sm text-gray-600 mb-4">
        Use API keys to access your collections from external services like CI/CD pipelines.
      </p>

      {/* New Key Display */}
      {newKey?.key && (
        <div className="mb-4 p-4 bg-green-50 border border-green-200 rounded-lg">
          <p className="text-sm text-green-800 font-medium mb-2">
            API Key created successfully! Copy it now - you won't be able to see it again.
          </p>
          <div className="flex items-center gap-2">
            <code className="flex-1 p-2 bg-white border rounded text-sm font-mono break-all">
              {newKey.key}
            </code>
            <button
              onClick={() => copyToClipboard(newKey.key!)}
              className="px-3 py-2 bg-green-500 text-white rounded hover:bg-green-600 text-sm"
            >
              Copy
            </button>
          </div>
          <button
            onClick={() => setNewKey(null)}
            className="mt-2 text-sm text-green-700 hover:underline"
          >
            Dismiss
          </button>
        </div>
      )}

      {loading ? (
        <div className="text-center py-8 text-gray-500">Loading...</div>
      ) : apiKeys.length === 0 ? (
        <div className="text-center py-8 text-gray-500">
          No API keys yet. Create one to get started.
        </div>
      ) : (
        <div className="space-y-2">
          {apiKeys.map(key => (
            <div key={key.id} className="p-4 border rounded-lg bg-white hover:bg-gray-50">
              <div className="flex justify-between items-start">
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-medium text-gray-800">{key.name}</span>
                    <span className={`px-2 py-0.5 rounded text-xs ${getPermissionBadgeColor(key.permissions)}`}>
                      {key.permissions}
                    </span>
                  </div>
                  <div className="mt-1 text-sm text-gray-500">
                    <code className="bg-gray-100 px-1 rounded">{key.key_prefix}...</code>
                  </div>
                  <div className="mt-2 text-xs text-gray-400 space-x-4">
                    <span>Created: {formatDate(key.created_at)}</span>
                    <span>Last used: {formatDate(key.last_used_at)}</span>
                    {key.expires_at && <span>Expires: {formatDate(key.expires_at)}</span>}
                  </div>
                </div>
                <button
                  onClick={() => setDeleteTarget(key)}
                  className="px-3 py-1 text-red-500 hover:bg-red-50 rounded text-sm"
                >
                  Delete
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* API Usage Info */}
      <div className="mt-6 border border-gray-200 rounded-xl overflow-hidden">
        <div className="bg-gradient-to-r from-blue-500 to-indigo-600 px-4 py-3">
          <h3 className="font-semibold text-white flex items-center gap-2">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" />
            </svg>
            API Documentation
          </h3>
        </div>

        <div className="p-4 space-y-4 bg-white">
          {/* Base URL */}
          <div>
            <label className="text-xs font-medium text-gray-500 uppercase tracking-wider">Base URL</label>
            <div className="mt-1 flex items-center gap-2">
              <code className="flex-1 px-3 py-2 bg-gray-900 text-green-400 rounded-lg text-sm font-mono">
                https://postbaby.uz/api/v1
              </code>
              <button
                onClick={() => copyToClipboard('https://postbaby.uz/api/v1')}
                className="p-2 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-lg transition-colors"
                title="Copy"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
                </svg>
              </button>
            </div>
          </div>

          {/* Authentication */}
          <div>
            <label className="text-xs font-medium text-gray-500 uppercase tracking-wider">Authentication</label>
            <div className="mt-1 bg-gray-50 rounded-lg p-3 space-y-2">
              <div className="flex items-center gap-2 text-sm">
                <span className="px-2 py-0.5 bg-blue-100 text-blue-700 rounded text-xs font-medium">Header</span>
                <code className="text-gray-700 font-mono">X-API-Key: your_api_key</code>
              </div>
              <div className="text-xs text-gray-500">or</div>
              <div className="flex items-center gap-2 text-sm">
                <span className="px-2 py-0.5 bg-purple-100 text-purple-700 rounded text-xs font-medium">Bearer</span>
                <code className="text-gray-700 font-mono">Authorization: ApiKey your_api_key</code>
              </div>
            </div>
          </div>

          {/* Endpoints */}
          <div>
            <label className="text-xs font-medium text-gray-500 uppercase tracking-wider">Endpoints</label>
            <div className="mt-1 space-y-1">
              <div className="grid grid-cols-[auto_1fr_auto] gap-2 items-center p-2 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors">
                <span className="px-2 py-0.5 bg-green-100 text-green-700 rounded text-xs font-bold w-16 text-center">GET</span>
                <code className="text-sm text-gray-700 font-mono">/collections</code>
                <span className="text-xs text-gray-500">List all</span>
              </div>
              <div className="grid grid-cols-[auto_1fr_auto] gap-2 items-center p-2 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors">
                <span className="px-2 py-0.5 bg-green-100 text-green-700 rounded text-xs font-bold w-16 text-center">GET</span>
                <code className="text-sm text-gray-700 font-mono">/collections/:id</code>
                <span className="text-xs text-gray-500">Get details</span>
              </div>
              <div className="grid grid-cols-[auto_1fr_auto] gap-2 items-center p-2 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors">
                <span className="px-2 py-0.5 bg-green-100 text-green-700 rounded text-xs font-bold w-16 text-center">GET</span>
                <code className="text-sm text-gray-700 font-mono">/collections/:id/raw</code>
                <span className="text-xs text-gray-500">Raw JSON</span>
              </div>
              <div className="grid grid-cols-[auto_1fr_auto] gap-2 items-center p-2 bg-yellow-50 rounded-lg hover:bg-yellow-100 transition-colors">
                <span className="px-2 py-0.5 bg-yellow-100 text-yellow-700 rounded text-xs font-bold w-16 text-center">POST</span>
                <code className="text-sm text-gray-700 font-mono">/collections</code>
                <span className="text-xs text-yellow-600 font-medium">write</span>
              </div>
              <div className="grid grid-cols-[auto_1fr_auto] gap-2 items-center p-2 bg-blue-50 rounded-lg hover:bg-blue-100 transition-colors">
                <span className="px-2 py-0.5 bg-blue-100 text-blue-700 rounded text-xs font-bold w-16 text-center">PUT</span>
                <code className="text-sm text-gray-700 font-mono">/collections/:id</code>
                <span className="text-xs text-blue-600 font-medium">write</span>
              </div>
              <div className="grid grid-cols-[auto_1fr_auto] gap-2 items-center p-2 bg-red-50 rounded-lg hover:bg-red-100 transition-colors">
                <span className="px-2 py-0.5 bg-red-100 text-red-700 rounded text-xs font-bold w-16 text-center">DELETE</span>
                <code className="text-sm text-gray-700 font-mono">/collections/:id</code>
                <span className="text-xs text-red-600 font-medium">write</span>
              </div>
            </div>
          </div>

          {/* Example */}
          <div>
            <label className="text-xs font-medium text-gray-500 uppercase tracking-wider">Example Request</label>
            <pre className="mt-1 bg-gray-900 text-gray-100 p-3 rounded-lg text-xs font-mono overflow-x-auto">
<span className="text-green-400">curl</span> -X GET <span className="text-yellow-300">"https://postbaby.uz/api/v1/collections"</span> \{'\n'}  -H <span className="text-yellow-300">"X-API-Key: pmx_your_api_key"</span>
            </pre>
          </div>
        </div>
      </div>

      {/* Create Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 flex items-center justify-center z-50">
          <div className="fixed inset-0 bg-black/50" onClick={() => setShowCreateModal(false)} />
          <div className="relative bg-white dark:bg-gray-800 rounded-lg p-6 w-full max-w-md mx-4 shadow-xl">
            <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-4">Create API Key</h3>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Name</label>
                <input
                  type="text"
                  value={formData.name}
                  onChange={(e) => setFormData(prev => ({ ...prev, name: e.target.value }))}
                  placeholder="e.g., CI/CD Pipeline"
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Permissions</label>
                <select
                  value={formData.permissions}
                  onChange={(e) => setFormData(prev => ({ ...prev, permissions: e.target.value }))}
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                >
                  <option value="read">Read only</option>
                  <option value="write">Write only</option>
                  <option value="read_write">Read & Write</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Expiration (days)</label>
                <input
                  type="number"
                  value={formData.expires_in || ''}
                  onChange={(e) => setFormData(prev => ({ ...prev, expires_in: parseInt(e.target.value) || 0 }))}
                  placeholder="0 = never expires"
                  min="0"
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                />
                <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">Leave empty or 0 for no expiration</p>
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-6">
              <button
                onClick={() => setShowCreateModal(false)}
                className="px-4 py-2 text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg"
              >
                Cancel
              </button>
              <button
                onClick={handleCreate}
                disabled={!formData.name.trim()}
                className="px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 disabled:opacity-50"
              >
                Create
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      <ConfirmModal
        isOpen={!!deleteTarget}
        title="Delete API Key?"
        message={`Are you sure you want to delete "${deleteTarget?.name}"? Any integrations using this key will stop working.`}
        confirmText="Delete"
        onConfirm={handleDelete}
        onCancel={() => setDeleteTarget(null)}
        variant="danger"
      />
    </div>
  );
}
