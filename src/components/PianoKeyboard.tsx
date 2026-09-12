import { midiToNoteName, NOTE_NAMES } from "../audio/note";

const START_MIDI = 48; // C3
const END_MIDI = 84; // C6

function isBlack(midi: number): boolean {
  const pc = midi % 12;
  return pc === 1 || pc === 3 || pc === 6 || pc === 8 || pc === 10;
}

interface PianoKeyboardProps {
  activeMidi: number | null;
  targetMidi?: number | null;
  onKey: (midi: number) => void;
}

export function PianoKeyboard({ activeMidi, targetMidi, onKey }: PianoKeyboardProps) {
  const whites: number[] = [];
  const blacks: { midi: number; left: number }[] = [];
  let whiteIndex = 0;
  for (let midi = START_MIDI; midi <= END_MIDI; midi++) {
    if (isBlack(midi)) {
      blacks.push({ midi, left: whiteIndex - 0.35 });
    } else {
      whites.push(midi);
      whiteIndex += 1;
    }
  }
  const whiteWidth = 100 / whites.length;

  return (
    <div className="keyboard" role="group" aria-label="Piano keyboard">
      {whites.map((midi, i) => {
        const classes = [
          "key white",
          activeMidi === midi ? "active" : "",
          targetMidi === midi ? "target" : "",
        ].join(" ");
        return (
          <button
            key={midi}
            className={classes}
            style={{ left: `${i * whiteWidth}%`, width: `${whiteWidth}%` }}
            onMouseDown={() => onKey(midi)}
            title={midiToNoteName(midi)}
          >
            <span>{NOTE_NAMES[midi % 12]}{Math.floor(midi / 12) - 1}</span>
          </button>
        );
      })}
      {blacks.map(({ midi, left }) => {
        const classes = [
          "key black",
          activeMidi === midi ? "active" : "",
          targetMidi === midi ? "target" : "",
        ].join(" ");
        return (
          <button
            key={midi}
            className={classes}
            style={{ left: `${left * whiteWidth}%`, width: `${whiteWidth * 0.62}%` }}
            onMouseDown={() => onKey(midi)}
            title={midiToNoteName(midi)}
          />
        );
      })}
    </div>
  );
}
