import type { GameStateUpdate, TournamentUpdate } from '../../types/game';
import './GameInfo.css';

interface GameInfoProps {
  state: GameStateUpdate;
  tournamentInfo: TournamentUpdate | null;
}

export function GameInfo({ state, tournamentInfo }: GameInfoProps) {
  if (tournamentInfo) {
    return (
      <div className="game-info">
        <span className="game-info-label">{state.gameLabel}</span>
        <span className="game-info-players">
          {tournamentInfo.remainingPlayers}/{tournamentInfo.totalPlayers}
        </span>
        <span className="game-info-level">
          Level {tournamentInfo.blindLevel}: {tournamentInfo.smallBlind}/{tournamentInfo.bigBlind}
        </span>
        {tournamentInfo.ante > 0 && (
          <span className="game-info-ante">Ante {tournamentInfo.ante}</span>
        )}
        {tournamentInfo.handsUntilNextLevel > 0 && (
          <span className="game-info-next">
            Next level in {tournamentInfo.handsUntilNextLevel} hands
          </span>
        )}
      </div>
    );
  }

  if (state.gameLabel) {
    return (
      <div className="game-info">
        <span className="game-info-label">{state.gameLabel}</span>
      </div>
    );
  }

  return null;
}
