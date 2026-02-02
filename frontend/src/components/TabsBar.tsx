import type { RequestTab } from '../types';

interface TabsBarProps {
  tabs: RequestTab[];
  activeTabId: string;
  onTabSelect: (tabId: string) => void;
  onTabClose: (tabId: string) => void;
  onNewTab: () => void;
  savingStatus?: 'idle' | 'saving' | 'saved';
}

export default function TabsBar({
  tabs,
  activeTabId,
  onTabSelect,
  onTabClose,
  onNewTab,
  savingStatus = 'idle',
}: TabsBarProps) {
  const getMethodColor = (method: string) => {
    const colors: Record<string, string> = {
      GET: 'text-green-600',
      POST: 'text-blue-600',
      PUT: 'text-yellow-600',
      DELETE: 'text-red-600',
      PATCH: 'text-teal-600',
    };
    return colors[method] || 'text-gray-600';
  };

  return (
    <div className="flex items-center bg-gray-100 border-b border-gray-200 overflow-x-auto">
      <div className="flex items-center flex-1 min-w-0">
        {tabs.map((tab) => (
          <div
            key={tab.id}
            onClick={() => onTabSelect(tab.id)}
            className={`
              group flex items-center gap-2 px-4 py-2 cursor-pointer border-r border-gray-200
              min-w-[120px] max-w-[200px] transition-colors
              ${activeTabId === tab.id
                ? 'bg-white border-b-2 border-b-blue-500'
                : 'bg-gray-50 hover:bg-gray-100'
              }
            `}
          >
            <span className={`text-xs font-bold ${getMethodColor(tab.method)}`}>
              {tab.method}
            </span>
            <span className="text-sm text-gray-700 truncate flex-1">
              {tab.name || 'Untitled'}
            </span>
            {tabs.length > 1 && (
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onTabClose(tab.id);
                }}
                className="opacity-0 group-hover:opacity-100 p-1 hover:bg-gray-200 rounded transition-opacity"
              >
                <svg className="w-3 h-3 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            )}
          </div>
        ))}
      </div>
      <button
        onClick={onNewTab}
        className="px-3 py-2 hover:bg-gray-200 transition-colors flex-shrink-0"
        title="New Tab"
      >
        <svg className="w-4 h-4 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
        </svg>
      </button>
      {savingStatus !== 'idle' && (
        <div className="px-3 py-2 text-xs text-gray-500 flex items-center gap-1 flex-shrink-0">
          {savingStatus === 'saving' && (
            <>
              <svg className="w-3 h-3 animate-spin" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              <span>Saving...</span>
            </>
          )}
          {savingStatus === 'saved' && (
            <>
              <svg className="w-3 h-3 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
              <span className="text-green-600">Saved</span>
            </>
          )}
        </div>
      )}
    </div>
  );
}
