import { useState } from 'react';
import { useWebSocket } from './hooks/useWebSocket';
import { useGameState } from './hooks/useGameState';
import { Table } from './components/Table/Table';
import { ActionPanel } from './components/ActionPanel/ActionPanel';
import { Controls } from './components/Controls/Controls';
import { HandHistory } from './components/HandHistory/HandHistory';
import { Lobby } from './components/Lobby/Lobby';
import { GameInfo } from './components/GameInfo/GameInfo';
import type { GameConfig } from './types/game';
import './App.css';

function App() {
  const { connected, messages, send } = useWebSocket('ws://localhost:8080/game');
  const { state, handResult, actionLog, tournamentInfo, tournamentFinished } = useGameState(messages);
  const [gameStarted, setGameStarted] = useState(false);

  const startGame = (config: GameConfig) => {
    send({ type: 'start_game', playerName: 'You', config });
    setGameStarted(true);
  };

  const handleAction = (action: string, amount?: number) => {
    send({ type: 'player_action', action, amount: amount ?? null });
  };

  const handleToggle = (setting: string, value: boolean) => {
    send({ type: 'toggle_setting', setting, value });
  };

  const handleDealNextHand = () => {
    send({ type: 'deal_next_hand' });
  };

  if (!gameStarted || !state) {
    return (
      <div className="app">
        <Lobby onStartGame={startGame} connected={connected} />
      </div>
    );
  }

  const canDeal = state.phase === 'HAND_COMPLETE' || state.phase === 'SHOWDOWN';

  return (
    <div className="app">
      <div className="game-header">
        <span>Hand #{state.handNumber}</span>
        <span className="phase-badge">{state.phase.replace('_', ' ')}</span>
        <GameInfo state={state} tournamentInfo={tournamentInfo} />
      </div>

      <Controls
        showAiCards={state.showAiCards}
        showPlayerTypes={state.showPlayerTypes}
        onToggle={handleToggle}
        onDealNextHand={handleDealNextHand}
        canDeal={canDeal}
      />

      <Table state={state} />

      {state.isUserTurn && (
        <ActionPanel state={state} onAction={handleAction} />
      )}

      {handResult && (
        <div className="hand-result-banner">
          {handResult.summary}
        </div>
      )}

      {tournamentFinished && (
        <div className="tournament-finished-banner">
          {tournamentFinished.finishPosition === 1
            ? `You won the tournament! (1st of ${tournamentFinished.totalPlayers})`
            : `Tournament over! You finished ${tournamentFinished.finishPosition}${getOrdinalSuffix(tournamentFinished.finishPosition)} of ${tournamentFinished.totalPlayers}`}
        </div>
      )}

      <HandHistory actions={actionLog} result={handResult} />
    </div>
  );
}

function getOrdinalSuffix(n: number): string {
  const mod10 = n % 10;
  const mod100 = n % 100;
  if (mod10 === 1 && mod100 !== 11) return 'st';
  if (mod10 === 2 && mod100 !== 12) return 'nd';
  if (mod10 === 3 && mod100 !== 13) return 'rd';
  return 'th';
}

export default App;
