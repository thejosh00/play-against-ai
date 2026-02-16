import { useEffect, useRef, useState } from 'react';
import type {
  GameStateUpdate,
  ActionPerformed,
  HandResult,
  TournamentUpdate,
  TournamentFinished,
  ServerMessage,
} from '../types/game';

export interface GameData {
  state: GameStateUpdate | null;
  handResult: HandResult | null;
  actionLog: ActionPerformed[];
  tournamentInfo: TournamentUpdate | null;
  tournamentFinished: TournamentFinished | null;
}

export function useGameState(messages: ServerMessage[]): GameData {
  const [state, setState] = useState<GameStateUpdate | null>(null);
  const [handResult, setHandResult] = useState<HandResult | null>(null);
  const [actionLog, setActionLog] = useState<ActionPerformed[]>([]);
  const [tournamentInfo, setTournamentInfo] = useState<TournamentUpdate | null>(null);
  const [tournamentFinished, setTournamentFinished] = useState<TournamentFinished | null>(null);
  const lastProcessedIndex = useRef(0);

  useEffect(() => {
    for (let i = lastProcessedIndex.current; i < messages.length; i++) {
      const msg = messages[i];

      switch (msg.type) {
        case 'game_state':
          setState(msg);
          if (msg.phase === 'PRE_FLOP') {
            setHandResult(null);
            setActionLog([]);
          }
          break;
        case 'action_performed':
          setActionLog((prev) => [...prev, msg]);
          break;
        case 'hand_result':
          setHandResult(msg);
          break;
        case 'player_eliminated':
          setActionLog((prev) => [
            ...prev,
            {
              type: 'action_performed' as const,
              playerIndex: msg.playerIndex,
              playerName: msg.playerName,
              action: `${msg.playerName} has been eliminated`,
              phase: 'HAND_COMPLETE',
            },
          ]);
          break;
        case 'player_reloaded':
          setActionLog((prev) => [
            ...prev,
            {
              type: 'action_performed' as const,
              playerIndex: msg.playerIndex,
              playerName: msg.playerName,
              action: `${msg.playerName} reloads for $${msg.chips}`,
              phase: 'HAND_COMPLETE',
            },
          ]);
          break;
        case 'player_joined':
          setActionLog((prev) => [
            ...prev,
            {
              type: 'action_performed' as const,
              playerIndex: msg.playerIndex,
              playerName: msg.playerName,
              action: `${msg.playerName} joins the table with $${msg.chips}`,
              phase: 'HAND_COMPLETE',
            },
          ]);
          break;
        case 'tournament_update':
          setTournamentInfo(msg);
          break;
        case 'tournament_finished':
          setTournamentFinished(msg);
          break;
        case 'error':
          console.error('Server error:', msg.message);
          break;
      }
    }
    lastProcessedIndex.current = messages.length;
  }, [messages.length]);

  return { state, handResult, actionLog, tournamentInfo, tournamentFinished };
}
