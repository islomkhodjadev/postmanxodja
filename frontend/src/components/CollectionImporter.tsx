import { useState } from 'react';
import { importCollection, createCollection } from '../services/api';
import { useTeam } from '../contexts/TeamContext';
import InputModal from './InputModal';

interface Props {
  onImportSuccess: () => void;
}

export default function CollectionImporter({ onImportSuccess }: Props) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const { currentTeam } = useTeam();

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !currentTeam) return;

    setLoading(true);
    setError(null);

    try {
      const text = await file.text();
      await importCollection(currentTeam.id, text);
      onImportSuccess();
      e.target.value = '';
    } catch (err: any) {
      setError(err.response?.data?.error || 'Failed to import collection');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateCollection = async (name: string) => {
    if (!currentTeam) return;

    setLoading(true);
    setError(null);

    try {
      await createCollection(currentTeam.id, name);
      onImportSuccess();
      setShowCreateModal(false);
    } catch (err: any) {
      setError(err.response?.data?.error || 'Failed to create collection');
    } finally {
      setLoading(false);
    }
  };

  if (!currentTeam) {
    return (
      <div className="p-4 border-b border-gray-200 bg-white">
        <p className="text-sm text-gray-500">Select a team to manage collections</p>
      </div>
    );
  }

  return (
    <>
      <div className="p-4 border-b border-gray-200 bg-white">
        <div className="flex gap-2">
          <button
            onClick={() => setShowCreateModal(true)}
            disabled={loading}
            className={`
              flex-1 px-3 py-2 rounded-lg shadow-sm font-medium text-sm
              transition-colors duration-200 flex items-center justify-center gap-1
              ${loading
                ? 'bg-gray-400 text-white cursor-not-allowed'
                : 'bg-blue-500 hover:bg-blue-600 text-white'
              }
            `}
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
            New
          </button>
          <label
            htmlFor="file-upload"
            className={`
              flex-1 px-3 py-2 rounded-lg shadow-sm font-medium text-sm
              transition-colors duration-200 flex items-center justify-center gap-1 cursor-pointer
              ${loading
                ? 'bg-gray-400 text-white cursor-not-allowed'
                : 'bg-gray-100 hover:bg-gray-200 text-gray-700 border border-gray-300'
              }
            `}
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
            </svg>
            Import
          </label>
          <input
            id="file-upload"
            type="file"
            accept=".json"
            onChange={handleFileUpload}
            disabled={loading}
            className="hidden"
          />
        </div>
        {error && (
          <div className="mt-3 p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm">
            {error}
          </div>
        )}
      </div>

      <InputModal
        isOpen={showCreateModal}
        onCancel={() => setShowCreateModal(false)}
        onConfirm={handleCreateCollection}
        title="Create Collection"
        placeholder="My Collection"
        confirmText="Create"
      />
    </>
  );
}
