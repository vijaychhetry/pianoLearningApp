import { useEffect, useRef, useState } from "react";
import type { PitchSession } from "../audio/usePitchSession";
import { midiToNoteName } from "../audio/note";

export const PRACTICE_NOTES = [60, 62, 64, 65, 67, 69, 71, 72];

interface PracticePanelProps {
  session: PitchSession;
  target: number;
  onTargetChange: (midi: number) => void;
}

export function pickPracticeNote(except?: number): number {
  const pool = PRACTICE_NOTES.filter((n) => n !== except);
  return pool[Math.floor(Math.random() * pool.length)];
}

export function PracticePanel({ session, target, onTargetChange }: PracticePanelProps) {
  const [score, setScore] = useState({ hits: 0, tries: 0 });
  const [feedback, setFeedback] = useState("Play the highlighted note");
  const handledRef = useRef<number | null>(null);
  const detected = session.state?.midi ?? null;

  useEffect(() => {
    if (detected === null) {
      handledRef.current = null;
      return;
    }
    if (handledRef.current === detected) return;
    handledRef.current = detected;
    if (detected === target) {
      setScore((s) => ({ hits: s.hits + 1, tries: s.tries + 1 }));
      setFeedback(`Yes — ${midiToNoteName(target)}`);
      const next = pickPracticeNote(target);
      const timer = window.setTimeout(() => {
        onTargetChange(next);
        setFeedback("Play the highlighted note");
        handledRef.current = null;
      }, 700);
      return () => window.clearTimeout(timer);
    }
    setScore((s) => ({ ...s, tries: s.tries + 1 }));
    setFeedback(`Heard ${midiToNoteName(detected)} — try ${midiToNoteName(target)}`);
  }, [detected, target, onTargetChange]);

  const accuracy = score.tries === 0 ? 0 : Math.round((score.hits / score.tries) * 100);

  return (
    <div className="panel">
      <h2>Practice</h2>
      <p className="lede">Play the target note on your piano or click the keyboard (simulator).</p>
      <div className="practice-hero">
        <div className="target-note">{midiToNoteName(target)}</div>
        <p>{feedback}</p>
        <p className="score">
          {score.hits} correct{score.tries > 0 ? ` · ${accuracy}%` : ""}
        </p>
      </div>
      <div className="actions">
        <button
          onClick={() => {
            handledRef.current = null;
            onTargetChange(pickPracticeNote(target));
            setFeedback("Play the highlighted note");
          }}
        >
          Skip
        </button>
        <button onClick={() => setScore({ hits: 0, tries: 0 })}>Reset score</button>
      </div>
    </div>
  );
}
