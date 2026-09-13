interface TunerGaugeProps {
  cents: number | null;
  note: string | null;
  frequency: number | null;
  locked: boolean;
}

export function TunerGauge({ cents, note, frequency, locked }: TunerGaugeProps) {
  const clamped = Math.max(-50, Math.min(50, cents ?? 0));
  const angle = (clamped / 50) * 50;
  return (
    <div className="tuner">
      <div className={`note-hero ${locked ? "locked" : ""}`}>
        <div className="note-name">{note ?? "—"}</div>
        <div className="note-hz">{frequency ? `${frequency.toFixed(1)} Hz` : "listening"}</div>
      </div>
      <div className="needle-wrap">
        <div className="needle-scale">
          <span>-50</span>
          <span>0</span>
          <span>+50</span>
        </div>
        <div className="needle-track">
          <div className="needle-center" />
          <div
            className={`needle ${cents === null ? "idle" : ""}`}
            style={{ transform: `translateX(-50%) rotate(${angle}deg)` }}
          />
        </div>
        <div className="cents-label">
          {cents === null ? "cents" : `${cents > 0 ? "+" : ""}${cents.toFixed(0)} cents`}
        </div>
      </div>
    </div>
  );
}
