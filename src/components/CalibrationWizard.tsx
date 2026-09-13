import { useState } from "react";
import type { PitchSession } from "../audio/usePitchSession";
import {
  clearCalibration,
  defaultCalibration,
  measureNoiseFloor,
  measureReferencePitch,
  mergeCalibration,
  saveCalibration,
} from "../audio/calibration";
import { LevelMeter } from "./LevelMeter";
import { midiToNoteName } from "../audio/note";

interface CalibrationWizardProps {
  session: PitchSession;
}

type Step = "intro" | "silence" | "a4" | "done";

export function CalibrationWizard({ session }: CalibrationWizardProps) {
  const [step, setStep] = useState<Step>("intro");
  const [busy, setBusy] = useState(false);
  const [log, setLog] = useState<string[]>([]);
  const [quality, setQuality] = useState(session.calibration?.quality ?? "ok");

  const ensureInput = async () => {
    if (session.running) return;
    if (session.inputMode === "simulator") {
      session.useSimulator();
      return;
    }
    await session.startMic();
  };

  const runSilence = async () => {
    setBusy(true);
    setLog(["Stay quiet for 2 seconds…"]);
    await ensureInput();
    const frames = await session.captureFrames(2000);
    const noise = measureNoiseFloor(frames);
    const draft = mergeCalibration(
      noise,
      {
        measuredHz: 440,
        pitchOffsetCents: session.calibration?.pitchOffsetCents ?? 0,
        a4Hz: 440,
        quality: "ok",
        notes: ["Pitch offset kept from previous calibration until A4 is captured."],
      },
      session.sampleRate,
    );
    session.applyCalibration(draft);
    saveCalibration(draft);
    setQuality(draft.quality);
    setLog(noise.notes.concat(`Gate set to RMS ${draft.gateRms.toFixed(4)}`));
    setBusy(false);
    setStep("a4");
  };

  const runA4 = async () => {
    setBusy(true);
    setLog(["Play and hold A4 (the A above middle C)…"]);
    await ensureInput();
    if (session.inputMode === "simulator") {
      session.playSimulatedNote(69);
    }
    const frames = await session.captureFrames(2500);
    const noise = {
      noiseFloorRms: session.calibration?.noiseFloorRms ?? 0.002,
      gateRms: session.calibration?.gateRms ?? 0.008,
      quality: session.calibration?.quality ?? "ok" as const,
      notes: [] as string[],
    };
    const pitch = measureReferencePitch(frames, session.sampleRate);
    const merged = mergeCalibration(noise, pitch, session.sampleRate);
    session.applyCalibration(merged);
    saveCalibration(merged);
    setQuality(merged.quality);
    setLog(pitch.notes.concat(`Measured ${pitch.measuredHz.toFixed(1)} Hz`));
    setBusy(false);
    setStep("done");
  };

  const skipPitch = () => {
    const next = session.calibration ?? defaultCalibration();
    const skipped = {
      ...next,
      pitchOffsetCents: 0,
      a4Hz: 440,
      notes: [...next.notes, "Skipped A4. Using concert pitch A440."],
    };
    session.applyCalibration(skipped);
    saveCalibration(skipped);
    setStep("done");
    setLog(["Using A440 with no piano offset."]);
  };

  const reset = () => {
    clearCalibration();
    const factory = defaultCalibration();
    session.applyCalibration(factory);
    saveCalibration(factory);
    setStep("intro");
    setLog(["Calibration cleared."]);
  };

  const cal = session.calibration;

  return (
    <div className="panel">
      <h2>Calibration</h2>
      <p className="lede">
        Two measurements fix the usual failures: a silence floor so quiet notes are not eaten by the
        gate, and an A4 reference so a piano that is not at 440 Hz still maps to the right keys.
      </p>

      <ol className="steps">
        <li className={step === "intro" ? "current" : ""}>Enable input</li>
        <li className={step === "silence" ? "current" : ""}>Silence floor</li>
        <li className={step === "a4" ? "current" : ""}>A4 reference</li>
        <li className={step === "done" ? "current" : ""}>Confirm</li>
      </ol>

      <LevelMeter rms={session.liveRms} gate={cal?.gateRms ?? 0.008} />

      {step === "intro" && (
        <div className="actions">
          <button className="primary" onClick={() => void session.startMic()}>
            Use microphone
          </button>
          <button onClick={() => { session.useSimulator(); setStep("silence"); }}>
            Use simulator (no mic)
          </button>
          <button onClick={() => setStep("silence")} disabled={!session.running}>
            Next
          </button>
        </div>
      )}

      {step === "silence" && (
        <div className="actions">
          <p>Stop playing. The app will listen to room noise and set the gate above it.</p>
          <button className="primary" disabled={busy} onClick={() => void runSilence()}>
            {busy ? "Listening…" : "Capture silence"}
          </button>
        </div>
      )}

      {step === "a4" && (
        <div className="actions">
          <p>
            Play <strong>A4</strong> ({midiToNoteName(69)}, 440 Hz) and hold it. In simulator mode the
            app plays it for you.
          </p>
          <button className="primary" disabled={busy} onClick={() => void runA4()}>
            {busy ? "Listening…" : "Capture A4"}
          </button>
          <button disabled={busy} onClick={skipPitch}>
            Skip — use A440
          </button>
        </div>
      )}

      {step === "done" && (
        <div className="actions">
          <p className={`quality ${quality}`}>Calibration quality: {quality}</p>
          <button className="primary" onClick={() => setStep("silence")}>
            Recalibrate
          </button>
          <button onClick={reset}>Reset to defaults</button>
        </div>
      )}

      {session.error && <p className="error">{session.error}</p>}

      <dl className="stats">
        <div>
          <dt>Noise floor</dt>
          <dd>{cal ? cal.noiseFloorRms.toFixed(4) : "—"}</dd>
        </div>
        <div>
          <dt>Gate</dt>
          <dd>{cal ? cal.gateRms.toFixed(4) : "—"}</dd>
        </div>
        <div>
          <dt>Pitch offset</dt>
          <dd>{cal ? `${cal.pitchOffsetCents.toFixed(1)} cents` : "—"}</dd>
        </div>
        <div>
          <dt>Sample rate</dt>
          <dd>{session.sampleRate} Hz</dd>
        </div>
      </dl>

      {log.length > 0 && (
        <ul className="log">
          {log.map((line) => (
            <li key={line}>{line}</li>
          ))}
        </ul>
      )}
    </div>
  );
}
