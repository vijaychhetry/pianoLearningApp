import { useCallback, useEffect, useState } from "react";
import { usePitchSession } from "./audio/usePitchSession";
import { PianoKeyboard } from "./components/PianoKeyboard";
import { CalibrationWizard } from "./components/CalibrationWizard";
import { ListenPanel } from "./components/ListenPanel";
import { PracticePanel } from "./components/PracticePanel";
import { midiToNoteName } from "./audio/note";

type Tab = "listen" | "calibrate" | "practice";

export function App() {
  const session = usePitchSession();
  const [tab, setTab] = useState<Tab>("listen");
  const [practiceTarget, setPracticeTarget] = useState(60);
  const [heldMidi, setHeldMidi] = useState<number | null>(null);
  const onTargetChange = useCallback((midi: number) => setPracticeTarget(midi), []);
  const liveMidi = session.state?.midi ?? null;

  useEffect(() => {
    if (liveMidi !== null) {
      setHeldMidi(liveMidi);
      return;
    }
    const timer = window.setTimeout(() => setHeldMidi(null), 1400);
    return () => window.clearTimeout(timer);
  }, [liveMidi]);

  const onKey = (midi: number) => {
    if (!session.running || session.inputMode !== "simulator") {
      session.useSimulator();
    }
    session.playSimulatedNote(midi);
  };

  const displayMidi = liveMidi ?? heldMidi;

  return (
    <div className="app">
      <header className="top">
        <div>
          <p className="eyebrow">Piano Learning</p>
          <h1>Microphone note recognition</h1>
        </div>
        <nav>
          {(["listen", "calibrate", "practice"] as Tab[]).map((id) => (
            <button
              key={id}
              className={tab === id ? "tab active" : "tab"}
              onClick={() => setTab(id)}
            >
              {id}
            </button>
          ))}
        </nav>
      </header>

      <main>
        {tab === "listen" && <ListenPanel session={session} displayMidi={displayMidi} />}
        {tab === "calibrate" && <CalibrationWizard session={session} />}
        {tab === "practice" && (
          <PracticePanel session={session} target={practiceTarget} onTargetChange={onTargetChange} />
        )}
      </main>

      <section className="board">
        <div className="board-meta">
          <span>
            Input: <strong>{session.inputMode}</strong>
            {session.running ? " · live" : " · stopped"}
          </span>
          <span>
            Heard: <strong>{displayMidi !== null ? midiToNoteName(displayMidi) : "none"}</strong>
          </span>
          <span>
            Cal: <strong>{session.calibration?.quality ?? "unset"}</strong>
          </span>
        </div>
        <PianoKeyboard
          activeMidi={displayMidi}
          targetMidi={tab === "practice" ? practiceTarget : null}
          onKey={onKey}
        />
        <p className="hint">
          Click a key to inject a harmonic piano tone into the detector (works without a microphone).
          Live piano: enable microphone, then play single notes near the computer.
        </p>
      </section>
    </div>
  );
}
