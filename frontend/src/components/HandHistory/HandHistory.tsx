import type { ActionPerformed, HandResult } from '../../types/game';
import './HandHistory.css';

interface HandHistoryProps {
  actions: ActionPerformed[];
  result: HandResult | null;
}

export function HandHistory({ actions, result }: HandHistoryProps) {
  return (
    <div className="hand-history">
      <div className="hand-history-title">Hand History</div>
      <div className="hand-history-log">
        {actions.map((a, i) => (
          <div key={i} className="history-entry">
            <span className="history-phase">[{a.phase}]</span>{' '}
            <span className="history-player">{a.playerName}</span>:{' '}
            <span className="history-action">{a.action}</span>
          </div>
        ))}
        {result && (
          <div className="history-entry history-result">
            {result.summary}
          </div>
        )}
      </div>
    </div>
  );
}
