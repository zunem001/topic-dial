"""Soundtrack for the Topic Dial demo, made from scratch with numpy.

A warm 96 bpm loop in G (felt keys, pad, round bass, brushed drums) and UI sounds tuned to it,
placed on the events render.js recorded. Nothing hisses or clicks hard: every sound has a soft
attack, keeps its energy below the harsh 2-5 kHz band, and shares one small, dark room.

- The topic bar rolling out plays a short music-box run on the chord of the moment, rising and
  moving left to right with the wheel, over a breath of air; it lands with a soft felt thump.
- Winding up plays the run back down, right to left, and the wheel seats in its well with a
  quiet latch.
- Each detent of the wheel is a soft wooden note on the chord of the moment, pitched by the
  topic's place on the wheel, so turning it slowly plays a small melody.
- The same run opens and closes the video, and the drums step back while the wheel is turned.

The video itself uses the music alone, its opening and closing run included, without the UI sounds
(--music); the UI sounds are there for anyone who wants them back.

    python3 music.py frames/events.json soundtrack.wav --music
    python3 music.py frames/events.json soundtrack-ui.wav
"""
import json
import sys
import wave

import numpy as np

SR = 48000
BPM = 96
BEAT = 60 / BPM
BAR = 4 * BEAT
# Where the closing title starts: the music settles on the tonic and plays its signature.
OUTRO = 40.0
rng = np.random.default_rng(7)

# Two bars each: bass, voicing, the chord's pitch classes, and an open root-fifth-ninth voicing.
# The last pair resolves to the tonic under the end card.
CHORDS = [
    ("Gmaj9", 43, [59, 62, 66, 69], {7, 11, 2, 6, 9}, [55, 62, 69]),
    ("D/F#", 42, [57, 62, 64, 66], {2, 6, 9, 4}, [50, 57, 64]),
    ("Em9", 40, [55, 59, 62, 66], {4, 7, 11, 2, 6}, [52, 59, 66]),
    ("Asus4", 45, [57, 62, 64, 69], {9, 2, 4}, [57, 64, 69]),
]
# How loud each UI sound sits over the music in the same moment, as the ear hears it (A-weighted,
# measured on the stems with --stems): the rolls, the news and Keep open about 9 to 10 dB over, the
# detents about 6, a tap about 5. The music also leans back while they play.
LEVEL = {"tap": 1.95, "tick": 1.45, "roll_out": 1.0, "roll_up": 1.45, "pop": 1.4, "keep": 1.15, "mode": 1.8, "theme": 2.5}


def midi(n):
    return 440.0 * 2 ** ((n - 69) / 12)


def times(length):
    n = int(length * SR)
    return n, np.arange(n) / SR


def attack(t, seconds):
    """A raised-cosine fade-in: no clicks, however short."""
    return 0.5 - 0.5 * np.cos(np.pi * np.clip(t / max(seconds, 1e-5), 0, 1))


def place(buf, start, sig, pan=0.0, gain=1.0):
    i = int(round(start * SR))
    if i >= buf.shape[1] or i + len(sig) <= 0:
        return
    if i < 0:
        sig, i = sig[-i:], 0
    j = min(buf.shape[1], i + len(sig))
    s = sig[: j - i] * gain
    left = np.cos((pan + 1) * np.pi / 4)
    right = np.sin((pan + 1) * np.pi / 4)
    buf[0, i:j] += s * left
    buf[1, i:j] += s * right


def place_stereo(buf, start, sig2, gain=1.0):
    i = int(round(start * SR))
    if i >= buf.shape[1]:
        return
    j = min(buf.shape[1], i + sig2.shape[1])
    buf[:, i:j] += sig2[:, : j - i] * gain


def one_pole(x, cutoff_hz):
    """One-pole low-pass for short sounds; the cutoff may change over time."""
    cutoff = np.broadcast_to(np.asarray(cutoff_hz, dtype=float), x.shape)
    a = 1 - np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc += a[i] * (x[i] - acc)
        y[i] = acc
    return y


def band_noise(n, center, octaves):
    """Noise with a soft, bell-shaped band around `center` Hz (shaped in the frequency domain)."""
    x = rng.standard_normal(n)
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    spec *= np.exp(-0.5 * (np.log2(np.maximum(f, 1.0) / center) / octaves) ** 2)
    y = np.fft.irfft(spec, n)
    return y / (np.std(y) + 1e-12)


def chord_at(t):
    return CHORDS[(int(t // BAR) // 2) % len(CHORDS)] if t < OUTRO else CHORDS[0]


def run_notes(pcs, low=74, count=6):
    """The chord's own notes, rising from `low`: the music-box run of that chord."""
    notes, n = [], low
    while len(notes) < count:
        if n % 12 in pcs:
            notes.append(n)
        n += 1
    return notes


# Instruments ---------------------------------------------------------------

def pad_note(freq, length):
    n, t = times(length + 1.4)
    out = np.zeros(n)
    for det in (-0.07, 0.0, 0.06):
        f = freq * 2 ** (det / 12)
        for h in range(1, 7):
            out += np.sin(2 * np.pi * f * h * t + rng.uniform(0, 6.28)) / (h ** 1.6)
    sustain = np.where(t < length, 1.0, np.exp(-(t - length) / 0.7))
    out *= attack(t, 0.9) * sustain * (1 + 0.05 * np.sin(2 * np.pi * 0.21 * t))
    return out / 6


def felt_key(freq, length=1.8, vel=0.6):
    """A felt piano note: round, a little stretched, with a soft hammer."""
    n, t = times(length)
    out = np.zeros(n)
    for k, amp, decay in ((1, 1.0, 1.2), (2, 0.4, 0.5), (3, 0.18, 0.26), (4, 0.08, 0.15), (5, 0.04, 0.09), (6, 0.02, 0.05)):
        f = freq * k * np.sqrt(1 + 0.0003 * k * k)
        out += amp * (vel if k > 1 else 1.0) * np.sin(2 * np.pi * f * t) * np.exp(-t / decay)
    return out * attack(t, 0.006)


def celesta(freq, length=1.5, vel=0.6):
    """A soft music-box tine: mostly fundamental, with a faint glassy shimmer that dies fast."""
    n, t = times(length)
    out = np.sin(2 * np.pi * freq * t) * np.exp(-t / 0.65) \
        + 0.16 * vel * np.sin(2 * np.pi * 2 * freq * t) * np.exp(-t / 0.2) \
        + 0.045 * vel * np.sin(2 * np.pi * 3 * freq * t) * np.exp(-t / 0.08) \
        + 0.02 * vel * np.sin(2 * np.pi * 4.2 * freq * t) * np.exp(-t / 0.025)
    return out * attack(t, 0.004) * (0.55 + 0.45 * vel)


def bass_note(freq, length):
    n, t = times(length + 0.35)
    out = np.sin(2 * np.pi * freq * t) + 0.18 * np.sin(2 * np.pi * freq * 2 * t)
    sustain = np.where(t < length, 1 - 0.35 * np.minimum(1, t / 0.3), 0.65 * np.exp(-(t - length) / 0.1))
    return out * attack(t, 0.012) * sustain


def kick():
    n, t = times(0.45)
    f = 48 + 70 * np.exp(-t / 0.04)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.17) * attack(t, 0.004)


def brush():
    """A brushed snare: a short, dark breath of noise over a soft body."""
    n, t = times(0.32)
    noise = one_pole(rng.standard_normal(n), 4300)
    noise -= one_pole(noise, 350)
    body = 0.35 * np.sin(2 * np.pi * 185 * t) * np.exp(-t / 0.045)
    return (noise * np.exp(-t / 0.09) * 0.9 + body) * attack(t, 0.003)


def shaker():
    n, t = times(0.1)
    noise = one_pole(rng.standard_normal(n), 8000)
    noise -= one_pole(noise, 3000)
    return noise * attack(t, 0.007) * np.exp(-t / 0.03) * 1.6


# UI sounds -----------------------------------------------------------------

def sfx_tap():
    """A fingertip on glass: a soft, low tock."""
    n, t = times(0.12)
    out = np.sin(2 * np.pi * 620 * t) * np.exp(-t / 0.018) \
        + 0.45 * np.sin(2 * np.pi * 240 * t) * np.exp(-t / 0.014) \
        + 0.08 * np.sin(2 * np.pi * 1550 * t) * np.exp(-t / 0.005)
    return out * attack(t, 0.0015) * 0.5


def sfx_tock(note, firm=False):
    """One detent of the wheel: a small wooden note, lower and fuller where the pinned topics end."""
    n, t = times(0.5)
    f = midi(note)
    out = np.sin(2 * np.pi * f * t) * np.exp(-t / (0.075 if not firm else 0.12)) \
        + 0.1 * np.sin(2 * np.pi * 3.9 * f * t) * np.exp(-t / 0.006) \
        + 0.28 * np.sin(2 * np.pi * f / 2 * t) * np.exp(-t / 0.028)
    if firm:
        out += 0.5 * np.sin(2 * np.pi * f / 2 * t) * np.exp(-t / 0.1)
    return out * attack(t, 0.0012) * (0.5 if not firm else 0.62)


def air(length, center, octaves, env, pan_from=0.0, pan_to=0.0):
    """A breath of air that follows an envelope and moves across the stereo field."""
    n = len(env)
    x = band_noise(n, center, octaves) * env
    pan = np.linspace(pan_from, pan_to, n)
    return np.vstack([x * np.cos((pan + 1) * np.pi / 4), x * np.sin((pan + 1) * np.pi / 4)])


def thump(freq, length=0.25, drop=0.75):
    """A felt thump: a low note that sinks a little as it dies."""
    n, t = times(length)
    f = freq * (drop + (1 - drop) * np.exp(-t / 0.03))
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.06) * attack(t, 0.003)


def roll_out(buf, t0, wet):
    """The bar rolls out: a rising music-box run, left to right with the wheel, over a breath."""
    _, root, _, pcs, _ = chord_at(t0)
    T = 0.54
    notes = run_notes(pcs)
    # Notes fall where the wheel passes the tabs: quick at first, slowing as it lands (ease-out).
    for i, (p, vel) in enumerate(zip([0.12, 0.3, 0.48, 0.66, 0.82, 0.95], [0.42, 0.46, 0.5, 0.55, 0.6, 0.7])):
        at = t0 + T * (1 - (1 - p) ** 0.25)
        sig = celesta(midi(notes[i]), 1.4, vel)
        pan = -0.55 + 1.1 * i / 5
        place(buf, at, sig, pan, 0.31 * vel)
        place(wet, at, sig, pan, 0.16 * vel)
    n, t = times(T + 0.25)
    env = attack(t, 0.03) * np.clip(1 - t / (T + 0.05), 0, 1) ** 2.2
    place_stereo(buf, t0, air(T + 0.25, 820, 0.75, env, -0.5, 0.5), 0.034)
    place(buf, t0 + T - 0.03, thump(midi(root + 12)), 0.0, 0.24)


def roll_up(buf, t0, wet):
    """The bar winds up: the run falls back, right to left, and the wheel seats in its well."""
    _, root, _, pcs, _ = chord_at(t0)
    T = 0.48
    notes = run_notes(pcs)[::-1]

    def eased_time(p):  # inverse of ease-in-out-cubic: sparse, dense, sparse
        return (p / 4) ** (1 / 3) if p < 0.5 else 1 - ((2 - 2 * p) ** (1 / 3)) / 2

    for i, (p, vel) in enumerate(zip([0.05, 0.2, 0.4, 0.6, 0.8, 0.95], [0.5, 0.47, 0.45, 0.43, 0.42, 0.46])):
        at = t0 + T * eased_time(p)
        sig = celesta(midi(notes[i]), 1.2, vel)
        pan = 0.55 - 1.1 * i / 5
        place(buf, at, sig, pan, 0.28 * vel)
        place(wet, at, sig, pan, 0.15 * vel)
    n, t = times(T + 0.2)
    env = np.sin(np.pi * np.clip(t / T, 0, 1)) ** 1.6
    place_stereo(buf, t0, air(T + 0.2, 700, 0.7, env, 0.5, -0.5), 0.03)
    # The latch: a soft low tock and the faintest tick, as the wheel settles in.
    place(buf, t0 + T, thump(midi(root + 12), 0.22, 0.7), -0.3, 0.32)
    n, t = times(0.05)
    place(buf, t0 + T + 0.004, np.sin(2 * np.pi * 1350 * t) * np.exp(-t / 0.005) * attack(t, 0.001), -0.3, 0.05)


def sfx_pop(buf, t0, wet):
    """News in another topic: a soft bubble, then two drops of the chord."""
    _, _, _, pcs, _ = chord_at(t0)
    n, t = times(0.18)
    f = 430 + 220 * (1 - np.exp(-t / 0.03))
    bubble = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.045) * attack(t, 0.003)
    place(buf, t0, bubble, -0.2, 0.26)
    top = run_notes(pcs)[-3:]
    for k, (dt, note) in enumerate(((0.05, top[0]), (0.14, top[2]))):
        sig = celesta(midi(note), 1.4, 0.55)
        place(buf, t0 + dt, sig, -0.15 + 0.2 * k, 0.2)
        place(wet, t0 + dt, sig, -0.15 + 0.2 * k, 0.12)


def sfx_keep(buf, t0, wet):
    """Keep open: a warm strummed chord, settling."""
    _, root, voicing, _, _ = chord_at(t0)
    for k, note in enumerate([root + 24] + [v + 12 for v in voicing[1:]]):
        sig = felt_key(midi(note), 2.0, 0.55)
        place(buf, t0 + 0.022 * k, sig, -0.3 + 0.2 * k, 0.11)
        place(wet, t0 + 0.022 * k, sig, -0.3 + 0.2 * k, 0.07)


def sfx_mode(buf, t0, wet):
    """Tabs move to the side or back: a short breath and a single note."""
    _, _, _, pcs, _ = chord_at(t0)
    n, t = times(0.32)
    env = np.sin(np.pi * np.clip(t / 0.3, 0, 1)) ** 2
    place_stereo(buf, t0, air(0.32, 650, 0.7, env, -0.4, 0.2), 0.024)
    sig = celesta(midi(run_notes(pcs)[2]), 1.2, 0.45)
    place(buf, t0 + 0.12, sig, 0.0, 0.11)
    place(wet, t0 + 0.12, sig, 0.0, 0.08)


def sfx_theme(buf, t0, wet):
    """Light to dark: the air sinks, a low chord swells under it, and one high note appears."""
    _, _, _, pcs, open_voicing = chord_at(t0 + 0.5)
    length = 1.6
    n, t = times(length)
    hi = band_noise(n, 1300, 0.6)
    lo = band_noise(n, 380, 0.6)
    mix = np.clip(t / 0.8, 0, 1)
    breath = (hi * (1 - mix) + lo * mix) * np.sin(np.pi * np.clip(t / 0.95, 0, 1)) ** 2
    place(buf, t0, breath, 0.0, 0.03)
    for note in open_voicing:
        swell = np.sin(2 * np.pi * midi(note) * t) * attack(t, 0.4) * np.exp(-np.maximum(0, t - 0.5) / 0.45)
        place(buf, t0 + 0.15, swell, 0.0, 0.05)
    sig = celesta(midi(run_notes(pcs)[-1]), 1.8, 0.35)
    place(buf, t0 + 0.85, sig, 0.25, 0.08)
    place(wet, t0 + 0.85, sig, 0.25, 0.09)


def signature(buf, t0, wet, step=0.1, hold=2.2, final=False):
    """The roll-out run, slow: the sound of Topic Dial, at the start and under the end card."""
    _, root, _, pcs, _ = chord_at(t0)
    notes = run_notes(pcs, low=74, count=6)
    for i, note in enumerate(notes):
        last = i == len(notes) - 1
        vel = 0.4 + 0.05 * i
        sig = celesta(midi(note), hold if last else 1.6, vel)
        place(buf, t0 + step * i, sig, -0.5 + 0.2 * i, 0.16 * vel)
        place(wet, t0 + step * i, sig, -0.5 + 0.2 * i, 0.14 * vel)
    if final:
        # And home: the root, low and soft, under the last note.
        for k, note in enumerate((root + 24, root + 36)):
            sig = felt_key(midi(note), 3.0, 0.4)
            place(buf, t0 + step * len(notes) + 0.02 * k, sig, 0.0, 0.08)
            place(wet, t0 + step * len(notes) + 0.02 * k, sig, 0.0, 0.07)


# Room ----------------------------------------------------------------------

def room_impulse(seconds=1.7, predelay=0.018):
    """A small, dark room: decorrelated noise that dies away, its highs dying first."""
    n, t = times(seconds)
    ir = np.zeros((2, n))
    for ch in range(2):
        noise = rng.standard_normal(n)
        bright = noise * np.exp(-t / 0.12)
        dark = one_pole(noise, 2200) * np.exp(-t / 0.32) * 2.4
        ir[ch] = (bright * 0.35 + dark) * attack(t, 0.004)
    pre = np.zeros((2, int(predelay * SR)))
    ir = np.hstack([pre, ir])
    return ir / np.sqrt(np.sum(ir ** 2) / 2)


def convolve(x, ir):
    n = x.shape[1] + ir.shape[1] - 1
    size = 1 << (n - 1).bit_length()
    out = np.empty((2, n))
    for ch in range(2):
        spec = np.fft.rfft(x[ch], size) * np.fft.rfft(ir[ch], size)
        out[ch] = np.fft.irfft(spec, size)[:n]
    return out[:, : x.shape[1]]


def shelve(x, freqs, gains_db):
    """A smooth EQ curve through (frequency, gain) points, applied in the frequency domain."""
    out = np.empty_like(x)
    for ch in range(x.shape[0]):
        spec = np.fft.rfft(x[ch])
        f = np.fft.rfftfreq(x.shape[1], 1 / SR)
        g = np.interp(np.log2(np.maximum(f, 1)), np.log2(freqs), gains_db)
        out[ch] = np.fft.irfft(spec * 10 ** (g / 20), x.shape[1])
    return out


def leveled(sound, buf, t0, wet, level):
    """Plays a UI sound at its level from the LEVEL table, dry and into the room alike."""
    i0, i1 = max(0, int((t0 - 0.5) * SR)), min(buf.shape[1], int((t0 + 3.5) * SR))
    dry, room = np.zeros((2, buf.shape[1])), np.zeros((2, buf.shape[1]))
    sound(dry, t0, room)
    buf[:, i0:i1] += dry[:, i0:i1] * level
    wet[:, i0:i1] += room[:, i0:i1] * level


def envelope(x, seconds):
    """A smoothed loudness envelope of a stereo bus, for ducking."""
    mono = np.abs(x).max(axis=0)
    k = max(1, int(seconds * SR))
    c = np.cumsum(np.concatenate([[0.0], mono]))
    avg = (c[k:] - c[:-k]) / k
    return np.concatenate([avg, np.full(k - 1, avg[-1])])


# Arrangement ---------------------------------------------------------------

def compose(duration, events, stems=False, ui=True):
    n = int((duration + 2.0) * SR)
    music = np.zeros((2, n))
    drums = np.zeros((2, n))
    sfx = np.zeros((2, n))
    wet = np.zeros((2, n))
    ui_wet = np.zeros((2, n))
    sidechain = np.ones(n)

    # The wheel is turned slowly from 12 s to 15 s: the drums step back for that bar.
    quiet_bars = {5}
    bars = int(np.ceil(duration / BAR)) + 1
    arp_shape = [0, 1, 2, 3, 2, 1, 3, 2]
    for bar in range(bars):
        start = bar * BAR
        _, root, voicing, _, _ = chord_at(start)
        if bar % 2 == 0:
            for note in voicing:
                place(music, start, pad_note(midi(note), BAR * 2 - 0.1), pan=rng.uniform(-0.4, 0.4), gain=0.24)
                place(wet, start, pad_note(midi(note), BAR * 2 - 0.1), gain=0.06)
        # Felt keys, an octave up, eighth notes with a little swing and a human touch.
        if bar >= 1:
            for i in range(8):
                note = voicing[arp_shape[(i + bar) % 8]] + 12
                vel = 0.5 + 0.22 * (i % 2 == 0) + rng.uniform(-0.05, 0.05)
                at = start + i * BEAT / 2 + (0.018 if i % 2 else 0.0) + rng.uniform(-0.004, 0.004)
                sig = felt_key(midi(note), 1.4, vel)
                pan = 0.3 if i % 2 else -0.3
                place(music, at, sig, pan=pan, gain=0.1 * vel)
                place(wet, at, sig, pan=pan, gain=0.05 * vel)
        if bar >= 2:
            place(music, start, bass_note(midi(root), BEAT * 1.4), gain=0.3)
            place(music, start + BEAT * 2.5, bass_note(midi(root), BEAT * 1.2), gain=0.22)
        if 2 <= bar < bars - 2 and bar not in quiet_bars:
            for beat in range(4):
                bt = start + beat * BEAT
                if beat in (0, 2):
                    place(drums, bt, kick(), gain=0.36)
                    i0 = int(bt * SR)
                    if i0 < n:
                        seg = 1 - 0.22 * np.exp(-np.arange(min(int(0.3 * SR), n - i0)) / SR / 0.1)
                        sidechain[i0:i0 + len(seg)] = np.minimum(sidechain[i0:i0 + len(seg)], seg)
                if beat in (1, 3):
                    place(drums, bt, brush(), gain=0.12, pan=0.08)
                    place(wet, bt, brush(), gain=0.05)
                for half in range(2):
                    swing = 0.03 * BEAT if half else 0.0
                    place(drums, bt + half * BEAT / 2 + swing, shaker(), gain=0.12 if half else 0.075, pan=0.35)
        elif bar in quiet_bars:
            # Only the shaker, softly, so the wheel's own notes carry the beat.
            for beat in range(4):
                place(drums, start + beat * BEAT + 0.03 * BEAT, shaker(), gain=0.06, pan=0.35)

    music *= sidechain

    # The run that opens and closes the video belongs to the UI sounds when they play, and to the
    # music when it plays alone.
    sig, sig_wet = (sfx, ui_wet) if ui else (music, wet)
    signature(sig, 0.45, sig_wet)
    signature(sig, OUTRO, sig_wet, step=0.12, hold=3.0, final=True)

    for e in events if ui else []:
        t, kind, data = e["t"], e["type"], e.get("data") or ""
        if kind == "tap":
            place(sfx, t, sfx_tap(), pan=0.0, gain=0.58 * LEVEL["tap"])
        elif kind == "tick":
            # The detent's note climbs the chord of the moment with the topic's place on the wheel.
            firm = data.startswith("edge")
            index = int(data.split()[-1]) if data.split() else 3
            ladder = run_notes(chord_at(t)[3], low=67, count=12)
            note = ladder[max(0, min(len(ladder) - 1, index))]
            sig = sfx_tock(note, firm)
            place(sfx, t, sig, pan=-0.35, gain=0.55 * LEVEL["tick"])
            place(ui_wet, t, sig, pan=-0.35, gain=0.26 * LEVEL["tick"])
        elif kind in ("rollDown", "rollOut"):
            leveled(roll_out, sfx, t, ui_wet, LEVEL["roll_out"])
        elif kind == "rollUp":
            leveled(roll_up, sfx, t, ui_wet, LEVEL["roll_up"])
        elif kind == "pop":
            leveled(sfx_pop, sfx, t, ui_wet, LEVEL["pop"])
        elif kind in ("pin", "keep"):
            leveled(sfx_keep, sfx, t, ui_wet, LEVEL["keep"])
        elif kind == "mode":
            leveled(sfx_mode, sfx, t, ui_wet, LEVEL["mode"])
        elif kind == "theme":
            leveled(sfx_theme, sfx, t - 0.45, ui_wet, LEVEL["theme"])

    # The music leans back a little whenever the interface speaks, so the UI can stay quiet.
    duck = 1 - 0.42 * np.clip(envelope(sfx, 0.06) / 0.12, 0, 1)
    duck = np.convolve(duck, np.ones(int(0.05 * SR)) / int(0.05 * SR), mode="same")
    music *= duck

    ir = room_impulse()
    bed = music * 0.95 + drums * 0.85 + convolve(wet, ir) * 0.55
    ui = sfx + convolve(ui_wet, ir) * 0.55
    if stems:
        return bed[:, : int(duration * SR)], ui[:, : int(duration * SR)]
    mix = bed + ui

    # A gentle master: no rumble, softer highs, a calm limiter.
    mix = shelve(mix, [20, 35, 120, 4000, 10000, 16000, 24000], [-24, 0, 0, 0, -1, -3, -6])
    total = mix.shape[1]
    fade_in = np.minimum(1, np.arange(total) / (0.4 * SR))
    end = int(duration * SR)
    fade_out = np.clip((end - np.arange(total)) / (2.6 * SR), 0, 1)
    mix = (mix * fade_in * fade_out)[:, :end]
    mix /= np.max(np.abs(mix)) + 1e-12
    mix = np.tanh(mix * 1.15) / np.tanh(1.15)
    return mix / np.max(np.abs(mix)) * 0.86


def write_wav(path, x):
    pcm = (np.clip(x, -1, 1).T * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def main():
    events_path, out_path = sys.argv[1], sys.argv[2]
    data = json.load(open(events_path))
    if "--stems" in sys.argv:
        # The music and the UI apart, unmastered, to check how the UI sits on the music.
        bed, ui = compose(data["duration"], data["events"], stems=True)
        np.save(out_path, np.stack([bed, ui]))
        print(f"wrote stems to {out_path}")
        return
    ui = "--music" not in sys.argv
    mix = compose(data["duration"], data["events"], ui=ui)
    write_wav(out_path, mix)
    print(f"wrote {out_path}: {mix.shape[1] / SR:.1f} s, " + (f"{len(data['events'])} sound events" if ui else "music only"))


if __name__ == "__main__":
    main()
