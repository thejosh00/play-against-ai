import './Pot.css';

interface PotProps {
  amount: number;
}

export function Pot({ amount }: PotProps) {
  if (amount <= 0) return null;
  return (
    <div className="pot">
      <span className="pot-label">Pot:</span> ${amount}
    </div>
  );
}
