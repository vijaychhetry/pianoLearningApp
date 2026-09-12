export const A4_HZ = 440;
export const A4_MIDI = 69;
export const MIN_PIANO_MIDI = 21; // A0
export const MAX_PIANO_MIDI = 108; // C8
export const NOTE_NAMES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"] as const;

export function midiToFreq(midi: number, a4Hz = A4_HZ): number {
  return a4Hz * 2 ** ((midi - A4_MIDI) / 12);
}

export function freqToMidiFloat(freq: number, a4Hz = A4_HZ): number {
  return A4_MIDI + 12 * Math.log2(freq / a4Hz);
}

export function freqToMidi(freq: number, a4Hz = A4_HZ): number {
  return Math.round(freqToMidiFloat(freq, a4Hz));
}

export function centsOff(freq: number, midi: number, a4Hz = A4_HZ): number {
  const target = midiToFreq(midi, a4Hz);
  return 1200 * Math.log2(freq / target);
}

export function midiToNoteName(midi: number): string {
  const name = NOTE_NAMES[((midi % 12) + 12) % 12];
  const octave = Math.floor(midi / 12) - 1;
  return `${name}${octave}`;
}

export function noteNameToMidi(name: string): number | null {
  const match = name.trim().match(/^([A-Ga-g])([#b]?)(-?\d+)$/);
  if (!match) return null;
  const letter = match[1].toUpperCase();
  const accidental = match[2];
  const octave = Number(match[3]);
  const base: Record<string, number> = { C: 0, D: 2, E: 4, F: 5, G: 7, A: 9, B: 11 };
  let pc = base[letter];
  if (accidental === "#") pc += 1;
  if (accidental === "b") pc -= 1;
  return (octave + 1) * 12 + ((pc + 12) % 12);
}

export function applyCentsOffset(freq: number, offsetCents: number): number {
  return freq * 2 ** (offsetCents / 1200);
}

export function clampMidi(midi: number): number {
  return Math.min(MAX_PIANO_MIDI, Math.max(MIN_PIANO_MIDI, midi));
}
