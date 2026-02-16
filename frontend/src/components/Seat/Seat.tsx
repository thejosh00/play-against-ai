import type { PlayerDto } from '../../types/game';
import { Card } from '../Card/Card';
import './Seat.css';

interface SeatProps {
  player: PlayerDto;
  isCurrentPlayer: boolean;
}

export function Seat({ player, isCurrentPlayer }: SeatProps) {
  const seatClass = [
    'seat',
    player.isFolded ? 'seat-folded' : '',
    player.isSittingOut ? 'seat-sitting-out' : '',
    isCurrentPlayer ? 'seat-active' : '',
    player.isAllIn ? 'seat-all-in' : '',
  ].filter(Boolean).join(' ');

  return (
    <div className={seatClass}>
      <div className="seat-info">
        <div className="seat-name">
          {player.name}
          {player.isDealer && <span className="dealer-btn">D</span>}
        </div>
        {player.playerType && (
          <div className="seat-type">{player.playerType}</div>
        )}
        <div className="seat-chips">${player.chips}</div>
        <div className="seat-position">{player.position}</div>
      </div>

      <div className="seat-cards">
        {player.holeCards ? (
          player.holeCards.map((card, i) => (
            <Card key={i} card={card} />
          ))
        ) : player.isFolded || player.isSittingOut ? null : (
          <>
            <Card faceDown />
            <Card faceDown />
          </>
        )}
      </div>

      {player.lastAction && (
        <div className="seat-action">{player.lastAction}</div>
      )}

      {player.isThinking && (
        <div className="seat-thinking">Thinking...</div>
      )}
    </div>
  );
}
