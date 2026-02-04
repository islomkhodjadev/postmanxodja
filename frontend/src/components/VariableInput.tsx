import { useState, useRef, useMemo, useCallback } from 'react';
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
  const [tooltipPosition, setTooltipPosition] = useState({ x: 0, y: 0 });
  const [isFocused, setIsFocused] = useState(false);
  const inputRef = useRef<HTMLInputElement | HTMLTextAreaElement>(null);
  const overlayRef = useRef<HTMLDivElement>(null);

  // Get current environment variables
  const envVariables = useMemo(() => {
    if (!selectedEnvId) return {};
    const env = environments.find(e => e.id === selectedEnvId);
    if (!env?.variables) return {};

    const vars: Record<string, string> = {};
    try {
      const parsed = typeof env.variables === 'string' ? JSON.parse(env.variables) : env.variables;
      if (Array.isArray(parsed)) {
        parsed.forEach((v: { key: string; value: string; enabled?: boolean }) => {
          if (v.enabled !== false) {
            vars[v.key] = v.value;
          }
        });
      } else if (typeof parsed === 'object') {
        Object.assign(vars, parsed);
      }
    } catch {
      // Invalid JSON, ignore
    }
    return vars;
  }, [environments, selectedEnvId]);

  // Find all variables in the text
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

  const handleVariableHover = useCallback((variable: VariableInfo, e: React.MouseEvent) => {
    setHoveredVariable(variable);
    setTooltipPosition({ x: e.clientX, y: e.clientY });
  }, []);

  const handleVariableLeave = useCallback(() => {
    setHoveredVariable(null);
  }, []);

  // Sync scroll between input and overlay
  const handleScroll = useCallback(() => {
    if (inputRef.current && overlayRef.current) {
      overlayRef.current.scrollTop = inputRef.current.scrollTop;
      overlayRef.current.scrollLeft = inputRef.current.scrollLeft;
    }
  }, []);

  // Render highlighted text with hoverable variable spans
  const renderHighlightedContent = () => {
    if (variables.length === 0) {
      return <span className="text-transparent whitespace-pre-wrap">{value || placeholder}</span>;
    }

    const parts: React.ReactNode[] = [];
    let lastIndex = 0;

    variables.forEach((variable, index) => {
      // Add text before the variable
      if (variable.start > lastIndex) {
        parts.push(
          <span key={`text-${index}`} className="text-transparent whitespace-pre-wrap">
            {value.slice(lastIndex, variable.start)}
          </span>
        );
      }

      // Add the variable with highlighting
      const hasValue = variable.value !== undefined;
      parts.push(
        <span
          key={`var-${index}`}
          className={`rounded cursor-help whitespace-pre-wrap ${
            hasValue
              ? 'bg-orange-200/80 text-orange-700'
              : 'bg-red-200/80 text-red-700'
          }`}
          onMouseEnter={(e) => handleVariableHover(variable, e)}
          onMouseMove={(e) => setTooltipPosition({ x: e.clientX, y: e.clientY })}
          onMouseLeave={handleVariableLeave}
        >
          {value.slice(variable.start, variable.end)}
        </span>
      );

      lastIndex = variable.end;
    });

    // Add remaining text
    if (lastIndex < value.length) {
      parts.push(
        <span key="text-end" className="text-transparent whitespace-pre-wrap">
          {value.slice(lastIndex)}
        </span>
      );
    }

    return parts;
  };

  const baseClass = multiline
    ? 'w-full min-h-[150px] max-h-[400px] border border-gray-300 rounded-lg px-3 py-2 text-sm font-mono resize-y'
    : 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm';

  const focusClass = isFocused ? 'ring-2 ring-blue-500 border-blue-500' : '';

  return (
    <div className="relative">
      {/* Highlight overlay - positioned absolutely over the input */}
      <div
        ref={overlayRef}
        className={`absolute inset-0 ${baseClass} ${focusClass} pointer-events-none overflow-hidden bg-transparent border-transparent`}
        style={{
          pointerEvents: variables.length > 0 ? 'auto' : 'none',
        }}
      >
        <div className="pointer-events-none">
          {variables.length > 0 && (
            <div
              className="absolute inset-0 px-3 py-2"
              style={{ pointerEvents: 'auto' }}
            >
              {renderHighlightedContent()}
            </div>
          )}
        </div>
      </div>

      {/* Actual input */}
      {multiline ? (
        <textarea
          ref={inputRef as React.RefObject<HTMLTextAreaElement>}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onScroll={handleScroll}
          onFocus={() => setIsFocused(true)}
          onBlur={() => setIsFocused(false)}
          placeholder={placeholder}
          rows={rows}
          className={`${baseClass} ${focusClass} outline-none ${className}`}
          style={{
            color: variables.length > 0 ? 'transparent' : undefined,
            caretColor: 'black',
            background: 'transparent',
          }}
        />
      ) : (
        <input
          ref={inputRef as React.RefObject<HTMLInputElement>}
          type="text"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onScroll={handleScroll}
          onFocus={() => setIsFocused(true)}
          onBlur={() => setIsFocused(false)}
          placeholder={placeholder}
          className={`${baseClass} ${focusClass} outline-none ${className}`}
          style={{
            color: variables.length > 0 ? 'transparent' : undefined,
            caretColor: 'black',
            background: 'transparent',
          }}
        />
      )}

      {/* Tooltip */}
      {hoveredVariable && (
        <div
          className="fixed z-[9999] bg-gray-900 text-white text-xs rounded-lg shadow-xl pointer-events-none animate-in fade-in duration-150"
          style={{
            left: Math.min(tooltipPosition.x + 12, window.innerWidth - 250),
            top: tooltipPosition.y - 60,
            maxWidth: '240px',
          }}
        >
          <div className="px-3 py-2 border-b border-gray-700">
            <div className="flex items-center gap-2">
              <svg className="w-3.5 h-3.5 text-orange-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z" />
              </svg>
              <span className="font-semibold text-orange-400">{hoveredVariable.name}</span>
            </div>
          </div>
          <div className="px-3 py-2">
            {hoveredVariable.value !== undefined ? (
              <div className="space-y-1">
                <div className="text-[10px] text-gray-400 uppercase tracking-wider">Value</div>
                <div className="text-gray-200 font-mono break-all">
                  {hoveredVariable.value || <span className="text-gray-500 italic">empty string</span>}
                </div>
              </div>
            ) : (
              <div className="flex items-center gap-2 text-red-400">
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                </svg>
                <span>Not found in environment</span>
              </div>
            )}
          </div>
          {/* Arrow */}
          <div
            className="absolute w-2 h-2 bg-gray-900 rotate-45"
            style={{ bottom: '-4px', left: '20px' }}
          />
        </div>
      )}
    </div>
  );
}
