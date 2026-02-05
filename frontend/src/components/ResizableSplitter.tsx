import { useState, useRef, useCallback, useEffect, ReactNode } from 'react';

interface ResizableSplitterProps {
  topPanel: ReactNode;
  bottomPanel: ReactNode;
  initialTopHeight?: number; // percentage (0-100)
  minTopHeight?: number; // percentage
  maxTopHeight?: number; // percentage
  collapsed?: boolean;
  onCollapsedChange?: (collapsed: boolean) => void;
}

export default function ResizableSplitter({
  topPanel,
  bottomPanel,
  initialTopHeight = 50,
  minTopHeight = 20,
  maxTopHeight = 80,
  collapsed = false,
  onCollapsedChange,
}: ResizableSplitterProps) {
  const [topHeight, setTopHeight] = useState(initialTopHeight);
  const [isDragging, setIsDragging] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const toggleCollapse = () => {
    onCollapsedChange?.(!collapsed);
  };

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    setIsDragging(true);
  }, []);

  const handleMouseMove = useCallback(
    (e: MouseEvent) => {
      if (!isDragging || !containerRef.current) return;

      const container = containerRef.current;
      const containerRect = container.getBoundingClientRect();
      const newTopHeight = ((e.clientY - containerRect.top) / containerRect.height) * 100;

      const clampedHeight = Math.min(Math.max(newTopHeight, minTopHeight), maxTopHeight);
      setTopHeight(clampedHeight);
    },
    [isDragging, minTopHeight, maxTopHeight]
  );

  const handleMouseUp = useCallback(() => {
    setIsDragging(false);
  }, []);

  useEffect(() => {
    if (isDragging) {
      document.addEventListener('mousemove', handleMouseMove);
      document.addEventListener('mouseup', handleMouseUp);
      document.body.style.cursor = 'row-resize';
      document.body.style.userSelect = 'none';
    }

    return () => {
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };
  }, [isDragging, handleMouseMove, handleMouseUp]);

  return (
    <div ref={containerRef} className="flex-1 flex flex-col min-h-0">
      {/* Top Panel */}
      <div
        className={`overflow-y-auto bg-white dark:bg-gray-800 ${collapsed ? 'flex-1' : 'flex-shrink-0'}`}
        style={{ height: collapsed ? undefined : `${topHeight}%` }}
      >
        {topPanel}
      </div>

      {/* Splitter Handle with Toggle Button */}
      {!collapsed && (
        <div className="relative flex items-center">
          <div
            className={`
              h-2 flex-1 cursor-row-resize
              flex items-center justify-center
              bg-gray-100 dark:bg-gray-700 border-y border-gray-200 dark:border-gray-600
              hover:bg-blue-100 dark:hover:bg-blue-900/30
              ${isDragging ? 'bg-blue-200 dark:bg-blue-800' : ''}
            `}
            onMouseDown={handleMouseDown}
            style={{ transition: 'none' }}
          >
            <div className="flex gap-1">
              <div className="w-8 h-0.5 bg-gray-400 dark:bg-gray-500 rounded-full" />
            </div>
          </div>
          <button
            onClick={toggleCollapse}
            className="absolute left-1/2 -translate-x-1/2 z-10 h-6 w-12 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-lg shadow-sm hover:bg-gray-50 dark:hover:bg-gray-700 flex items-center justify-center"
            style={{ top: -3, transition: 'none' }}
            title="Collapse response panel"
          >
            <svg
              className="w-4 h-4 text-gray-500 dark:text-gray-400 rotate-90"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </button>
        </div>
      )}

      {/* Bottom Panel */}
      {!collapsed && (
        <div
          className="bg-gray-50 dark:bg-gray-900 min-h-0 overflow-hidden"
          style={{ height: `${100 - topHeight}%` }}
        >
          {bottomPanel}
        </div>
      )}

      {/* Expand Button (when collapsed) */}
      {collapsed && (
        <div className="relative flex items-center justify-center border-t-2 border-gray-300 dark:border-gray-600 bg-gray-100 dark:bg-gray-800 py-2 flex-shrink-0">
          <button
            onClick={toggleCollapse}
            className="flex items-center gap-2 px-4 py-2 bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 rounded-lg shadow-sm hover:bg-gray-50 dark:hover:bg-gray-600 text-gray-600 dark:text-gray-300 hover:text-gray-900 dark:hover:text-gray-100"
            style={{ transition: 'none' }}
            title="Expand response panel"
          >
            <svg
              className="w-4 h-4"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 15l7-7 7 7" />
            </svg>
            <span className="text-sm font-medium">Show Response</span>
          </button>
        </div>
      )}
    </div>
  );
}
