import { useState } from 'react';
import type { ExecuteResponse } from '../types';

interface Props {
  response: ExecuteResponse | null;
}

export default function ResponseViewer({ response }: Props) {
  const [activeTab, setActiveTab] = useState<'body' | 'headers'>('body');

  if (!response) {
    return (
      <div className="p-8 text-gray-400 dark:text-gray-500 text-center">
        Send a request to see the response here
      </div>
    );
  }

  const getStatusColor = (status: number) => {
    if (status >= 200 && status < 300) return '#28a745';
    if (status >= 300 && status < 400) return '#ffc107';
    return '#dc3545';
  };

  const getStatusText = (status: number) => {
    const statusTexts: Record<number, string> = {
      200: 'OK',
      201: 'Created',
      204: 'No Content',
      400: 'Bad Request',
      401: 'Unauthorized',
      403: 'Forbidden',
      404: 'Not Found',
      500: 'Internal Server Error',
      502: 'Bad Gateway',
      503: 'Service Unavailable',
    };
    return statusTexts[status] || 'Unknown';
  };

  const formatJSON = (str: string) => {
    try {
      return JSON.stringify(JSON.parse(str), null, 2);
    } catch {
      return str;
    }
  };

  return (
    <div className="h-full w-full flex flex-col p-6 overflow-hidden">
      {/* Status Bar */}
      <div className="mb-4 flex gap-6 items-center bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-4 shadow-sm">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-gray-600 dark:text-gray-400">Status:</span>
          <span
            className="font-bold text-base px-3 py-1 rounded"
            style={{
              color: getStatusColor(response.status),
              backgroundColor: response.status >= 200 && response.status < 300 ? '#d4edda' :
                               response.status >= 400 ? '#f8d7da' : '#fff3cd'
            }}
          >
            {response.status_text || `${response.status} ${getStatusText(response.status)}`}
          </span>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-gray-600 dark:text-gray-400">Time:</span>
          <span className="font-semibold text-base text-gray-800 dark:text-gray-200">{response.time}ms</span>
        </div>
      </div>

      {/* Tabs */}
      <div className="mb-4 flex gap-2 border-b border-gray-300 dark:border-gray-700">
        <button
          onClick={() => setActiveTab('body')}
          className={`
            px-5 py-2.5 font-semibold text-sm transition-all duration-150 border-b-2
            ${activeTab === 'body'
              ? 'border-blue-500 text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-900/30'
              : 'border-transparent text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700'
            }
          `}
        >
          Body
        </button>
        <button
          onClick={() => setActiveTab('headers')}
          className={`
            px-5 py-2.5 font-semibold text-sm transition-all duration-150 border-b-2
            ${activeTab === 'headers'
              ? 'border-blue-500 text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-900/30'
              : 'border-transparent text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700'
            }
          `}
        >
          Headers ({Object.keys(response.headers).length})
        </button>
      </div>

      {/* Content Area - Fixed height */}
      <div className="flex-1 min-h-0 min-w-0">
        {activeTab === 'body' && (
          <div className="h-full w-full bg-white dark:bg-gray-800 border-2 border-gray-300 dark:border-gray-600 rounded-lg shadow-md overflow-auto">
            {response.body ? (
              <pre className="m-0 p-5 whitespace-pre-wrap break-all font-mono text-sm text-gray-900 dark:text-gray-100 leading-6 max-w-full">
                {formatJSON(response.body)}
              </pre>
            ) : (
              <div className="h-full flex items-center justify-center text-gray-400 dark:text-gray-500 text-lg">
                No response body
              </div>
            )}
          </div>
        )}

        {activeTab === 'headers' && (
          <div className="h-full w-full bg-white dark:bg-gray-800 border-2 border-gray-300 dark:border-gray-600 rounded-lg shadow-md p-5 overflow-auto">
            {Object.keys(response.headers).length > 0 ? (
              Object.entries(response.headers).map(([key, value]) => (
                <div key={key} className="mb-4 pb-4 border-b border-gray-200 dark:border-gray-700 last:border-b-0 last:pb-0 last:mb-0">
                  <div className="font-bold text-sm text-blue-600 dark:text-blue-400 mb-2">{key}</div>
                  <div className="text-sm text-gray-800 dark:text-gray-200 break-all font-mono bg-gray-50 dark:bg-gray-700 p-2 rounded">{value}</div>
                </div>
              ))
            ) : (
              <div className="h-full flex items-center justify-center text-gray-400 dark:text-gray-500 text-lg">
                No headers
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
