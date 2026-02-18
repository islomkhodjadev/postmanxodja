import { useState } from 'react';
import { parseCurl } from '../utils/curlParser';

interface CurlImportModalProps {
  isOpen: boolean;
  onClose: () => void;
  onImport: (data: { method: string; url: string; headers: Record<string, string>; body: string }) => void;
}

export default function CurlImportModal({ isOpen, onClose, onImport }: CurlImportModalProps) {
  const [curlCommand, setCurlCommand] = useState('');
  const [error, setError] = useState('');

  if (!isOpen) return null;

  const handleImport = () => {
    try {
      setError('');
      const trimmed = curlCommand.trim();

      if (!trimmed) {
        setError('Please paste a cURL command');
        return;
      }

      if (!trimmed.toLowerCase().startsWith('curl')) {
        setError('Invalid cURL command. Must start with "curl"');
        return;
      }

      const parsed = parseCurl(trimmed);

      if (!parsed.url) {
        setError('Could not extract URL from cURL command');
        return;
      }

      onImport(parsed);
      setCurlCommand('');
      onClose();
    } catch (err) {
      setError('Failed to parse cURL command. Please check the format.');
      console.error('cURL parse error:', err);
    }
  };

  const handleClose = () => {
    setCurlCommand('');
    setError('');
    onClose();
  };

  return (
    <div className="fixed inset-0 flex items-center justify-center z-50">
      <div className="fixed inset-0 bg-black/50" onClick={handleClose} />
      <div className="relative bg-white dark:bg-gray-800 rounded-lg shadow-xl w-full max-w-2xl mx-4">
        <div className="p-6">
          <div className="flex justify-between items-center mb-4">
            <h2 className="text-xl font-semibold text-gray-900 dark:text-gray-100">Import from cURL</h2>
            <button
              onClick={handleClose}
              className="text-gray-400 hover:text-gray-600"
            >
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                Paste your cURL command
              </label>
              <textarea
                value={curlCommand}
                onChange={(e) => setCurlCommand(e.target.value)}
                placeholder="curl 'https://api.example.com/users' -H 'Authorization: Bearer token'"
                className="w-full h-48 px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 font-mono text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                autoFocus
              />
              {error && (
                <p className="mt-2 text-sm text-red-600">{error}</p>
              )}
            </div>

            <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-md p-3">
              <p className="text-sm text-blue-800 dark:text-blue-300">
                <strong>Tip:</strong> Right-click on a network request in browser DevTools →
                Copy → Copy as cURL, then paste it here!
              </p>
            </div>
          </div>

          <div className="flex justify-end gap-3 mt-6">
            <button
              onClick={handleClose}
              className="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 rounded-md hover:bg-gray-50 dark:hover:bg-gray-600"
            >
              Cancel
            </button>
            <button
              onClick={handleImport}
              className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700"
            >
              Import
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
