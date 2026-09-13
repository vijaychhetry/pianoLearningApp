import { useCallback, useEffect, useRef, useState } from "react";
import { NoteDetector, type Calibration, type DetectorState, type FrameDebug } from "./detector";
import { defaultCalibration, loadCalibration } from "./calibration";
import { midiToFreq } from "./note";
import { pianoTone } from "./synth";

export type InputMode = "microphone" | "simulator";

export interface PitchSession {
  running: boolean;
  error: string | null;
  inputMode: InputMode;
  calibration: Calibration | null;
  state: DetectorState | null;
  liveRms: number;
  sampleRate: number;
  startMic: () => Promise<void>;
  stop: () => void;
  useSimulator: () => void;
  playSimulatedNote: (midi: number) => void;
  captureFrames: (ms: number) => Promise<Float32Array[]>;
  applyCalibration: (next: Calibration) => void;
  setCalibration: (next: Calibration | null) => void;
}

const FRAME_SIZE = 2048;

const idleState = (): DetectorState => ({
  midi: null,
  note: null,
  frequency: null,
  cents: null,
  debug: {
    rms: 0,
    gate: 0.008,
    frequency: null,
    yinHz: null,
    hpsHz: null,
    probability: 0,
    midi: null,
    note: null,
    cents: null,
    accepted: false,
    reason: "idle",
  },
});

export function usePitchSession(): PitchSession {
  const detectorRef = useRef(new NoteDetector());
  const audioRef = useRef<AudioContext | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const processorRef = useRef<ScriptProcessorNode | AnalyserNode | null>(null);
  const sourceRef = useRef<MediaStreamAudioSourceNode | null>(null);
  const rafRef = useRef<number>(0);
  const bufferRef = useRef(new Float32Array(FRAME_SIZE));
  const pendingFramesRef = useRef<Float32Array[] | null>(null);
  const simBufferRef = useRef<Float32Array | null>(null);
  const simOffsetRef = useRef(0);

  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [inputMode, setInputMode] = useState<InputMode>("microphone");
  const [calibration, setCalibrationState] = useState<Calibration | null>(null);
  const [state, setState] = useState<DetectorState | null>(idleState());
  const [liveRms, setLiveRms] = useState(0);
  const [sampleRate, setSampleRate] = useState(48000);

  useEffect(() => {
    const stored = loadCalibration() ?? defaultCalibration();
    setCalibrationState(stored);
    detectorRef.current.setCalibration(stored);
  }, []);

  const stopGraph = useCallback(() => {
    cancelAnimationFrame(rafRef.current);
    processorRef.current?.disconnect();
    sourceRef.current?.disconnect();
    streamRef.current?.getTracks().forEach((t) => t.stop());
    processorRef.current = null;
    sourceRef.current = null;
    streamRef.current = null;
    void audioRef.current?.close();
    audioRef.current = null;
    detectorRef.current.reset();
    setRunning(false);
  }, []);

  const ingest = useCallback((samples: Float32Array, rate: number) => {
    if (pendingFramesRef.current) {
      pendingFramesRef.current.push(new Float32Array(samples));
    }
    const next = detectorRef.current.process(samples, rate);
    setLiveRms(next.debug.rms);
    setState(next);
  }, []);

  const startAnalyserLoop = useCallback((ctx: AudioContext, node: AnalyserNode) => {
    node.fftSize = FRAME_SIZE * 2;
    node.smoothingTimeConstant = 0;
    const tick = () => {
      node.getFloatTimeDomainData(bufferRef.current);
      ingest(bufferRef.current, ctx.sampleRate);
      rafRef.current = requestAnimationFrame(tick);
    };
    rafRef.current = requestAnimationFrame(tick);
  }, [ingest]);

  const startMic = useCallback(async () => {
    stopGraph();
    setError(null);
    setInputMode("microphone");
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: false,
          noiseSuppression: false,
          autoGainControl: false,
          channelCount: 1,
        },
      });
      const ctx = new AudioContext();
      if (ctx.state === "suspended") await ctx.resume();
      const source = ctx.createMediaStreamSource(stream);
      const analyser = ctx.createAnalyser();
      source.connect(analyser);
      streamRef.current = stream;
      audioRef.current = ctx;
      sourceRef.current = source;
      processorRef.current = analyser;
      setSampleRate(ctx.sampleRate);
      detectorRef.current.reset();
      startAnalyserLoop(ctx, analyser);
      setRunning(true);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Microphone permission failed";
      setError(message);
      setRunning(false);
    }
  }, [startAnalyserLoop, stopGraph]);

  const useSimulator = useCallback(() => {
    stopGraph();
    setError(null);
    setInputMode("simulator");
    const ctx = new AudioContext();
    audioRef.current = ctx;
    setSampleRate(ctx.sampleRate);
    detectorRef.current.reset();
    const silence = new Float32Array(FRAME_SIZE);
    const hop = Math.floor(FRAME_SIZE / 2);
    const hopMs = (hop / ctx.sampleRate) * 1000;
    let lastTs = 0;
    const tick = (ts: number) => {
      if (lastTs !== 0 && ts - lastTs < hopMs * 0.9) {
        rafRef.current = requestAnimationFrame(tick);
        return;
      }
      lastTs = ts;
      const sim = simBufferRef.current;
      if (sim && simOffsetRef.current + FRAME_SIZE < sim.length) {
        const frame = sim.slice(simOffsetRef.current, simOffsetRef.current + FRAME_SIZE);
        simOffsetRef.current += hop;
        ingest(frame, ctx.sampleRate);
      } else {
        if (sim) {
          simBufferRef.current = null;
          simOffsetRef.current = 0;
        }
        ingest(silence, ctx.sampleRate);
      }
      rafRef.current = requestAnimationFrame(tick);
    };
    rafRef.current = requestAnimationFrame(tick);
    setRunning(true);
  }, [ingest, stopGraph]);

  const playSimulatedNote = useCallback((midi: number) => {
    const rate = audioRef.current?.sampleRate ?? 48000;
    simBufferRef.current = pianoTone(midiToFreq(midi), rate, 1.8, { amplitude: 0.4, harmonics: 6 });
    simOffsetRef.current = 0;
    detectorRef.current.reset();
  }, []);

  const captureFrames = useCallback(async (ms: number) => {
    pendingFramesRef.current = [];
    await new Promise((resolve) => setTimeout(resolve, ms));
    const frames = pendingFramesRef.current ?? [];
    pendingFramesRef.current = null;
    return frames;
  }, []);

  const applyCalibration = useCallback((next: Calibration) => {
    detectorRef.current.setCalibration(next);
    setCalibrationState(next);
  }, []);

  const setCalibration = useCallback((next: Calibration | null) => {
    detectorRef.current.setCalibration(next);
    setCalibrationState(next);
  }, []);

  useEffect(() => () => stopGraph(), [stopGraph]);

  return {
    running,
    error,
    inputMode,
    calibration,
    state,
    liveRms,
    sampleRate,
    startMic,
    stop: stopGraph,
    useSimulator,
    playSimulatedNote,
    captureFrames,
    applyCalibration,
    setCalibration,
  };
}

export type { FrameDebug };
