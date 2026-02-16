import { useState } from 'react';
import type { GameStateUpdate } from '../../types/game';
import './ActionPanel.css';

interface ActionPanelProps {
  state: GameStateUpdate;
  onAction: (action: string, amount?: number) => void;
}

export function ActionPanel({ state, onAction }: ActionPanelProps) {
  const [raiseAmount, setRaiseAmount] = useState(state.minimumRaise);
  const isFacingBet = state.callAmount > 0;

  const handleRaise = () => {
    onAction('raise', raiseAmount);
  };

  const potSizes = [
    { label: '1/2 Pot', amount: Math.floor(state.pot / 2) },
    { label: '3/4 Pot', amount: Math.floor(state.pot * 3 / 4) },
    { label: 'Pot', amount: state.pot },
  ].filter(s => s.amount >= state.minimumRaise);

  const userPlayer = state.players[0];
  const maxRaise = userPlayer ? userPlayer.chips + userPlayer.currentBet : 0;

  return (
    <div className="action-panel">
      <div className="action-buttons">
        {isFacingBet ? (
          <>
            <button className="btn btn-fold" onClick={() => onAction('fold')}>
              Fold
            </button>
            <button className="btn btn-call" onClick={() => onAction('call')}>
              Call ${state.callAmount}
            </button>
          </>
        ) : (
          <button className="btn btn-check" onClick={() => onAction('check')}>
            Check
          </button>
        )}
        <button className="btn btn-raise" onClick={handleRaise}>
          Raise to ${raiseAmount}
        </button>
        <button className="btn btn-allin" onClick={() => onAction('all_in')}>
          All-In
        </button>
      </div>

      <div className="raise-controls">
        <input
          type="range"
          min={state.minimumRaise}
          max={maxRaise}
          value={raiseAmount}
          onChange={(e) => setRaiseAmount(Number(e.target.value))}
          className="raise-slider"
        />
        <div className="raise-presets">
          {potSizes.map((ps) => (
            <button
              key={ps.label}
              className="btn btn-preset"
              onClick={() => setRaiseAmount(Math.min(ps.amount, maxRaise))}
            >
              {ps.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
