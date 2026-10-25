(() => {
  if (window.__boxierAudioHook) {
    return;
  }
  window.__boxierAudioHook = true;

  const FRAME_SAMPLES = 960;
  const CHANNELS = 2;

  const state = {
    contexts: new WeakSet(),
    captures: new Set(),
    hookedElements: new WeakSet(),
    elements: new WeakSet(),
    mediaCaptures: 0,
    webAudioTaps: 0,
    sentChunks: 0,
    sentSamples: 0,
    readCount: 0,
    dataEvents: 0,
    dataFrames: 0,
    bridgeStates: {},
    errors: [],
    cefQuerySeen: typeof window.cefQuery === 'function'
  };
  window.__boxierAudioState = state;

  const queue = [];
  const maxQueue = 100;
  const bridgeContexts = new Set();
  let mediaContext = null;

  const resumeBridges = () => {
    bridgeContexts.forEach((ctx) => {
      if (ctx.state === 'suspended') {
        ctx.resume().catch(() => {});
      }
    });
  };

  window.addEventListener('pointerdown', () => {
    resumeBridges();
    document.querySelectorAll('audio,video').forEach((element) => {
      if (element.currentSrc && isVisible(element)) {
        ensurePlayback(element);
      }
    });
  }, true);
  window.addEventListener('keydown', resumeBridges, true);
  setInterval(resumeBridges, 1000);

  const send = (samples, sampleRate) => {
    if (!window.cefQuery) {
      return false;
    }
    let binary = '';
    const bytes = new Uint8Array(samples.buffer, samples.byteOffset, samples.byteLength);
    for (let i = 0; i < bytes.length; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    try {
      window.cefQuery({
        request: 'audio:' + Math.round(sampleRate) + ':' + CHANNELS + ':' + btoa(binary),
        persistent: false,
        onSuccess: () => {},
        onFailure: () => {}
      });
      state.sentChunks += 1;
      state.sentSamples += samples.length;
      return true;
    } catch (error) {
      state.errors.push(String(error));
      return false;
    }
  };

  const flush = () => {
    if (!window.cefQuery) {
      return;
    }
    state.cefQuerySeen = true;
    while (queue.length > 0) {
      const item = queue[0];
      if (!send(item.samples, item.sampleRate)) {
        return;
      }
      queue.shift();
    }
  };

  const enqueue = (samples, sampleRate) => {
    if (queue.length >= maxQueue) {
      queue.shift();
    }
    queue.push({ samples, sampleRate });
    flush();
  };

  setInterval(flush, 250);

  class PcmAssembler {
    constructor(sampleRate) {
      this.sampleRate = sampleRate;
      this.buffer = new Int16Array(FRAME_SAMPLES * CHANNELS * 8);
      this.count = 0;
    }

    push(samples, sampleRate) {
      if (sampleRate !== this.sampleRate) {
        this.sampleRate = sampleRate;
      }
      const needed = this.count + samples.length;
      if (needed > this.buffer.length) {
        let size = this.buffer.length;
        while (size < needed) {
          size *= 2;
        }
        const grown = new Int16Array(size);
        grown.set(this.buffer.subarray(0, this.count));
        this.buffer = grown;
      }
      this.buffer.set(samples, this.count);
      this.count = needed;

      const frameLength = FRAME_SAMPLES * CHANNELS;
      while (this.count >= frameLength) {
        const frame = this.buffer.slice(0, frameLength);
        enqueue(frame, this.sampleRate);
        this.buffer.copyWithin(0, frameLength, this.count);
        this.count -= frameLength;
      }
    }
  }

  const getMediaContext = () => {
    if (!mediaContext || mediaContext.state === 'closed') {
      mediaContext = new AudioContext({ sampleRate: 48000 });
      bridgeContexts.add(mediaContext);
      state.bridgeStates.media = mediaContext.state;
      mediaContext.addEventListener('statechange', () => {
        state.bridgeStates.media = mediaContext.state;
        });
    }
    return mediaContext;
  };

  const startCapture = (stream, media, element, bridge) => {
    let input = stream;
    let ctx = null;
    let source = null;
    let destination = null;
    if (bridge && stream) {
      try {
        ctx = getMediaContext();
        source = ctx.createMediaStreamSource(stream);
        destination = ctx.createMediaStreamDestination();
        source.connect(destination);
        if (ctx.state === 'suspended') {
          ctx.resume().catch(() => {});
        }
        input = destination.stream;
      } catch (error) {
        state.errors.push(String(error));
        return null;
      }
    }
    const tracks = input ? input.getAudioTracks() : [];
    if (tracks.length === 0 || typeof window.MediaStreamTrackProcessor === 'undefined') {
      return null;
    }
    const track = tracks[0];
    const processor = new MediaStreamTrackProcessor({ track });
    const capture = {
      track,
      stream: input,
      inputStream: stream,
      ctx,
      source,
      destination,
      processor,
      reader: processor.readable.getReader(),
      assembler: new PcmAssembler(track.getSettings().sampleRate || 48000),
      media,
      element,
      cancelled: false
    };
    state.captures.add(capture);
    if (media) {
      state.mediaCaptures += 1;
    }
    track.addEventListener('ended', () => cancelCapture(capture));
    readLoop(capture);
    return capture;
  };

  const readLoop = async (capture) => {
    try {
      while (!capture.cancelled) {
        const { value, done } = await capture.reader.read();
        state.readCount += 1;
        if (value) {
          state.dataFrames += value.numberOfFrames;
        }
        if (done || capture.cancelled) {
          if (value) {
            value.close();
          }
          break;
        }
        handleAudioData(capture, value);
        value.close();
      }
    } catch (error) {
      if (!capture.cancelled) {
        state.errors.push(String(error));
      }
    }
  };

  const handleAudioData = (capture, data) => {
    state.dataEvents += 1;
    const channels = data.numberOfChannels;
    const frames = data.numberOfFrames;
    if (frames <= 0 || channels <= 0) {
      return;
    }

    const interleaved = new Int16Array(frames * channels);
    data.copyTo(interleaved.buffer, { planeIndex: 0, format: 's16' });

    const stereo = new Int16Array(frames * CHANNELS);
    for (let i = 0; i < frames; i++) {
      stereo[i * 2] = interleaved[i * channels];
      stereo[i * 2 + 1] = channels > 1 ? interleaved[i * channels + 1] : interleaved[i * channels];
    }
    capture.assembler.push(stereo, data.sampleRate);
  };

  const cancelCapture = (capture) => {
    if (!capture || capture.cancelled) {
      return;
    }
    capture.cancelled = true;
    try {
      capture.reader.cancel();
    } catch (error) {
      state.errors.push(String(error));
    }
    try {
      capture.stream.getTracks().forEach((track) => track.stop());
    } catch (error) {
      state.errors.push(String(error));
    }
    try {
      if (capture.inputStream && capture.inputStream !== capture.stream) {
        capture.inputStream.getTracks().forEach((track) => track.stop());
      }
      if (capture.source) {
        capture.source.disconnect();
      }
      if (capture.ctx) {
        capture.ctx = null;
      }
    } catch (error) {
      state.errors.push(String(error));
    }
    state.captures.delete(capture);
    if (capture.media) {
      state.mediaCaptures = Math.max(0, state.mediaCaptures - 1);
    }
  };

  const setupTap = (ctx) => {
    if (!ctx || state.contexts.has(ctx)) {
      return null;
    }
    state.contexts.add(ctx);
    const dest = ctx.createMediaStreamDestination();
    state.captures.add(dest);
    const capture = startCapture(dest.stream, false, null);
    if (!capture) {
      return null;
    }
    capture.destination = dest;
    state.webAudioTaps += 1;
    return dest;
  };

  const tapDestinationConnections = () => {
    const proto = window.AudioNode && AudioNode.prototype;
    if (!proto || !proto.connect) {
      return;
    }
    const originalConnect = proto.connect;
    proto.connect = function(destination, ...args) {
      const result = originalConnect.call(this, destination, ...args);
      try {
        if (destination instanceof AudioDestinationNode) {
          const tap = setupTap(destination.context);
          if (tap) {
            originalConnect.call(this, tap);
          }
        }
      } catch (error) {
        state.errors.push(String(error));
      }
      return result;
    };
  };

  const isVisible = (element) => {
    try {
      const rect = element.getBoundingClientRect();
      if (rect.width < 2 || rect.height < 2) {
        return false;
      }
      const style = window.getComputedStyle(element);
      return style.display !== 'none' && style.visibility !== 'hidden' && Number(style.opacity) > 0.01;
    } catch (error) {
      return false;
    }
  };

  const ensurePlayback = (element) => {
    element.muted = false;
    if (element.volume < 0.01) {
      element.volume = 1;
    }
    if (element.paused && !element.__boxierForcingPlay) {
      element.__boxierForcingPlay = true;
      try {
        const promise = element.play();
        if (promise && promise.finally) {
          promise.finally(() => {
            element.__boxierForcingPlay = false;
          }).catch(() => {});
        } else {
          element.__boxierForcingPlay = false;
        }
      } catch (error) {
        element.__boxierForcingPlay = false;
        state.errors.push(String(error));
      }
    }
  };

  const captureElement = (element) => {
    if (!element || typeof element.captureStream !== 'function') {
      return;
    }
    if (!element.currentSrc || !isVisible(element)) {
      return;
    }
    hookElement(element);
    ensurePlayback(element);

    const existing = element.__boxierCapture || null;
    if (existing) {
      if (existing.ctx && existing.ctx.state === 'suspended') {
        existing.ctx.resume().catch(() => {});
      }
      return;
    }

    const connect = () => {
      try {
        const stream = element.captureStream();
        const capture = startCapture(stream, true, element, true);
        if (!capture) {
          return false;
        }
        element.__boxierCapture = capture;
        state.elements.add(element);
        return true;
      } catch (error) {
        state.errors.push(String(error));
        return false;
      }
    };

    if (!connect()) {
      element.addEventListener('playing', () => captureElement(element), { once: true });
    }
  };

  const hookElement = (element) => {
    if (state.hookedElements.has(element)) {
      return;
    }
    state.hookedElements.add(element);
    element.addEventListener('playing', () => {
      ensurePlayback(element);
      captureElement(element);
    });
    element.addEventListener('emptied', () => {
      const capture = element.__boxierCapture;
      if (capture) {
        element.__boxierCapture = null;
        cancelCapture(capture);
      }
    });
    element.addEventListener('ended', () => {
      const capture = element.__boxierCapture;
      if (capture) {
        element.__boxierCapture = null;
        cancelCapture(capture);
      }
    });
  };

  const scan = () => {
    document.querySelectorAll('audio,video').forEach(captureElement);
  };

  const installMediaHook = () => {
    const proto = window.HTMLMediaElement && HTMLMediaElement.prototype;
    if (!proto || !proto.play) {
      return;
    }
    const originalPlay = proto.play;
    proto.play = function(...args) {
      captureElement(this);
      return originalPlay.apply(this, args);
    };
  };

  tapDestinationConnections();
  installMediaHook();

  const startObserver = () => {
    try {
      if (document.documentElement) {
        new MutationObserver(scan).observe(document.documentElement, {
          childList: true,
          subtree: true
        });
      }
    } catch (error) {
      state.errors.push(String(error));
    }
    scan();
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
      installMediaHook();
      startObserver();
    }, { once: true });
  } else {
    installMediaHook();
    startObserver();
  }
})();
