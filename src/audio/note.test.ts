import { describe, expect, it } from "vitest";
import { centsOff, freqToMidi, midiToFreq, midiToNoteName, noteNameToMidi } from "./note";

describe("note mapping", () => {
  it("maps A4 to 440 Hz and MIDI 69", () => {
    expect(midiToFreq(69)).toBeCloseTo(440, 8);
    expect(freqToMidi(440)).toBe(69);
    expect(midiToNoteName(69)).toBe("A4");
    expect(noteNameToMidi("A4")).toBe(69);
  });

  it("maps C4 and C5 correctly", () => {
    expect(midiToNoteName(60)).toBe("C4");
    expect(freqToMidi(261.63)).toBe(60);
    expect(freqToMidi(523.25)).toBe(72);
  });

  it("reports cents off concert pitch", () => {
    const sharp = midiToFreq(69) * 2 ** (15 / 1200);
    expect(centsOff(sharp, 69)).toBeCloseTo(15, 5);
  });
});
