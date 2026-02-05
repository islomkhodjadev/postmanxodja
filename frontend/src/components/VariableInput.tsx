import { useState, useRef, useMemo, useCallback, useEffect } from 'react';
import type { Environment } from '../types';

interface VariableInputProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
  environments: Environment[];
  selectedEnvId?: number;
  multiline?: boolean;
  rows?: number;
}

interface VariableInfo {
  name: string;
  value: string | undefined;
  start: number;
  end: number;
}

export default function VariableInput({
  value,
  onChange,
  placeholder,
  className = '',
  environments,
  selectedEnvId,
  multiline = false,
  rows = 6,
}: VariableInputProps) {
  const [hoveredVariable, setHoveredVariable] = useState<VariableInfo | null>(null);
  const [clickedVariable, setClickedVariable] = useState<VariableInfo | null>(null);
  const [tooltipPosition, setTooltipPosition] = useState({ x: 0, y: 0 });
  const [popoverPosition, setPopoverPosition] = useState({ x: 0, y: 0 });
  const [isFocused, setIsFocused] = useState(false);
  const [copySuccess, setCopySuccess] = useState(false);
  const [inputWidth, setInputWidth] = useState(0);

  const inputRef = useRef<HTMLInputElement | HTMLTextAreaElement>(null);
  const overlayRef = useRef<HTMLDivElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  /* ---------------- Environment Variables ---------------- */

  const envVariables = useMemo(() => {
    if (!selectedEnvId) return {};
    const env = environments.find(e => e.id === selectedEnvId);
    if (!env?.variables) return {};

    const vars: Record<string, string> = {};
    try {
      const parsed = typeof env.variables === 'string'
        ? JSON.parse(env.variables)
        : env.variables;

      if (Array.isArray(parsed)) {
        parsed.forEach((v: { key: string; value: string; enabled?: boolean }) => {
          if (v.enabled !== false) vars[v.key] = v.value;
        });
      } else if (typeof parsed === 'object') {
        Object.assign(vars, parsed);
      }
    } catch {
      // ignore invalid JSON
    }

    return vars;
  }, [environments, selectedEnvId]);

  /* ---------------- Parse Variables ---------------- */

  const variables = useMemo(() => {
    const regex = /\{\{([^}]+)\}\}/g;
    const matches: VariableInfo[] = [];
    let match;

    while ((match = regex.exec(value)) !== null) {
      matches.push({
        name: match[1],
        value: envVariables[match[1]],
        start: match.index,
        end: match.index + match[0].length,
      });
    }

    return matches;
  }, [value, envVariables]);

  /* ---------------- Measure Input Width ---------------- */

  useEffect(() => {
    const updateWidth = () => {
      if (containerRef.current) {
        const rect = containerRef.current.getBoundingClientRect();
        setInputWidth(rect.width);
      }
    };

    updateWidth();
    window.addEventListener('resize', updateWidth);
    return () => window.removeEventListener('resize', updateWidth);
  }, []);

  /* ---------------- Render Variables Overlay ---------------- */

  const renderVariablesOverlay = () => {
    if (!value || variables.length === 0) return null;

    // For textarea (multiline), we need to handle line breaks differently
    if (multiline) {
      return variables.map((variable, index) => {
        const hasValue = variable.value !== undefined;
        
        // Calculate position based on text before the variable
        const textBefore = value.slice(0, variable.start);
        const linesBefore = textBefore.split('\n');
        const currentLine = linesBefore.length - 1;
        const charsInCurrentLine = linesBefore[currentLine].length;
        
        // Approximate positioning (this is a simplified approach)
        const lineHeight = 20; // Approximate line height in pixels
        const charWidth = 8.4; // Approximate character width for monospace font
        
        const top = currentLine * lineHeight + 8; // 8px for padding
        const left = charsInCurrentLine * charWidth + 12; // 12px for padding
        
        return (
          <div
            key={`var-${index}`}
            className={`absolute rounded px-0.5 whitespace-pre-wrap cursor-pointer pointer-events-auto border ${
              hasValue
                ? 'bg-orange-100/90 text-orange-700 hover:bg-orange-200/90 border-orange-300'
                : 'bg-red-100/90 text-red-700 hover:bg-red-200/90 border-red-300'
            }`}
            style={{
              top: `${top}px`,
              left: `${left}px`,
              maxWidth: `${inputWidth - left - 12}px`,
              wordBreak: 'break-all',
            }}
            onMouseEnter={(e) => {
              setHoveredVariable(variable);
              setTooltipPosition({ x: e.clientX, y: e.clientY });
            }}
            onMouseMove={(e) =>
              setTooltipPosition({ x: e.clientX, y: e.clientY })
            }
            onMouseLeave={() => setHoveredVariable(null)}
            onClick={(e) => {
              e.stopPropagation();
              setClickedVariable(variable);
              setPopoverPosition({ x: e.clientX, y: e.clientY });
              setCopySuccess(false);
            }}
          >
            {value.slice(variable.start, variable.end)}
          </div>
        );
      });
    }

    // For single-line input (URL)
    return variables.map((variable, index) => {
      const hasValue = variable.value !== undefined;
      const textBefore = value.slice(0, variable.start);
      const charWidth = 8.4; // Approximate character width for monospace font
      const left = textBefore.length * charWidth + 12; // 12px for padding
      
      return (
        <div
          key={`var-${index}`}
          className={`absolute rounded px-0.5 whitespace-nowrap cursor-pointer pointer-events-auto border ${
            hasValue
              ? 'bg-orange-100/90 text-orange-700 hover:bg-orange-200/90 border-orange-300'
              : 'bg-red-100/90 text-red-700 hover:bg-red-200/90 border-red-300'
          }`}
          style={{
            top: '8px',
            left: `${left}px`,
            maxWidth: `${inputWidth - left - 12}px`,
          }}
          onMouseEnter={(e) => {
            setHoveredVariable(variable);
            setTooltipPosition({ x: e.clientX, y: e.clientY });
          }}
          onMouseMove={(e) =>
            setTooltipPosition({ x: e.clientX, y: e.clientY })
          }
          onMouseLeave={() => setHoveredVariable(null)}
          onClick={(e) => {
            e.stopPropagation();
            setClickedVariable(variable);
            setPopoverPosition({ x: e.clientX, y: e.clientY });
            setCopySuccess(false);
          }}
        >
          {value.slice(variable.start, variable.end)}
        </div>
      );
    });
  };

  /* ---------------- Scroll Sync ---------------- */

  const handleScroll = useCallback(() => {
    if (inputRef.current && overlayRef.current) {
      overlayRef.current.scrollTop = inputRef.current.scrollTop;
      overlayRef.current.scrollLeft = inputRef.current.scrollLeft;
    }
  }, []);

  /* ---------------- Base Styles ---------------- */

  const baseClass = multiline
    ? 'w-full min-h-[150px] max-h-[400px] resize-y'
    : 'w-full';

  const inputClass =
    'border border-gray-300 dark:border-gray-600 rounded-lg px-3 py-2 text-sm font-mono bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100';

  const focusClass = isFocused ? 'ring-2 ring-blue-500 border-blue-500' : '';

  /* ---------------- Render ---------------- */

  return (
    <div ref={containerRef} className="relative">
      {/* Input (always visible) */}
      {multiline ? (
        <textarea
          ref={inputRef as React.RefObject<HTMLTextAreaElement>}
          value={value}
          rows={rows}
          onChange={(e) => onChange(e.target.value)}
          onScroll={handleScroll}
          onFocus={() => setIsFocused(true)}
          onBlur={() => setIsFocused(false)}
          placeholder={placeholder}
          className={`${baseClass} ${inputClass} ${focusClass} outline-none ${className}`}
          style={{
            caretColor: 'currentColor',
          }}
        />
      ) : (
        <input
          ref={inputRef as React.RefObject<HTMLInputElement>}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onScroll={handleScroll}
          onFocus={() => setIsFocused(true)}
          onBlur={() => setIsFocused(false)}
          placeholder={placeholder}
          className={`${baseClass} ${inputClass} ${focusClass} outline-none ${className}`}
          style={{
            caretColor: 'currentColor',
          }}
        />
      )}

      {/* Overlay for variable highlighting only */}
      {variables.length > 0 && (
        <div
          ref={overlayRef}
          className="absolute inset-0 pointer-events-none overflow-hidden select-none"
          style={{
            padding: '8px 12px',
            zIndex: 1,
          }}
        >
          {renderVariablesOverlay()}
        </div>
      )}

      {/* Tooltip */}
      {hoveredVariable && (
        <div
          className="fixed z-[9999] bg-gray-900 text-white text-xs rounded shadow-lg pointer-events-none"
          style={{
            left: Math.min(tooltipPosition.x + 12, window.innerWidth - 260),
            top: tooltipPosition.y - 50,
            maxWidth: 240,
          }}
        >
          <div className="px-3 py-2">
            <div className="font-semibold text-orange-400">
              {hoveredVariable.name}
            </div>
            {hoveredVariable.value !== undefined ? (
              <div className="mt-1 font-mono break-all">
                {hoveredVariable.value || (
                  <span className="italic text-gray-400">empty</span>
                )}
              </div>
            ) : (
              <div className="mt-1 text-red-400">
                Not found in environment
              </div>
            )}
          </div>
        </div>
      )}

      {/* Click Popover */}
      {clickedVariable && (
        <>
          <div
            className="fixed inset-0 z-[9998]"
            onClick={() => setClickedVariable(null)}
          />
          <div
            className="fixed z-[9999] bg-white rounded-lg shadow-xl border border-gray-200"
            style={{
              left: Math.min(popoverPosition.x, window.innerWidth - 220),
              top: popoverPosition.y + 8,
              minWidth: 200,
            }}
          >
            <div className="px-3 py-2 border-b font-medium">
              {clickedVariable.name}
            </div>
            <div className="p-2 space-y-1">
              {clickedVariable.value !== undefined && (
                <button
                  onClick={async () => {
                    await navigator.clipboard.writeText(clickedVariable.value!);
                    setCopySuccess(true);
                    setTimeout(() => setCopySuccess(false), 1200);
                  }}
                  className="w-full px-2 py-1 text-sm hover:bg-blue-50 rounded"
                >
                  {copySuccess ? 'Copied!' : 'Copy Value'}
                </button>
              )}
              <button
                onClick={async () =>
                  navigator.clipboard.writeText(`{{${clickedVariable.name}}}`)
                }
                className="w-full px-2 py-1 text-sm hover:bg-blue-50 rounded"
              >
                Copy Variable Name
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}