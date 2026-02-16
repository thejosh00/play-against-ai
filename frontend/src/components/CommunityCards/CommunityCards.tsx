import type { CardDto } from '../../types/game';
import { Card } from '../Card/Card';
import './CommunityCards.css';

interface CommunityCardsProps {
  cards: CardDto[];
}

export function CommunityCards({ cards }: CommunityCardsProps) {
  if (cards.length === 0) return null;

  return (
    <div className="community-cards">
      {cards.map((card, i) => (
        <Card key={i} card={card} />
      ))}
    </div>
  );
}
