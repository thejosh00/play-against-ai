import './Controls.css';

interface ControlsProps {
  showAiCards: boolean;
  showPlayerTypes: boolean;
  showStats: boolean;
  onToggle: (setting: string, value: boolean) => void;
  onDealNextHand: () => void;
  canDeal: boolean;
}

export function Controls({
  showAiCards,
  showPlayerTypes,
  showStats,
  onToggle,
  onDealNextHand,
  canDeal,
}: ControlsProps) {
  return (
    <div className="controls">
      <label className="toggle">
        <input
          type="checkbox"
          checked={showAiCards}
          onChange={(e) => onToggle('showAiCards', e.target.checked)}
        />
        <span>Show AI Cards</span>
      </label>

      <label className="toggle">
        <input
          type="checkbox"
          checked={showPlayerTypes}
          onChange={(e) => onToggle('showPlayerTypes', e.target.checked)}
        />
        <span>Show Player Types</span>
      </label>

      <label className="toggle">
        <input
          type="checkbox"
          checked={showStats}
          onChange={(e) => onToggle('showStats', e.target.checked)}
        />
        <span>Show Stats</span>
      </label>

      {canDeal && (
        <button className="btn btn-deal" onClick={onDealNextHand}>
          Deal Next Hand
        </button>
      )}
    </div>
  );
}
