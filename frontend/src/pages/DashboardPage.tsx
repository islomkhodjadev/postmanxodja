import { useState, useEffect, useCallback } from 'react';
import CollectionImporter from '../components/CollectionImporter';
import CollectionList from '../components/CollectionList';
import RequestBuilder from '../components/RequestBuilder';
import ResponseViewer from '../components/ResponseViewer';
import EnvironmentPanel from '../components/EnvironmentPanel';
import ResizableSplitter from '../components/ResizableSplitter';
import HorizontalSplitter from '../components/HorizontalSplitter';
import TabsBar from '../components/TabsBar';
import CurlImportModal from '../components/CurlImportModal';
import ConfirmModal from '../components/ConfirmModal';
import CollectionSelector from '../components/CollectionSelector';
import Header from '../components/layout/Header';
import { useTeam } from '../contexts/TeamContext';
import { getEnvironments, getSavedTabs, getCollection, updateCollection } from '../services/api';
import type { ExecuteResponse, Environment, RequestTab } from '../types';

// Helper to generate unique IDs
const generateId = () => Math.random().toString(36).substring(2, 11);

// Create a new empty tab
const createNewTab = (): RequestTab => ({
  id: generateId(),
  name: 'Untitled',
  method: 'GET',
  url: '',
  headers: {},
  body: '',
  queryParams: {},
});

export default function DashboardPage() {
  const defaultTab = createNewTab();
  const [tabs, setTabs] = useState<RequestTab[]>([defaultTab]);
  const [activeTabId, setActiveTabId] = useState<string>(defaultTab.id);
  const [responses, setResponses] = useState<Map<string, ExecuteResponse>>(new Map());
  const [refreshTrigger, setRefreshTrigger] = useState(0);
  const [environments, setEnvironments] = useState<Environment[]>([]);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [responseCollapsed, setResponseCollapsed] = useState(false);
  const [curlImportOpen, setCurlImportOpen] = useState(false);
  const [activeCollectionId, setActiveCollectionId] = useState<number | null>(null);
  const [collectionSelectorOpen, setCollectionSelectorOpen] = useState(false);
  const [tabToSave, setTabToSave] = useState<RequestTab | null>(null);
  const [confirmModal, setConfirmModal] = useState<{
    isOpen: boolean;
    title: string;
    message: string;
    confirmText?: string;
    cancelText?: string;
    thirdActionText?: string;
    variant?: 'danger' | 'warning' | 'info';
    thirdActionVariant?: 'danger' | 'warning' | 'info';
    onConfirm: () => void;
    onCancel?: () => void;
    onThirdAction?: () => void;
  }>({
    isOpen: false,
    title: '',
    message: '',
    onConfirm: () => {},
  });
  const { currentTeam, isLoading } = useTeam();

  // Get current active tab
  const activeTab = tabs.find(t => t.id === activeTabId) || tabs[0];
  const currentResponse = responses.get(activeTabId) || null;

  // Load tabs from database on mount
  useEffect(() => {
    const loadTabs = async () => {
      try {
        const savedTabs = await getSavedTabs();
        if (savedTabs && savedTabs.length > 0) {
          const loadedTabs: RequestTab[] = savedTabs.map(t => ({
            id: t.tab_id,
            name: t.name,
            method: t.method,
            url: t.url,
            headers: t.headers || {},
            body: t.body,
            queryParams: t.query_params || {},
          }));
          setTabs(loadedTabs);
          const activeTab = savedTabs.find(t => t.is_active);
          if (activeTab) {
            setActiveTabId(activeTab.tab_id);
          } else {
            setActiveTabId(loadedTabs[0].id);
          }
        }
      } catch (e) {
        console.error('Failed to load tabs:', e);
      }
    };
    loadTabs();
  }, []);

  // Load environments when team changes
  useEffect(() => {
    if (currentTeam) {
      loadEnvironments();
    } else {
      setEnvironments([]);
    }
  }, [currentTeam?.id]);

  const loadEnvironments = async () => {
    if (!currentTeam) return;
    try {
      const envs = await getEnvironments(currentTeam.id);
      setEnvironments(envs);
    } catch (err) {
      console.error('Failed to load environments:', err);
    }
  };

  const handleImportSuccess = () => {
    setRefreshTrigger(prev => prev + 1);
  };

  const handleRequestSelect = useCallback((request: any) => {
    // Track which collection is currently active
    if (request.collectionId) {
      setActiveCollectionId(request.collectionId);
    }

    // Extract URL from request
    let url = '';
    if (typeof request.url === 'string') {
      url = request.url;
    } else if (request.url?.raw) {
      url = request.url.raw;
    }

    // Extract headers
    const headers: Record<string, string> = {};
    if (request.header) {
      request.header.forEach((h: any) => {
        if (!h.disabled) {
          headers[h.key] = h.value;
        }
      });
    }

    // Extract query params from URL object
    const queryParams: Record<string, string> = {};
    if (request.url?.query) {
      request.url.query.forEach((q: any) => {
        if (!q.disabled) {
          queryParams[q.key] = q.value;
        }
      });
    }

    // Check if this request is already open
    const existingTab = tabs.find(tab =>
      tab.collectionId === request.collectionId &&
      tab.itemPath === request.itemPath
    );

    // If already open, just switch to it
    if (existingTab) {
      setActiveTabId(existingTab.id);
      return;
    }

    // Check if there's an empty/untitled tab we can reuse
    const emptyTab = tabs.find(tab =>
      tab.name === 'Untitled' &&
      !tab.url &&
      !tab.body &&
      Object.keys(tab.headers).length === 0
    );

    const requestData = {
      name: request.name || 'Untitled',
      method: request.method || 'GET',
      url,
      headers,
      body: request.body?.raw || '',
      queryParams,
      request,
      // Store collection source info for syncing changes back
      collectionId: request.collectionId,
      itemPath: request.itemPath,
    };

    if (emptyTab) {
      // Reuse existing empty tab
      setTabs(prev => prev.map(tab =>
        tab.id === emptyTab.id ? { ...tab, ...requestData } : tab
      ));
      setActiveTabId(emptyTab.id);
    } else {
      // Create new tab for this request
      const newTab: RequestTab = {
        id: generateId(),
        ...requestData,
      };
      setTabs(prev => [...prev, newTab]);
      setActiveTabId(newTab.id);
    }
  }, [tabs]);

  const handleTabSelect = useCallback((tabId: string) => {
    setActiveTabId(tabId);
  }, []);

  const handleTabRename = useCallback((tabId: string, newName: string) => {
    setTabs(prev => prev.map(tab =>
      tab.id === tabId ? { ...tab, name: newName, isDirty: true } : tab
    ));
  }, []);

  const handleNewTab = useCallback(() => {
    const newTab = createNewTab();
    // If there's an active collection, associate this new tab with it
    if (activeCollectionId) {
      newTab.collectionId = activeCollectionId;
      // Note: itemPath will be null/undefined for new requests
      // The save function will need to handle adding new requests to the collection
    }
    setTabs(prev => [...prev, newTab]);
    setActiveTabId(newTab.id);
  }, [activeCollectionId]);

  const handleImportCurl = useCallback(() => {
    setCurlImportOpen(true);
  }, []);

  const handleCurlImport = useCallback((data: { method: string; url: string; headers: Record<string, string>; body: string }) => {
    // Check if there's an empty/untitled tab we can reuse
    const emptyTab = tabs.find(tab =>
      tab.name === 'Untitled' &&
      !tab.url &&
      !tab.body &&
      Object.keys(tab.headers).length === 0
    );

    const newTabData = {
      name: 'Imported Request',
      method: data.method,
      url: data.url,
      headers: data.headers,
      body: data.body,
      queryParams: {},
    };

    if (emptyTab) {
      // Reuse existing empty tab
      setTabs(prev => prev.map(tab =>
        tab.id === emptyTab.id ? { ...tab, ...newTabData } : tab
      ));
      setActiveTabId(emptyTab.id);
    } else {
      // Create new tab
      const newTab: RequestTab = {
        id: generateId(),
        ...newTabData,
      };
      setTabs(prev => [...prev, newTab]);
      setActiveTabId(newTab.id);
    }
  }, [tabs]);

  const handleResponse = useCallback((resp: ExecuteResponse) => {
    setResponses(prev => new Map(prev).set(activeTabId, resp));
  }, [activeTabId]);

  const handleTabUpdate = useCallback((updates: Partial<RequestTab>) => {
    setTabs(prev => prev.map(tab =>
      tab.id === activeTabId ? { ...tab, ...updates, isDirty: true } : tab
    ));
  }, [activeTabId]);

  const handleEnvironmentsUpdate = () => {
    loadEnvironments();
  };

  // Helper function to save a specific tab to its collection
  const saveTabToCollection = useCallback(async (
    tab: RequestTab,
    targetCollectionId?: number,
    targetFolderPath?: string[]
  ): Promise<boolean> => {
    const collectionId = targetCollectionId || tab.collectionId;
    if (!currentTeam || !collectionId) return false;

    try {
      // Get the current collection
      const collectionData = await getCollection(currentTeam.id, collectionId);
      const collection = collectionData.collection;

      let updatedCollection;
      let newItemPath: string | undefined;

      // Prepare request data with all fields including query params
      const requestData = {
        method: tab.method,
        url: {
          raw: tab.url,
          query: Object.entries(tab.queryParams || {}).map(([key, value]) => ({
            key,
            value,
            disabled: false,
          })),
        },
        header: Object.entries(tab.headers || {}).map(([key, value]) => ({
          key,
          value,
          disabled: false,
        })),
        body: tab.body ? { mode: 'raw', raw: tab.body } : undefined,
      };

      if (tab.itemPath) {
        // Existing request - update it
        const pathParts = tab.itemPath.split('/');
        const requestName = pathParts[pathParts.length - 1];
        const folderPath = pathParts.slice(0, -1);

        const updateInItems = (items: any[], currentPath: string[]): any[] => {
          if (currentPath.length === 0) {
            // We're at the right level, find and update the request
            return items.map(item => {
              if (item.name === requestName && item.request) {
                return {
                  ...item,
                  name: tab.name,
                  request: requestData,
                };
              }
              return item;
            });
          }

          // Navigate deeper into folders
          return items.map(item => {
            if (item.name === currentPath[0] && item.item) {
              return {
                ...item,
                item: updateInItems(item.item, currentPath.slice(1)),
              };
            }
            return item;
          });
        };

        updatedCollection = {
          ...collection,
          item: updateInItems(collection.item, folderPath),
        };
      } else {
        // New request - add it to the collection at the specified folder path or root level
        const newRequest = {
          name: tab.name,
          request: requestData,
        };

        if (targetFolderPath && targetFolderPath.length > 0) {
          // Add to specific folder
          const addToFolder = (items: any[], currentPath: string[]): any[] => {
            if (currentPath.length === 0) {
              // We're at the target folder, add the request here
              return [...items, newRequest];
            }

            // Navigate deeper into folders
            return items.map(item => {
              if (item.name === currentPath[0] && item.item) {
                return {
                  ...item,
                  item: addToFolder(item.item, currentPath.slice(1)),
                };
              }
              return item;
            });
          };

          updatedCollection = {
            ...collection,
            item: addToFolder(collection.item, targetFolderPath),
          };

          // Set the itemPath for the newly added request
          newItemPath = [...targetFolderPath, tab.name].join('/');
        } else {
          // Add to root level
          updatedCollection = {
            ...collection,
            item: [...(collection.item || []), newRequest],
          };

          // Set the itemPath for the newly added request
          newItemPath = tab.name;
        }
      }

      await updateCollection(currentTeam.id, collectionId, {
        raw_json: JSON.stringify(updatedCollection),
      });

      // Mark tab as not dirty and update itemPath and collectionId if it was a new request
      setTabs(prev => prev.map(t =>
        t.id === tab.id ? {
          ...t,
          isDirty: false,
          itemPath: newItemPath || t.itemPath,
          collectionId: collectionId
        } : t
      ));

      // Refresh collections
      setRefreshTrigger(prev => prev + 1);

      return true;
    } catch (err) {
      console.error('Failed to save to collection:', err);
      return false;
    }
  }, [currentTeam]);

  const handleSaveToCollection = useCallback(async () => {
    await saveTabToCollection(activeTab);
  }, [activeTab, saveTabToCollection]);

  const handleCollectionSelect = useCallback(async (collectionId: number, folderPath: string[]) => {
    setCollectionSelectorOpen(false);
    if (tabToSave) {
      await saveTabToCollection(tabToSave, collectionId, folderPath);
      // Close the tab after saving
      const tabId = tabToSave.id;
      setTabs(prev => {
        const newTabs = prev.filter(t => t.id !== tabId);
        if (tabId === activeTabId && newTabs.length > 0) {
          const closedIndex = prev.findIndex(t => t.id === tabId);
          const newActiveIndex = Math.min(closedIndex, newTabs.length - 1);
          setActiveTabId(newTabs[newActiveIndex].id);
        }
        return newTabs;
      });
      setResponses(prev => {
        const newResponses = new Map(prev);
        newResponses.delete(tabId);
        return newResponses;
      });
      setTabToSave(null);
    }
  }, [tabToSave, saveTabToCollection, activeTabId]);

  const handleTabClose = useCallback((tabId: string) => {
    const tabToClose = tabs.find(t => t.id === tabId);

    const closeTab = () => {
      setTabs(prev => {
        const newTabs = prev.filter(t => t.id !== tabId);
        // If we closed the active tab, switch to another
        if (tabId === activeTabId && newTabs.length > 0) {
          const closedIndex = prev.findIndex(t => t.id === tabId);
          const newActiveIndex = Math.min(closedIndex, newTabs.length - 1);
          setActiveTabId(newTabs[newActiveIndex].id);
        }
        return newTabs;
      });
      // Remove response for closed tab
      setResponses(prev => {
        const newResponses = new Map(prev);
        newResponses.delete(tabId);
        return newResponses;
      });
    };

    // If tab has changes, show two-button modal
    if (tabToClose?.isDirty) {
      setConfirmModal({
        isOpen: true,
        title: 'Unsaved Changes',
        message: `"${tabToClose.name}" has unsaved changes. What would you like to do?`,
        cancelText: '', // Don't show cancel button
        thirdActionText: 'Discard changes',
        confirmText: 'Save changes',
        variant: 'info',
        thirdActionVariant: 'danger',
        onCancel: () => {
          // Prevent closing modal by clicking outside or pressing Escape
        },
        onThirdAction: () => {
          setConfirmModal(prev => ({ ...prev, isOpen: false }));
          closeTab();
        },
        onConfirm: async () => {
          setConfirmModal(prev => ({ ...prev, isOpen: false }));
          // Check if this is a new tab (no itemPath)
          if (!tabToClose.itemPath) {
            // Show collection selector for new tabs to let user choose where to save
            setTabToSave(tabToClose);
            setCollectionSelectorOpen(true);
          } else if (tabToClose.collectionId) {
            // Existing tab with collection, save directly
            await saveTabToCollection(tabToClose);
            closeTab();
          } else {
            // Can't save without collection
            closeTab();
          }
        },
      });
    } else {
      // No changes, close immediately
      closeTab();
    }
  }, [activeTabId, tabs, saveTabToCollection]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-screen bg-gray-50">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mx-auto mb-4"></div>
          <p className="text-gray-600">Loading...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-screen bg-gray-50">
      {/* cURL Import Modal */}
      <CurlImportModal
        isOpen={curlImportOpen}
        onClose={() => setCurlImportOpen(false)}
        onImport={handleCurlImport}
      />

      {/* Confirm Modal */}
      <ConfirmModal
        isOpen={confirmModal.isOpen}
        title={confirmModal.title}
        message={confirmModal.message}
        confirmText={confirmModal.confirmText}
        cancelText={confirmModal.cancelText}
        thirdActionText={confirmModal.thirdActionText}
        variant={confirmModal.variant}
        thirdActionVariant={confirmModal.thirdActionVariant}
        onConfirm={confirmModal.onConfirm}
        onCancel={confirmModal.onCancel || (() => setConfirmModal(prev => ({ ...prev, isOpen: false })))}
        onThirdAction={confirmModal.onThirdAction}
      />

      {/* Collection Selector Modal */}
      <CollectionSelector
        isOpen={collectionSelectorOpen}
        onSelect={handleCollectionSelect}
        onCancel={() => {
          setCollectionSelectorOpen(false);
          setTabToSave(null);
        }}
      />

      {/* Resizable Sidebar */}
      <HorizontalSplitter
        initialWidth={320}
        minWidth={200}
        maxWidth={500}
        collapsed={sidebarCollapsed}
        onCollapsedChange={setSidebarCollapsed}
      >
        <CollectionImporter onImportSuccess={handleImportSuccess} />
        <div className="flex-1 overflow-y-auto min-h-0">
          <CollectionList onRequestSelect={handleRequestSelect} refreshTrigger={refreshTrigger} />
        </div>
        <EnvironmentPanel onUpdate={handleEnvironmentsUpdate} />
      </HorizontalSplitter>

      {/* Main Content */}
      <div className="flex-1 flex flex-col min-h-0 min-w-0 overflow-hidden">
        {/* Header */}
        <Header />

        {/* Tabs Bar */}
        <TabsBar
          tabs={tabs}
          activeTabId={activeTabId}
          onTabSelect={handleTabSelect}
          onTabClose={handleTabClose}
          onTabRename={handleTabRename}
          onNewTab={handleNewTab}
          onImportCurl={handleImportCurl}
        />

        {/* Content Area */}
        {tabs.length === 0 ? (
          <div className="flex-1 flex items-center justify-center bg-gray-50">
            <div className="text-center max-w-md p-8">
              <svg className="w-24 h-24 mx-auto mb-6 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
              <h2 className="text-2xl font-semibold text-gray-700 mb-3">No Tabs Open</h2>
              <p className="text-gray-500 mb-6">
                Start by creating a new request or importing from cURL
              </p>
              <div className="flex gap-3 justify-center">
                <button
                  onClick={handleNewTab}
                  className="px-6 py-3 bg-blue-500 hover:bg-blue-600 text-white rounded-lg font-medium flex items-center gap-2"
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                  </svg>
                  New Request
                </button>
                <button
                  onClick={handleImportCurl}
                  className="px-6 py-3 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg font-medium flex items-center gap-2 border border-gray-300"
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10" />
                  </svg>
                  Import cURL
                </button>
              </div>
            </div>
          </div>
        ) : (
          <ResizableSplitter
            initialTopHeight={50}
            minTopHeight={20}
            maxTopHeight={80}
            collapsed={responseCollapsed}
            onCollapsedChange={setResponseCollapsed}
            topPanel={
              <RequestBuilder
                key={activeTabId}
                initialMethod={activeTab?.method || 'GET'}
                initialUrl={activeTab?.url || ''}
                initialHeaders={activeTab?.headers || {}}
                initialBody={activeTab?.body || ''}
                initialQueryParams={activeTab?.queryParams || {}}
                environments={environments}
                onResponse={handleResponse}
                onUpdate={handleTabUpdate}
                hasCollectionSource={!!activeTab?.collectionId}
                onSaveToCollection={handleSaveToCollection}
              />
            }
            bottomPanel={
              <ResponseViewer response={currentResponse} />
            }
          />
        )}
      </div>
    </div>
  );
}
