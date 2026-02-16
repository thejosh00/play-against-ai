import type { CardDto } from '../../types/game';
import './Card.css';

const suitSymbols: Record<string, string> = {
  h: '♥', d: '♦', c: '♣', s: '♠',
};
const suitColors: Record<string, string> = {
  h: '#e74c3c', d: '#3498db', c: '#2ecc71', s: '#2c3e50',
};
const rankDisplay: Record<string, string> = {
  'T': '10', 'J': 'J', 'Q': 'Q', 'K': 'K', 'A': 'A',
};

interface CardProps {
  card?: CardDto | null;
  faceDown?: boolean;
}

export function Card({ card, faceDown }: CardProps) {
  if (!card || faceDown) {
    return (
      <div className="card card-back">
        <div className="card-back-pattern" />
      </div>
    );
  }

  const rank = rankDisplay[card.rank] || card.rank;
  const suit = suitSymbols[card.suit] || card.suit;
  const color = suitColors[card.suit] || '#000';

  return (
    <div className="card card-face" style={{ color }}>
      <div className="card-rank">{rank}</div>
      <div className="card-suit">{suit}</div>
    </div>
  );
}
