import { useState } from 'react';
import type { GameConfig, CashStakes, TournamentBuyin } from '../../types/game';
import './Lobby.css';

interface LobbyProps {
  onStartGame: (config: GameConfig) => void;
  connected: boolean;
}

type GameType = 'cash' | 'tournament' | null;

const CASH_OPTIONS: { value: CashStakes; label: string; desc: string }[] = [
  { value: 'ONE_TWO', label: '$1/$2', desc: '200 chips - Easy' },
  { value: 'TWO_FIVE', label: '$2/$5', desc: '500 chips - Medium' },
  { value: 'FIVE_TEN', label: '$5/$10', desc: '1,000 chips - Hard' },
];

const TOURNAMENT_OPTIONS: { value: TournamentBuyin; label: string; desc: string }[] = [
  { value: 'HUNDRED', label: '$100', desc: '10,000 chips - Easy' },
  { value: 'FIVE_HUNDRED', label: '$500', desc: '10,000 chips - Medium' },
  { value: 'FIFTEEN_HUNDRED', label: '$1,500', desc: '10,000 chips - Hard' },
];

const PLAYER_COUNTS = [6, 45, 180, 1000] as const;

export function Lobby({ onStartGame, connected }: LobbyProps) {
  const [gameType, setGameType] = useState<GameType>(null);
  const [cashStakes, setCashStakes] = useState<CashStakes>('TWO_FIVE');
  const [tournamentBuyin, setTournamentBuyin] = useState<TournamentBuyin>('FIVE_HUNDRED');
  const [rakeEnabled, setRakeEnabled] = useState(false);
  const [antesEnabled, setAntesEnabled] = useState(false);
  const [playerCount, setPlayerCount] = useState<number>(45);
  const [tableSize, setTableSize] = useState<number>(6);

  const handleStart = () => {
    if (gameType === 'cash') {
      onStartGame({ type: 'cash', stakes: cashStakes, rakeEnabled, tableSize });
    } else if (gameType === 'tournament') {
      onStartGame({ type: 'tournament', buyin: tournamentBuyin, playerCount, antesEnabled, tableSize });
    }
  };

  return (
    <div className="lobby">
      <h1 className="lobby-title">Play Against AI</h1>
      <p className="lobby-subtitle">Texas Hold'em vs AI Opponents</p>

      {!connected && <p className="lobby-connecting">Connecting to server...</p>}

      <div className="lobby-section">
        <h2>Game Type</h2>
        <div className="lobby-cards">
          <button
            className={`lobby-card ${gameType === 'cash' ? 'selected' : ''}`}
            onClick={() => setGameType('cash')}
          >
            <span className="lobby-card-title">Cash Game</span>
            <span className="lobby-card-desc">Unlimited rebuys, consistent blinds</span>
          </button>
          <button
            className={`lobby-card ${gameType === 'tournament' ? 'selected' : ''}`}
            onClick={() => setGameType('tournament')}
          >
            <span className="lobby-card-title">Tournament</span>
            <span className="lobby-card-desc">Increasing blinds, elimination format</span>
          </button>
        </div>
      </div>

      {gameType && (
        <div className="lobby-section">
          <h2>Table Size</h2>
          <div className="lobby-cards">
            <button
              className={`lobby-card ${tableSize === 6 ? 'selected' : ''}`}
              onClick={() => setTableSize(6)}
            >
              <span className="lobby-card-title">6-Max</span>
              <span className="lobby-card-desc">6 players per table</span>
            </button>
            <button
              className={`lobby-card ${tableSize === 9 ? 'selected' : ''}`}
              onClick={() => setTableSize(9)}
            >
              <span className="lobby-card-title">9-Max</span>
              <span className="lobby-card-desc">9 players per table</span>
            </button>
          </div>
        </div>
      )}

      {gameType === 'cash' && (
        <>
          <div className="lobby-section">
            <h2>Stakes</h2>
            <div className="lobby-cards">
              {CASH_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  className={`lobby-card ${cashStakes === opt.value ? 'selected' : ''}`}
                  onClick={() => setCashStakes(opt.value)}
                >
                  <span className="lobby-card-title">{opt.label}</span>
                  <span className="lobby-card-desc">{opt.desc}</span>
                </button>
              ))}
            </div>
          </div>
          <div className="lobby-section">
            <h2>Options</h2>
            <label className="lobby-toggle">
              <input
                type="checkbox"
                checked={rakeEnabled}
                onChange={(e) => setRakeEnabled(e.target.checked)}
              />
              <span>Rake (5% with cap)</span>
            </label>
          </div>
        </>
      )}

      {gameType === 'tournament' && (
        <>
          <div className="lobby-section">
            <h2>Buy-in</h2>
            <div className="lobby-cards">
              {TOURNAMENT_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  className={`lobby-card ${tournamentBuyin === opt.value ? 'selected' : ''}`}
                  onClick={() => setTournamentBuyin(opt.value)}
                >
                  <span className="lobby-card-title">{opt.label}</span>
                  <span className="lobby-card-desc">{opt.desc}</span>
                </button>
              ))}
            </div>
          </div>
          <div className="lobby-section">
            <h2>Options</h2>
            <label className="lobby-toggle">
              <input
                type="checkbox"
                checked={antesEnabled}
                onChange={(e) => setAntesEnabled(e.target.checked)}
              />
              <span>Antes (starting at level 4)</span>
            </label>
            <div className="lobby-player-count">
              <span>Players:</span>
              <div className="lobby-count-options">
                {PLAYER_COUNTS.map((count) => (
                  <button
                    key={count}
                    className={`lobby-count-btn ${playerCount === count ? 'selected' : ''}`}
                    onClick={() => setPlayerCount(count)}
                  >
                    {count}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </>
      )}

      {gameType && (
        <button className="lobby-start" onClick={handleStart} disabled={!connected}>
          Start Game
        </button>
      )}
    </div>
  );
}
