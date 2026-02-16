import { useCallback, useEffect, useRef, useState } from 'react';
import type { ServerMessage } from '../types/game';

export function useWebSocket(url: string) {
  const wsRef = useRef<WebSocket | null>(null);
  const [connected, setConnected] = useState(false);
  const [messages, setMessages] = useState<ServerMessage[]>([]);

  useEffect(() => {
    let disposed = false;

    const connect = () => {
      const ws = new WebSocket(url);
      wsRef.current = ws;

      ws.onopen = () => {
        if (!disposed) setConnected(true);
      };
      ws.onclose = () => {
        if (!disposed) {
          setConnected(false);
          setTimeout(connect, 2000);
        }
      };
      ws.onerror = () => ws.close();
      ws.onmessage = (event) => {
        if (disposed) return;
        try {
          const msg = JSON.parse(event.data) as ServerMessage;
          setMessages((prev) => [...prev, msg]);
        } catch {
          // ignore malformed messages
        }
      };
    };

    connect();

    return () => {
      disposed = true;
      wsRef.current?.close();
    };
  }, [url]);

  const send = useCallback((msg: object) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify(msg));
    }
  }, []);

  return { connected, messages, send };
}
