(() => {
  if (window.__boxierAudioHook) {
    return;
  }
  window.__boxierAudioHook = true;

  const state = {
    contexts: new WeakSet(),
    captures: new Set(),
    hookedElements: new WeakSet(),
    elements: new WeakSet(),
    sentChunks: 0,
    sentSamples: 0,
    errors: [],
    cefQuerySeen: typeof window.cefQuery === 'function'
  };
  window.__boxierAudioState = state;

  const queue = [];
  const maxQueue = 100;

  const send = (pcm, sampleRate) => {
    if (!window.cefQuery) {
      return false;
    }
    let binary = '';
    const bytes = new Uint8Array(pcm.buffer, pcm.byteOffset, pcm.byteLength);
    for (let i = 0; i < bytes.length; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    try {
      window.cefQuery({
        request: 'audio:' + Math.round(sampleRate) + ':' + btoa(binary),
        persistent: false,
        onSuccess: () => {},
        onFailure: () => {}
      });
      state.sentChunks += 1;
      state.sentSamples += pcm.length;
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

  const setupTap = (ctx) => {
    if (!ctx || state.contexts.has(ctx)) {
      return null;
    }
    state.contexts.add(ctx);

    const processor = ctx.createScriptProcessor(1024, 1, 1);
    const silent = ctx.createGain();
    silent.gain.value = 0;
    processor.connect(silent);
    silent.connect(ctx.destination);
    state.captures.add(processor);
    state.captures.add(silent);

    processor.onaudioprocess = (event) => {
      const input = event.inputBuffer;
      const length = input.length;
      const channels = input.numberOfChannels;
      const pcm = new Int16Array(length);
      if (channels === 1) {
        const data = input.getChannelData(0);
        for (let i = 0; i < length; i++) {
          pcm[i] = Math.max(-1, Math.min(1, data[i])) * 32767;
        }
      } else {
        const left = input.getChannelData(0);
        const right = input.getChannelData(1);
        for (let i = 0; i < length; i++) {
          const value = (left[i] + right[i]) * 0.5;
          pcm[i] = Math.max(-1, Math.min(1, value)) * 32767;
        }
      }
      enqueue(pcm, input.sampleRate);
    };

    if (ctx.state === 'suspended') {
      ctx.resume().catch(() => {});
    }
    return processor;
  };

  const disconnectCapture = (capture) => {
    try {
      capture.source.disconnect();
    } catch (error) {
      state.errors.push(String(error));
    }
    try {
      capture.processor.disconnect();
    } catch (error) {
      state.errors.push(String(error));
    }
    try {
      capture.ctx.close();
    } catch (error) {
      state.errors.push(String(error));
    }
    state.captures.delete(capture);
  };

  const captureElement = (element, force) => {
    if (!element || typeof element.captureStream !== 'function') {
      return;
    }
    hookElement(element);
    const existing = state.elements.has(element) ? element.__boxierCapture : null;
    if (existing && !force) {
      return;
    }
    if (existing) {
      disconnectCapture(existing);
    }

    const connect = () => {
      try {
        const stream = element.captureStream();
        const tracks = stream ? stream.getAudioTracks() : [];
        if (tracks.length === 0) {
          return false;
        }
        let ctx;
        try {
          ctx = new AudioContext({ sampleRate: 48000 });
        } catch (error) {
          ctx = new AudioContext();
        }
        const source = ctx.createMediaStreamSource(stream);
        const processor = setupTap(ctx);
        if (!processor) {
          return false;
        }
        source.connect(processor);
        const capture = { ctx, source, processor, stream };
        element.__boxierCapture = capture;
        state.elements.add(element);
        state.captures.add(capture);
        for (const track of tracks) {
          track.addEventListener('ended', () => {
            if (element.__boxierCapture === capture) {
              element.__boxierCapture = null;
              disconnectCapture(capture);
            }
          });
        }
        return true;
      } catch (error) {
        state.errors.push(String(error));
        return false;
      }
    };

    if (!connect()) {
      element.addEventListener('playing', () => captureElement(element, true), { once: true });
    }
  };

  const hookElement = (element) => {
    if (state.hookedElements.has(element)) {
      return;
    }
    state.hookedElements.add(element);
    element.addEventListener('playing', () => captureElement(element, true));
    element.addEventListener('emptied', () => {
      const capture = element.__boxierCapture;
      if (capture) {
        element.__boxierCapture = null;
        disconnectCapture(capture);
      }
    });
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
