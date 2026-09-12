import { LevelMeter } from "./LevelMeter";
import { TunerGauge } from "./TunerGauge";
import type { PitchSession } from "../audio/usePitchSession";

interface ListenPanelProps {
  session: PitchSession;
}

export function ListenPanel({ session }: ListenPanelProps) {
  const debug = session.state?.debug;
  return (
    <div className="panel">
      <h2>Listen</h2>
      <p className="lede">
        Play single notes. The detector uses YIN for the fundamental, then Harmonic Product Spectrum
        to catch piano octave errors. A note only locks after three stable frames.
      </p>
      <TunerGauge
        cents={session.state?.cents ?? null}
        note={session.state?.note ?? null}
        frequency={session.state?.frequency ?? null}
        locked={Boolean(session.state?.midi)}
      />
      <LevelMeter rms={session.liveRms} gate={session.calibration?.gateRms ?? 0.008} />
      <dl className="stats">
        <div>
          <dt>YIN</dt>
          <dd>{debug?.yinHz ? `${debug.yinHz.toFixed(1)} Hz` : "—"}</dd>
        </div>
        <div>
          <dt>HPS</dt>
          <dd>{debug?.hpsHz ? `${debug.hpsHz.toFixed(1)} Hz` : "—"}</dd>
        </div>
        <div>
          <dt>Confidence</dt>
          <dd>{debug ? `${Math.round(debug.probability * 100)}%` : "—"}</dd>
        </div>
        <div>
          <dt>Status</dt>
          <dd>{debug?.reason ?? "idle"}</dd>
        </div>
      </dl>
      {session.error && <p className="error">{session.error}</p>}
      <div className="actions">
        <button className="primary" onClick={() => void session.startMic()} disabled={session.running && session.inputMode === "microphone"}>
          {session.running && session.inputMode === "microphone" ? "Microphone live" : "Enable microphone"}
        </button>
        <button onClick={() => session.useSimulator()}>Simulator</button>
        <button onClick={session.stop} disabled={!session.running}>
          Stop
        </button>
      </div>
    </div>
  );
}
