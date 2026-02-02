import { useState, useEffect, useCallback, useRef } from 'react';
import CollectionImporter from '../components/CollectionImporter';
import CollectionList from '../components/CollectionList';
import RequestBuilder from '../components/RequestBuilder';
import ResponseViewer from '../components/ResponseViewer';
import EnvironmentPanel from '../components/EnvironmentPanel';
import ResizableSplitter from '../components/ResizableSplitter';
import HorizontalSplitter from '../components/HorizontalSplitter';
import TabsBar from '../components/TabsBar';
import Header from '../components/layout/Header';
import { useTeam } from '../contexts/TeamContext';
import { getEnvironments, getSavedTabs, saveTabs, getCollection, updateCollection } from '../services/api';
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
  const [tabsLoaded, setTabsLoaded] = useState(false);
  const [savingStatus, setSavingStatus] = useState<'idle' | 'saving' | 'saved'>('idle');
  const saveTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
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
      } finally {
        setTabsLoaded(true);
      }
    };
    loadTabs();
  }, []);

  // Save tabs to database with debounce
  useEffect(() => {
    if (!tabsLoaded) return;

    // Clear existing timeout
    if (saveTimeoutRef.current) {
      clearTimeout(saveTimeoutRef.current);
    }

    // Debounce save by 1 second
    saveTimeoutRef.current = setTimeout(async () => {
      setSavingStatus('saving');
      try {
        const tabsToSave = tabs.map((t, i) => ({
          tab_id: t.id,
          name: t.name,
          method: t.method,
          url: t.url,
          headers: t.headers,
          body: t.body,
          query_params: t.queryParams,
          is_active: t.id === activeTabId,
          sort_order: i,
        }));
        await saveTabs(tabsToSave, activeTabId);
        setSavingStatus('saved');
        // Reset to idle after 2 seconds
        setTimeout(() => setSavingStatus('idle'), 2000);
      } catch (e) {
        console.error('Failed to save tabs:', e);
        setSavingStatus('idle');
      }
    }, 1000);

    return () => {
      if (saveTimeoutRef.current) {
        clearTimeout(saveTimeoutRef.current);
      }
    };
  }, [tabs, activeTabId, tabsLoaded]);

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

    // Create new tab for this request
    const newTab: RequestTab = {
      id: generateId(),
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

    setTabs(prev => [...prev, newTab]);
    setActiveTabId(newTab.id);
  }, []);

  const handleTabSelect = useCallback((tabId: string) => {
    setActiveTabId(tabId);
  }, []);

  const handleNewTab = useCallback(() => {
    const newTab = createNewTab();
    setTabs(prev => [...prev, newTab]);
    setActiveTabId(newTab.id);
  }, []);

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
  const saveTabToCollection = useCallback(async (tab: RequestTab, showAlert = true): Promise<boolean> => {
    if (!currentTeam || !tab.collectionId || !tab.itemPath) return false;

    try {
      // Get the current collection
      const collectionData = await getCollection(currentTeam.id, tab.collectionId);
      const collection = collectionData.collection;

      // Find and update the request in the collection
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
                request: {
                  ...item.request,
                  method: tab.method,
                  url: tab.url,
                  header: Object.entries(tab.headers).map(([key, value]) => ({ key, value })),
                  body: tab.body ? { mode: 'raw', raw: tab.body } : undefined,
                },
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

      const updatedCollection = {
        ...collection,
        item: updateInItems(collection.item, folderPath),
      };

      await updateCollection(currentTeam.id, tab.collectionId, {
        raw_json: JSON.stringify(updatedCollection),
      });

      // Mark tab as not dirty
      setTabs(prev => prev.map(t =>
        t.id === tab.id ? { ...t, isDirty: false } : t
      ));

      // Refresh collections
      setRefreshTrigger(prev => prev + 1);

      if (showAlert) {
        alert('Saved to collection!');
      }
      return true;
    } catch (err) {
      console.error('Failed to save to collection:', err);
      if (showAlert) {
        alert('Failed to save to collection');
      }
      return false;
    }
  }, [currentTeam]);

  const handleSaveToCollection = useCallback(async () => {
    await saveTabToCollection(activeTab);
  }, [activeTab, saveTabToCollection]);

  const handleTabClose = useCallback(async (tabId: string) => {
    const tabToClose = tabs.find(t => t.id === tabId);

    // If tab has a collection source, ask if user wants to save
    if (tabToClose?.collectionId && tabToClose?.itemPath) {
      const shouldSave = window.confirm(
        `Save changes to "${tabToClose.name}" back to the collection before closing?`
      );

      if (shouldSave) {
        await saveTabToCollection(tabToClose, false);
      }
    }

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
      <div className="flex-1 flex flex-col min-h-0">
        {/* Header */}
        <Header />

        {/* Tabs Bar */}
        <TabsBar
          tabs={tabs}
          activeTabId={activeTabId}
          onTabSelect={handleTabSelect}
          onTabClose={handleTabClose}
          onNewTab={handleNewTab}
          savingStatus={savingStatus}
        />

        {/* Resizable Request/Response Panels */}
        <ResizableSplitter
          initialTopHeight={50}
          minTopHeight={20}
          maxTopHeight={80}
          topPanel={
            <RequestBuilder
              key={activeTabId}
              initialMethod={activeTab.method}
              initialUrl={activeTab.url}
              initialHeaders={activeTab.headers}
              initialBody={activeTab.body}
              initialQueryParams={activeTab.queryParams}
              environments={environments}
              onResponse={handleResponse}
              onUpdate={handleTabUpdate}
              hasCollectionSource={!!activeTab.collectionId}
              onSaveToCollection={handleSaveToCollection}
            />
          }
          bottomPanel={
            <ResponseViewer response={currentResponse} />
          }
        />
      </div>
    </div>
  );
}
