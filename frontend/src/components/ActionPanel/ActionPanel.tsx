import { useState, useEffect } from 'react';
import type { GameStateUpdate } from '../../types/game';
import './ActionPanel.css';

interface ActionPanelProps {
  state: GameStateUpdate;
  onAction: (action: string, amount?: number) => void;
}

export function ActionPanel({ state, onAction }: ActionPanelProps) {
  const [raiseAmount, setRaiseAmount] = useState(state.minimumRaise);
  const [inputText, setInputText] = useState(String(state.minimumRaise));
  const isFacingBet = state.callAmount > 0;

  const userPlayer = state.players[0];
  const maxRaise = userPlayer ? userPlayer.chips + userPlayer.currentBet : 0;

  useEffect(() => {
    const bb = Math.floor(state.minimumRaise / 2);
    const defaultRaise = state.phase === 'PRE_FLOP'
      ? Math.min(bb * 3, maxRaise)
      : state.minimumRaise;
    setRaiseAmount(defaultRaise);
    setInputText(String(defaultRaise));
  }, [state.minimumRaise, state.phase, maxRaise]);

  const handleRaise = () => {
    onAction('raise', raiseAmount);
  };

  const handleSliderChange = (value: number) => {
    setRaiseAmount(value);
    setInputText(String(value));
  };

  const handleInputFocus = (e: React.FocusEvent<HTMLInputElement>) => {
    setInputText('');
    e.target.select();
  };

  const handleInputChange = (value: string) => {
    setInputText(value);
    const num = parseInt(value, 10);
    if (!isNaN(num) && num > 0) {
      setRaiseAmount(num);
    }
  };

  const handleInputBlur = () => {
    const clamped = Math.max(state.minimumRaise, Math.min(raiseAmount, maxRaise));
    setRaiseAmount(clamped);
    setInputText(String(clamped));
  };

  const handleInputKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      (e.target as HTMLInputElement).blur();
      handleRaise();
    }
  };

  const raisePreset = (amount: number) => {
    const clamped = Math.min(amount, maxRaise);
    onAction('raise', clamped);
  };

  const isPreFlop = state.phase === 'PRE_FLOP';

  const bb = Math.floor(state.minimumRaise / 2);
  const bbPresets = isPreFlop ? [
    { label: '2x', amount: bb * 2 },
    { label: '2.5x', amount: Math.floor(bb * 2.5) },
  ].filter(s => s.amount >= state.minimumRaise && s.amount <= maxRaise) : [];

  const potSizes = [
    { label: '1/2 Pot', amount: Math.floor(state.pot / 2) },
    { label: '3/4 Pot', amount: Math.floor(state.pot * 3 / 4) },
    { label: 'Pot', amount: state.pot },
  ].filter(s => s.amount >= state.minimumRaise && s.amount <= maxRaise);

  const presets = [...bbPresets, ...potSizes];

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
          onChange={(e) => handleSliderChange(Number(e.target.value))}
          className="raise-slider"
        />
        <input
          type="number"
          min={state.minimumRaise}
          max={maxRaise}
          value={inputText}
          onChange={(e) => handleInputChange(e.target.value)}
          onFocus={handleInputFocus}
          onBlur={handleInputBlur}
          onKeyDown={handleInputKeyDown}
          className="raise-input"
        />
      </div>

      {presets.length > 0 && (
        <div className="raise-presets">
          {presets.map((ps) => (
            <button
              key={ps.label}
              className="btn btn-preset"
              onClick={() => raisePreset(ps.amount)}
            >
              {ps.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
