import './BetChips.css';

const DENOMINATIONS = [
  { value: 500, color: '#7B1FA2', label: 'purple' },
  { value: 100, color: '#212121', label: 'black' },
  { value: 25, color: '#43A047', label: 'green' },
  { value: 5, color: '#E53935', label: 'red' },
  { value: 1, color: '#FFFFFF', label: 'white' },
];

function breakdownChips(amount: number): { color: string; label: string }[] {
  const chips: { color: string; label: string }[] = [];
  let remaining = amount;

  for (const denom of DENOMINATIONS) {
    const count = Math.floor(remaining / denom.value);
    if (count > 0) {
      const visible = Math.min(count, 3);
      for (let i = 0; i < visible; i++) {
        chips.push({ color: denom.color, label: denom.label });
      }
      remaining -= count * denom.value;
    }
  }

  return chips.slice(0, 6);
}

interface BetChipsProps {
  amount: number;
}

export function BetChips({ amount }: BetChipsProps) {
  const chips = breakdownChips(amount);

  return (
    <div className="bet-chips">
      <div className="chip-stack">
        {chips.map((chip, i) => (
          <div
            key={i}
            className={`chip chip-${chip.label}`}
            style={{ backgroundColor: chip.color }}
          />
        ))}
      </div>
      <div className="bet-amount">${amount}</div>
    </div>
  );
}
