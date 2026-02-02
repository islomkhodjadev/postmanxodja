import { useEffect, useState } from 'react';
import { getCollections, getCollection, deleteCollection, updateCollection, exportCollection } from '../services/api';
import { useTeam } from '../contexts/TeamContext';
import ConfirmModal from './ConfirmModal';
import InputModal from './InputModal';
import type { Collection, PostmanItem, PostmanCollection } from '../types';

interface Props {
  onRequestSelect: (request: any) => void;
  refreshTrigger: number;
}

interface DeleteTarget {
  type: 'collection' | 'folder' | 'request';
  collectionId: number;
  path?: string;
  name: string;
}

interface AddTarget {
  type: 'folder' | 'request';
  collectionId: number;
  parentPath?: string;
}

export default function CollectionList({ onRequestSelect, refreshTrigger }: Props) {
  const [collections, setCollections] = useState<Collection[]>([]);
  const [expandedCollections, setExpandedCollections] = useState<Set<number>>(new Set());
  const [expandedFolders, setExpandedFolders] = useState<Set<string>>(new Set());
  const [collectionData, setCollectionData] = useState<Map<number, PostmanCollection>>(new Map());
  const { currentTeam } = useTeam();

  // Modal states
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null);
  const [addTarget, setAddTarget] = useState<AddTarget | null>(null);

  useEffect(() => {
    if (currentTeam) {
      loadCollections();
      setExpandedCollections(new Set());
      setCollectionData(new Map());
    }
  }, [refreshTrigger, currentTeam?.id]);

  const loadCollections = async () => {
    if (!currentTeam) return;
    try {
      const data = await getCollections(currentTeam.id);
      setCollections(data);
    } catch (err) {
      console.error('Failed to load collections:', err);
    }
  };

  const toggleCollection = async (id: number) => {
    if (!currentTeam) return;
    const newExpanded = new Set(expandedCollections);

    if (newExpanded.has(id)) {
      newExpanded.delete(id);
    } else {
      newExpanded.add(id);
      if (!collectionData.has(id)) {
        try {
          const data = await getCollection(currentTeam.id, id);
          setCollectionData(new Map(collectionData.set(id, data.collection)));
        } catch (err) {
          console.error('Failed to load collection details:', err);
        }
      }
    }

    setExpandedCollections(newExpanded);
  };

  const handleDeleteClick = (target: DeleteTarget, e: React.MouseEvent) => {
    e.stopPropagation();
    setDeleteTarget(target);
  };

  const handleConfirmDelete = async () => {
    if (!deleteTarget || !currentTeam) return;

    try {
      if (deleteTarget.type === 'collection') {
        await deleteCollection(currentTeam.id, deleteTarget.collectionId);
        loadCollections();
      } else {
        // Delete folder or request from collection
        const collection = collectionData.get(deleteTarget.collectionId);
        if (collection && deleteTarget.path) {
          const updatedCollection = deleteItemFromCollection(collection, deleteTarget.path);
          await updateCollection(currentTeam.id, deleteTarget.collectionId, {
            raw_json: JSON.stringify(updatedCollection),
          });
          setCollectionData(new Map(collectionData.set(deleteTarget.collectionId, updatedCollection)));
        }
      }
    } catch (err) {
      console.error('Failed to delete:', err);
    }

    setDeleteTarget(null);
  };

  const handleAddClick = (target: AddTarget, e: React.MouseEvent) => {
    e.stopPropagation();
    setAddTarget(target);
  };

  const handleConfirmAdd = async (name: string) => {
    if (!addTarget || !currentTeam) return;

    try {
      const collection = collectionData.get(addTarget.collectionId);
      if (collection) {
        let updatedCollection: PostmanCollection;

        if (addTarget.type === 'folder') {
          const newFolder: PostmanItem = { name, item: [] };
          updatedCollection = addItemToCollection(collection, addTarget.parentPath, newFolder);
        } else {
          const newRequest: PostmanItem = {
            name,
            request: {
              method: 'GET',
              url: '',
              header: [],
            },
          };
          updatedCollection = addItemToCollection(collection, addTarget.parentPath, newRequest);
        }

        await updateCollection(currentTeam.id, addTarget.collectionId, {
          raw_json: JSON.stringify(updatedCollection),
        });
        setCollectionData(new Map(collectionData.set(addTarget.collectionId, updatedCollection)));

        // Auto-expand the folder where item was added
        if (addTarget.parentPath) {
          setExpandedFolders(new Set(expandedFolders).add(addTarget.parentPath));
        }
      }
    } catch (err) {
      console.error('Failed to add item:', err);
    }

    setAddTarget(null);
  };

  const deleteItemFromCollection = (collection: PostmanCollection, path: string): PostmanCollection => {
    const pathParts = path.split('/');
    const itemName = pathParts[pathParts.length - 1];
    const parentPath = pathParts.slice(0, -1);

    const deleteFromItems = (items: PostmanItem[], currentPath: string[]): PostmanItem[] => {
      if (currentPath.length === 0) {
        return items.filter(item => item.name !== itemName);
      }

      return items.map(item => {
        if (item.name === currentPath[0] && item.item) {
          return {
            ...item,
            item: deleteFromItems(item.item, currentPath.slice(1)),
          };
        }
        return item;
      });
    };

    return {
      ...collection,
      item: deleteFromItems(collection.item, parentPath),
    };
  };

  const addItemToCollection = (collection: PostmanCollection, parentPath: string | undefined, newItem: PostmanItem): PostmanCollection => {
    if (!parentPath) {
      return {
        ...collection,
        item: [...collection.item, newItem],
      };
    }

    const pathParts = parentPath.split('/');

    const addToItems = (items: PostmanItem[], currentPath: string[]): PostmanItem[] => {
      return items.map(item => {
        if (item.name === currentPath[0]) {
          if (currentPath.length === 1 && item.item) {
            return {
              ...item,
              item: [...item.item, newItem],
            };
          } else if (item.item) {
            return {
              ...item,
              item: addToItems(item.item, currentPath.slice(1)),
            };
          }
        }
        return item;
      });
    };

    return {
      ...collection,
      item: addToItems(collection.item, pathParts),
    };
  };

  const toggleFolder = (folderPath: string) => {
    const newExpanded = new Set(expandedFolders);
    if (newExpanded.has(folderPath)) {
      newExpanded.delete(folderPath);
    } else {
      newExpanded.add(folderPath);
    }
    setExpandedFolders(newExpanded);
  };

  const renderItem = (item: PostmanItem, collectionId: number, depth = 0, parentPath = '') => {
    const itemPath = parentPath ? `${parentPath}/${item.name}` : item.name;
    const paddingLeft = `${depth * 16 + 12}px`;

    if (item.request) {
      return (
        <div
          key={itemPath}
          className="group py-2 px-3 cursor-pointer border-b border-gray-100 hover:bg-blue-50 transition-colors duration-150 flex items-center"
          style={{ paddingLeft }}
        >
          <div
            className="flex-1 flex items-center"
            onClick={() => onRequestSelect({ ...item.request, name: item.name, collectionId, itemPath })}
          >
            <span
              className="font-semibold mr-2 text-xs"
              style={{ color: getMethodColor(item.request.method) }}
            >
              {item.request.method}
            </span>
            <span className="text-sm text-gray-700">{item.name}</span>
          </div>
          <button
            onClick={(e) => handleDeleteClick({ type: 'request', collectionId, path: itemPath, name: item.name }, e)}
            className="opacity-0 group-hover:opacity-100 p-1 hover:bg-red-100 rounded transition-opacity"
            title="Delete request"
          >
            <svg className="w-4 h-4 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
            </svg>
          </button>
        </div>
      );
    }

    if (item.item) {
      const isExpanded = expandedFolders.has(itemPath);
      return (
        <div key={itemPath}>
          <div
            className="group py-2 px-3 font-semibold bg-gray-50 text-gray-700 text-sm cursor-pointer hover:bg-gray-100 transition-colors duration-150 flex items-center gap-2"
            style={{ paddingLeft }}
          >
            <div className="flex-1 flex items-center gap-2" onClick={() => toggleFolder(itemPath)}>
              <span className="text-gray-400 text-xs">{isExpanded ? '▼' : '▶'}</span>
              <svg className="w-4 h-4 text-yellow-500" fill="currentColor" viewBox="0 0 20 20">
                <path d="M2 6a2 2 0 012-2h5l2 2h5a2 2 0 012 2v6a2 2 0 01-2 2H4a2 2 0 01-2-2V6z" />
              </svg>
              <span>{item.name}</span>
              <span className="text-xs text-gray-400">{item.item.length}</span>
            </div>
            <div className="opacity-0 group-hover:opacity-100 flex items-center gap-1 transition-opacity">
              <button
                onClick={(e) => handleAddClick({ type: 'folder', collectionId, parentPath: itemPath }, e)}
                className="p-1 hover:bg-blue-100 rounded"
                title="Add folder"
              >
                <svg className="w-4 h-4 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 13h6m-3-3v6m-9 1V7a2 2 0 012-2h6l2 2h6a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2z" />
                </svg>
              </button>
              <button
                onClick={(e) => handleAddClick({ type: 'request', collectionId, parentPath: itemPath }, e)}
                className="p-1 hover:bg-green-100 rounded"
                title="Add request"
              >
                <svg className="w-4 h-4 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                </svg>
              </button>
              <button
                onClick={(e) => handleDeleteClick({ type: 'folder', collectionId, path: itemPath, name: item.name }, e)}
                className="p-1 hover:bg-red-100 rounded"
                title="Delete folder"
              >
                <svg className="w-4 h-4 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                </svg>
              </button>
            </div>
          </div>
          {isExpanded && (
            <div className="bg-white">
              {item.item.map(subItem => renderItem(subItem, collectionId, depth + 1, itemPath))}
            </div>
          )}
        </div>
      );
    }

    return null;
  };

  const getMethodColor = (method: string) => {
    const colors: Record<string, string> = {
      GET: '#28a745',
      POST: '#007bff',
      PUT: '#ffc107',
      DELETE: '#dc3545',
      PATCH: '#17a2b8'
    };
    return colors[method] || '#6c757d';
  };

  if (!currentTeam) {
    return (
      <div className="h-full overflow-y-auto border-r border-gray-200 bg-white p-4">
        <p className="text-sm text-gray-500">Select a team to view collections</p>
      </div>
    );
  }

  return (
    <div className="h-full overflow-y-auto border-r border-gray-200 bg-white">
      <h3 className="px-4 py-3 font-semibold text-gray-800 border-b border-gray-200">Collections</h3>
      {collections.length === 0 ? (
        <div className="px-4 py-8 text-center text-gray-500 text-sm">
          No collections yet. Import one to get started.
        </div>
      ) : (
        collections.map(collection => (
          <div key={collection.id} className="mb-2">
            <div
              onClick={() => toggleCollection(collection.id)}
              className="group px-4 py-3 cursor-pointer bg-gray-50 hover:bg-gray-100 border-b border-gray-200 flex justify-between items-center transition-colors duration-150"
            >
              <span className="text-sm font-medium text-gray-700 flex items-center gap-2">
                <span className="text-gray-500">{expandedCollections.has(collection.id) ? '▼' : '▶'}</span>
                {collection.name}
              </span>
              <div className="flex items-center gap-1">
                {expandedCollections.has(collection.id) && (
                  <>
                    <button
                      onClick={(e) => handleAddClick({ type: 'folder', collectionId: collection.id }, e)}
                      className="opacity-0 group-hover:opacity-100 p-1 hover:bg-blue-100 rounded transition-opacity"
                      title="Add folder"
                    >
                      <svg className="w-4 h-4 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 13h6m-3-3v6m-9 1V7a2 2 0 012-2h6l2 2h6a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2z" />
                      </svg>
                    </button>
                    <button
                      onClick={(e) => handleAddClick({ type: 'request', collectionId: collection.id }, e)}
                      className="opacity-0 group-hover:opacity-100 p-1 hover:bg-green-100 rounded transition-opacity"
                      title="Add request"
                    >
                      <svg className="w-4 h-4 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                      </svg>
                    </button>
                  </>
                )}
                <button
                  onClick={async (e) => {
                    e.stopPropagation();
                    try {
                      await exportCollection(currentTeam.id, collection.id, collection.name);
                    } catch (err) {
                      console.error('Failed to export collection:', err);
                      alert('Failed to export collection');
                    }
                  }}
                  className="px-3 py-1 bg-blue-500 hover:bg-blue-600 text-white text-xs rounded-lg shadow-sm transition-colors duration-150"
                  title="Export to Postman format"
                >
                  Export
                </button>
                <button
                  onClick={(e) => handleDeleteClick({ type: 'collection', collectionId: collection.id, name: collection.name }, e)}
                  className="px-3 py-1 bg-red-500 hover:bg-red-600 text-white text-xs rounded-lg shadow-sm transition-colors duration-150"
                >
                  Delete
                </button>
              </div>
            </div>
            {expandedCollections.has(collection.id) && collectionData.get(collection.id) && (
              <div className="bg-white">
                {collectionData.get(collection.id)!.item.map((item: PostmanItem) => renderItem(item, collection.id))}
              </div>
            )}
          </div>
        ))
      )}

      {/* Delete Confirmation Modal */}
      <ConfirmModal
        isOpen={!!deleteTarget}
        title={`Delete ${deleteTarget?.type || 'item'}?`}
        message={`Are you sure you want to delete "${deleteTarget?.name}"? This action cannot be undone.`}
        confirmText="Delete"
        onConfirm={handleConfirmDelete}
        onCancel={() => setDeleteTarget(null)}
        variant="danger"
      />

      {/* Add Item Modal */}
      <InputModal
        isOpen={!!addTarget}
        title={addTarget?.type === 'folder' ? 'New Folder' : 'New Request'}
        placeholder={addTarget?.type === 'folder' ? 'Folder name' : 'Request name'}
        onConfirm={handleConfirmAdd}
        onCancel={() => setAddTarget(null)}
      />
    </div>
  );
}
