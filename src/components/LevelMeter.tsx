interface LevelMeterProps {
  rms: number;
  gate: number;
}

export function LevelMeter({ rms, gate }: LevelMeterProps) {
  const pct = Math.min(100, (rms / 0.2) * 100);
  const gatePct = Math.min(100, (gate / 0.2) * 100);
  const above = rms >= gate;
  return (
    <div className="meter">
      <div className="meter-label">
        <span>Mic level</span>
        <span className={above ? "ok" : "muted"}>{above ? "signal" : "below gate"}</span>
      </div>
      <div className="meter-track">
        <div className={`meter-fill ${above ? "hot" : ""}`} style={{ width: `${pct}%` }} />
        <div className="meter-gate" style={{ left: `${gatePct}%` }} title="Noise gate" />
      </div>
    </div>
  );
}
