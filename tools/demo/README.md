# Topic Dial demo video

Renders the 1080 × 1080, 60 fps demo of Topic Dial, 45 seconds long: one phone on a backdrop
that follows the story, a camera that moves in on the detail that matters, short captions, and a
soft music bed. Every touch is a fingertip of frosted glass: it comes down onto the screen and
presses in, drags a tapering smear of the same glass behind it while it moves, and lifts off in a
ring of light. It is white with a soft shadow and a fine dark edge, so it reads on light and dark
screens alike.

The music is a warm loop in G, its opening and closing run included, and every note sits on the
chord of the moment. The video uses it alone (`--music`). Without that flag, `music.py` adds UI
sounds placed on what happens on screen: the bar rolling out plays a short music-box run that rises
and moves left to right with the wheel, winding up plays it back down and ends in a quiet latch,
and each detent of the wheel is a small wooden note pitched by the topic's place on the wheel.
Nothing hisses: the sounds have soft attacks and stay below the harsh 2–5 kHz band, in one small
room.

The phone in this video is a web rebuild of the feature (`phone.js`) that follows the same rules
and numbers as `TopicsTabsView` and `TopicsRollWheel`; it is not a recording of the app. `Phone.interactive()` makes the same phone touchable in a browser.

The glass here is a little more opaque than the app's (97 % instead of 94 %). Headless Chromium drops
wide backdrop blurs, and the extra opacity hides the messages scrolling behind the glass the way the
app's blur does.

| File | What it is |
| --- | --- |
| `stage.html` | The stage: backdrop, device, camera, captions, fingertips |
| `phone.css`, `phone.js` | The phone screen: a forum group with the topic bar, driven on the page clock |
| `phone-ios.css` | The same screen as Telegram iOS, over `phone.css` (`stage.html?skin=ios`, see below) |
| `timeline.js` | The story: actions, camera moves, backdrop colors, captions, in seconds |
| `render.js` | Steps the stage frame by frame on Playwright's fake clock and saves JPEG frames |
| `music.py` | Synthesizes the music, and the UI sounds from the events the render recorded (`--music` leaves them out, `--stems` writes them apart) |

## Render

Needs Node with Playwright and Chromium, Python 3 with numpy, and ffmpeg.

```bash
export NODE_PATH=$(npm root -g)
node render.js --stills 6.2,16.9,23.3 --out stills         # a few PNG stills to check the look
node render.js --out frames --workers 3 --dsf 1.5           # every frame, plus frames/events.json
python3 music.py frames/events.json soundtrack.wav --music
ffmpeg -framerate 60 -i frames/f_%05d.jpg -i soundtrack.wav \
  -vf "scale=1080:1080:flags=lanczos,format=yuv420p" -c:v libx264 -preset slow -crf 16 \
  -c:a aac -b:a 192k -af volume=-2.6dB -movflags +faststart -shortest topic-dial-demo.mp4
```

After a change late in the story, `--from 33.6` renders only the frames from 33.6 s on into the same
folder; the story runs the same every time, so the earlier frames still match. Record the events
for the soundtrack with `--events` (below) rather than taking them from a partial render.

To change only the sound, record the events without rendering frames and swap the audio (here
with the UI sounds):

```bash
node render.js --events --out sound                         # sound/events.json, in a minute or two
python3 music.py sound/events.json soundtrack.wav
ffmpeg -i topic-dial-demo.mp4 -i soundtrack.wav -map 0:v:0 -map 1:a:0 -c:v copy \
  -af volume=-2.6dB -c:a aac -b:a 192k -shortest -movflags +faststart topic-dial-demo-new.mp4
```

`LEVEL` at the top of `music.py` sets how far each UI sound rises over the music. Check it with
`python3 music.py sound/events.json stems.npy --stems`, which saves the music and the UI apart.

Every animation runs on the page clock, so the result is the same on any machine; a slow machine
only takes longer.

## The iPhone version

`stage.html?skin=ios` tells the same story, with the same timings and music, on an iPhone 16 Pro
running Telegram iOS 13: iOS 26's Liquid Glass, the topic tabs and the pinned message in one
floating glass card under the navigation bar, and Telegram iOS's bubbles. `phone.js` takes its
sizes from its `ios` skin, in points, after `topic-dial-ios.patch` (see `IMPLEMENTATION.md` at the top of this repository), and
`phone-ios.css` styles it. Where the iOS patch differs from Android, so does this skin: the tabs keep
their size along the bar and get no badge where someone is typing, and the display uses iOS's words.
Inter stands in for San Francisco and must
be installed; this skin loads no web fonts. Add `--skin ios` to any render:

```bash
node render.js --skin ios --stills 6.2,23.3,28.6 --out stills-ios
node render.js --skin ios --out frames-ios --workers 3 --dsf 1.5
python3 music.py frames-ios/events.json soundtrack-ios.wav --music
ffmpeg -framerate 60 -i frames-ios/f_%05d.jpg -i soundtrack-ios.wav \
  -vf "scale=1080:1080:flags=lanczos,format=yuv420p" -c:v libx264 -preset slow -crf 16 \
  -c:a aac -b:a 192k -af volume=-2.6dB -movflags +faststart -shortest topic-dial-ios-demo.mp4
```

Its glass is more opaque than iOS's for the same reason as above (86 % white, 84 % dark), and the
chat fades out under the card's top instead of blurring behind the navigation bar.
