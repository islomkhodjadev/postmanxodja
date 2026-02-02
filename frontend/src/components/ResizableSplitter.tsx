import { useState, useRef, useCallback, useEffect, ReactNode } from 'react';

interface ResizableSplitterProps {
  topPanel: ReactNode;
  bottomPanel: ReactNode;
  initialTopHeight?: number; // percentage (0-100)
  minTopHeight?: number; // percentage
  maxTopHeight?: number; // percentage
}

export default function ResizableSplitter({
  topPanel,
  bottomPanel,
  initialTopHeight = 50,
  minTopHeight = 20,
  maxTopHeight = 80,
}: ResizableSplitterProps) {
  const [topHeight, setTopHeight] = useState(initialTopHeight);
  const [isDragging, setIsDragging] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

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
        className="overflow-y-auto bg-white flex-shrink-0"
        style={{ height: `${topHeight}%` }}
      >
        {topPanel}
      </div>

      {/* Splitter Handle */}
      <div
        className={`
          h-2 flex-shrink-0 cursor-row-resize
          flex items-center justify-center
          bg-gray-100 border-y border-gray-200
          hover:bg-blue-100 transition-colors
          ${isDragging ? 'bg-blue-200' : ''}
        `}
        onMouseDown={handleMouseDown}
      >
        <div className="flex gap-1">
          <div className="w-8 h-0.5 bg-gray-400 rounded-full" />
        </div>
      </div>

      {/* Bottom Panel */}
      <div
        className="bg-gray-50 min-h-0 overflow-hidden"
        style={{ height: `${100 - topHeight}%` }}
      >
        {bottomPanel}
      </div>
    </div>
  );
}
