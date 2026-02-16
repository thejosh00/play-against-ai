import type { GameStateUpdate } from '../../types/game';
import { Seat } from '../Seat/Seat';
import { BetChips } from '../BetChips/BetChips';
import { CommunityCards } from '../CommunityCards/CommunityCards';
import { Pot } from '../Pot/Pot';
import './Table.css';

interface TableProps {
  state: GameStateUpdate;
}

// Seat positions arranged clockwise around the oval: user at bottom center
const seatPositions6 = [
  { className: 'seat-pos-0' }, // bottom center (user)
  { className: 'seat-pos-1' }, // bottom left
  { className: 'seat-pos-2' }, // top left
  { className: 'seat-pos-3' }, // top center
  { className: 'seat-pos-4' }, // top right
  { className: 'seat-pos-5' }, // bottom right
];

const seatPositions9 = [
  { className: 'seat-pos-0' }, // bottom center (user)
  { className: 'seat-pos-1' }, // bottom left
  { className: 'seat-pos-2' }, // left
  { className: 'seat-pos-6' }, // top-left
  { className: 'seat-pos-3' }, // top center-left
  { className: 'seat-pos-7' }, // top center-right
  { className: 'seat-pos-4' }, // top-right
  { className: 'seat-pos-5' }, // right
  { className: 'seat-pos-8' }, // bottom-right
];

export function Table({ state }: TableProps) {
  const seatPositions = state.players.length > 6 ? seatPositions9 : seatPositions6;

  return (
    <div className="table">
      <div className="table-felt">
        <div className="table-center">
          <Pot amount={state.pot} />
          <CommunityCards cards={state.communityCards} />
        </div>

        {state.players.map((player, i) => (
          <div key={i} className={`seat-position ${seatPositions[i]?.className || ''}`}>
            <Seat
              player={player}
              isCurrentPlayer={state.currentPlayerIndex === i}
            />
            {player.currentBet > 0 && (
              <div className={`bet-pos bet-pos-${seatPositions[i]?.className.replace('seat-pos-', '') || i}`}>
                <BetChips amount={player.currentBet} />
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
