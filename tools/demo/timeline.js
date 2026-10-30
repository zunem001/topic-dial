// The story of the Topic Dial demo, in seconds. Actions drive the phone, the camera moves in on
// what matters, the backdrop follows each chapter, captions say what is happening. On the iPhone
// (window.SKIN "ios") the story is the same; the camera finds the same things where iOS puts them,
// and the captions name iOS where they name the platform.
(function () {
  "use strict";
  var ios = window.SKIN === "ios";
  var actions = [];
  function at(t, run, extra) { var a = extra || {}; a.t = t; a.run = run; actions.push(a); return a; }
  // A tap lands at t. On a tab the finger comes down a moment before, and the drop of glass blooms
  // under it; it lifts at t, which picks the topic.
  function tap(t, target, run, where) {
    if (/^v?tab:/.test(target)) {
      at(t - 0.09, function (P) { P.pressTab(target, true, where); });
      var pick = run;
      run = function (P, S) { P.pressTab(target, false); if (pick) pick(P, S); };
    }
    return at(t, run, { tap: target, at: where });
  }
  // A tap on the wheel: it dips with its glass, then the bar rolls out.
  function tapWheel(t) {
    at(t - 0.09, function (P) { P.pressWheel(true); });
    tap(t, "wheel", function (P) { P.pressWheel(false); P.tapWheel(); });
  }
  // A swipe on the wheel: px to the left (negative) turns it to the next topics. Standing up on the
  // side, the wheel turns under a swipe up or down.
  function swipe(t, px, dur, velocity, vertical) {
    at(t, null, { drag: { target: "wheel", px: px, dur: dur, hold: 0.04, velocity: velocity, vertical: !!vertical } });
  }

  // 0 · The bar, at its classic size: every topic with its icon on the wheel's scale, a puck of
  //     glass under the one you are in
  at(0, function (P) { P.reset({ mode: "top", rolled: false, current: 2 }); });
  at(1.7, function (P) { P.scrollTabs(150, 800); });
  at(2.6, function (P) { P.scrollTabs(0, 560); });
  // 1 · Roll it up into the wheel, which shares one island with the pinned message
  tap(3.2, "roll", function (P) { P.rollUp(); });
  // 2 · Swipe to switch: Routes, Gear swap, then back to Trips past two notches
  swipe(5.8, -30, 0.42, -200);
  swipe(7.0, -32, 0.42, -200);
  swipe(8.3, 68, 0.75, 60);
  // 3 · News elsewhere nudges the wheel; Ines starts typing in Routes and its light on the dial
  //     turns into moving dots
  at(9.6, function (P) { P.newMessage(8, false); });
  at(10.3, function (P) { P.typing(4, "Ines"); });
  // 4 · Turn it slowly: the display reads Routes, Gear swap and General, then General opens
  at(12.0, null, { drag: { target: "wheel", px: -34 * 3, dur: 2.6, hold: 0.35, velocity: 0, steps: 3 } });
  // 5 · Tap to roll every topic out: the wheel's bead of glass opens into the bar's puck on your
  //     topic. Pick Photos: a drop of glass blooms under the finger, the puck glides over and takes
  //     its place, and it all winds back up.
  tapWheel(15.4);
  at(16.0, function (P) { P.typing(4, null, ["Ines", "Update: the water is down to ankle height, all clear!", "9:44"]); });
  tap(16.95, "tab:5", function (P) { P.pickTopic(5); });
  // 6 · Keep it open, then send the tabs to the side
  tapWheel(19.6);
  tap(20.6, "keep", function (P) { P.keepOpen(); });
  tap(22.0, "toggle", function (P) { P.toggleMode(); });
  // 7 · On the rail the glass glides up from Photos to Trips, then down to General, stretching on
  //     its way. Meanwhile Jonas types in Gear swap: a small badge with moving dots on its icon,
  //     beside its counter, until his message lands and the counter goes up.
  at(22.45, function (P) { P.typing(3, "Jonas"); });
  tap(23.2, "vtab:2", function (P) { P.pickTopic(2); });
  tap(24.4, "vtab:1", function (P) { P.pickTopic(1); });
  at(25.0, function (P) { P.typing(3, null, ["Jonas", "Found microspikes in size L. Anyone need them?", "9:47"]); });
  // 8 · The rail rolls up into the wheel standing up, beside the pinned message. Turn it down to
  //     Photos. Tap it and the rail drops back down out of it, the chat stepping aside and the
  //     avatars tucking in; keep it open, then send the tabs to the bottom.
  tap(25.6, "vroll", function (P) { P.rollUp(); });
  swipe(26.9, 30, 0.45, 200, true);
  tapWheel(28.2);
  tap(29.7, "vkeep", function (P) { P.keepOpen(); });
  tap(30.7, "vtoggle", function (P) { P.toggleMode(); });
  // 9 · At the bottom the bar rolls up into the wheel right by your thumb. Tap it and the bar rolls
  //     out along the bottom; back along it to General, and pick it.
  tap(31.8, "roll", function (P) { P.rollUp(); });
  tapWheel(33.0);
  at(33.75, function (P) { P.scrollTabsTo(1, 560); });
  tap(34.6, "tab:1", function (P) { P.pickTopic(1); });
  // 10 · Dark: a mention in Gear swap; roll out, and the bar's glow leads back to it
  at(36.2, function (P) { P.setDark(true); P.setMode("top"); }, { sound: "theme" });
  at(37.0, function (P) { P.newMessage(3, true); });
  tapWheel(37.9);
  // Gear swap is out of sight to the left, where the bar glows: bring it in, then pick it.
  at(38.5, function (P) { P.scrollTabsTo(3, 520); });
  tap(39.2, "tab:3", function (P) { P.pickTopic(3); });

  // The same shots on the iPhone's 402 x 874 pt screen: the navigation bar at 62-106 pt, the glass
  // card from 114 pt (its tabs row, then the pinned message), the bottom row over the field at 800 pt.
  var easeIn = function (p) { return 1 - Math.pow(1 - p, 4); };
  var IOS_CAMERA = [
    { t: 0, cx: 201, cy: 437, z: 0.9, fy: 820 },
    { t: 0.1, d: 1.6, cx: 201, cy: 437, z: 1, fy: 712, ease: easeIn },
    { t: 1.5, d: 1.3, cx: 201, cy: 236, z: 1.42, fy: 560 },
    { t: 3.4, d: 1.2, cx: 191, cy: 180, z: 2.1, fy: 540 },
    { t: 9.4, d: 0.9, cx: 201, cy: 142, z: 2.55, fy: 540 },
    { t: 11.8, d: 0.9, cx: 201, cy: 154, z: 2.55, fy: 520 },
    { t: 15.1, d: 0.9, cx: 201, cy: 196, z: 1.75, fy: 540 },
    { t: 21.8, d: 1.0, cx: 170, cy: 330, z: 1.4 },
    { t: 25.4, d: 0.9, cx: 185, cy: 200, z: 2.2, fy: 520 },
    { t: 28.0, d: 0.9, cx: 201, cy: 437, z: 0.98 },
    { t: 31.0, d: 0.9, cx: 201, cy: 725, z: 2.1, fy: 560 },
    { t: 35.95, d: 0.5, cx: 191, cy: 190, z: 1.9, fy: 540 },
    { t: 39.9, d: 2.0, cx: 201, cy: 437, z: 0.95, fy: 712 }
  ];

  window.TIMELINE = {
    duration: 44.6,
    actions: actions,
    dips: [{ t0: 35.8, t1: 36.6 }],
    // Screen coordinates (dp) of the point the camera looks at, its zoom, and where on the stage that point sits.
    camera: ios ? IOS_CAMERA : [
      { t: 0, cx: 180, cy: 380, z: 0.9, fy: 820 },
      { t: 0.1, d: 1.6, cx: 180, cy: 380, z: 1, fy: 712, ease: easeIn },
      { t: 1.5, d: 1.3, cx: 180, cy: 200, z: 1.42, fy: 560 },
      { t: 3.4, d: 1.2, cx: 170, cy: 150, z: 2.1, fy: 540 },
      // Closer, so the dots on the dial and the display show.
      { t: 9.4, d: 0.9, cx: 180, cy: 116, z: 2.55, fy: 540 },
      { t: 11.8, d: 0.9, cx: 180, cy: 128, z: 2.55, fy: 520 },
      { t: 15.1, d: 0.9, cx: 180, cy: 165, z: 1.75, fy: 540 },
      // The side: the rail and the glass gliding along it, then up close on the wheel standing
      // beside the pinned message, and the whole phone as the rail drops down.
      { t: 21.8, d: 1.0, cx: 150, cy: 290, z: 1.45 },
      { t: 25.4, d: 0.9, cx: 165, cy: 170, z: 2.2, fy: 520 },
      { t: 28.0, d: 0.9, cx: 180, cy: 380, z: 1 },
      // Down to the bottom of the chat, where the wheel sits over the message field.
      { t: 31.0, d: 0.9, cx: 180, cy: 610, z: 2.1, fy: 560 },
      { t: 35.95, d: 0.5, cx: 170, cy: 160, z: 1.9, fy: 540 },
      { t: 39.9, d: 2.0, cx: 180, cy: 380, z: 0.95, fy: 712 }
    ],
    // Two-stop gradient plus the soft light behind the phone.
    backdrop: [
      { t: 0, a: "#9ccf9f", b: "#2f6e5f", glow: "#f3ffe0" },
      { t: 3.2, d: 1.4, a: "#62c0b6", b: "#1b5468", glow: "#d9fff6" },
      { t: 5.6, d: 1.4, a: "#5a9de6", b: "#1d3a87", glow: "#dcecff" },
      { t: 9.4, d: 1.4, a: "#74b4e0", b: "#1e4d80", glow: "#e6f4ff" },
      { t: 11.8, d: 1.4, a: "#4fb3c9", b: "#16466e", glow: "#dff7ff" },
      { t: 15.2, d: 1.4, a: "#7c7be8", b: "#2e2f8e", glow: "#e6e3ff" },
      { t: 19.4, d: 1.4, a: "#a26ee0", b: "#40238a", glow: "#f2e1ff" },
      { t: 21.9, d: 1.4, a: "#c46cb4", b: "#4f1f6e", glow: "#ffe3f6" },
      { t: 31.0, d: 1.4, a: "#e48a78", b: "#6c2a3c", glow: "#ffe9e1" },
      { t: 35.85, d: 0.8, a: "#2c3a44", b: "#080c10", glow: "#3a6d9a" },
      { t: 39.6, d: 2.0, a: "#7fc4a2", b: "#1f5560", glow: "#e8fff2" }
    ],
    captions: [
      { t0: 0.4, t1: 3.0, big: true, title: "Topic Dial", text: "Your topics, one swipe away" },
      { t0: 3.4, t1: 5.6, text: "Roll the tabs up into a little wheel." },
      { t0: 5.8, t1: 9.2, text: "Swipe it to switch topics, one notch each." },
      { t0: 9.4, t1: 11.8, text: "News nudges it; typing lights up its scale." },
      { t0: 12.0, t1: 15.0, text: "Turn it slowly: the pinned message reads each topic." },
      { t0: 15.3, t1: 16.8, text: "Tap it to roll every topic out." },
      { t0: 17.0, t1: 19.4, text: "Pick one: the glass glides over, and it winds up." },
      { t0: 19.6, t1: 21.8, text: "Or keep it open, the classic way." },
      { t0: 22.1, t1: 25.3, text: "On the side, the glass glides along the rail." },
      { t0: 25.6, t1: 28.0, text: "Roll it up into a wheel that stands up." },
      { t0: 28.2, t1: 31.0, text: "Tap it: the rail drops, and the chat steps aside." },
      { t0: 31.2, t1: 33.4, text: "At the bottom, the wheel sits right by your thumb." },
      { t0: 33.6, t1: 35.6, text: "And it rolls out along the bottom." },
      { t0: 36.6, t1: 39.6, text: "Light or dark, it fits right in." },
      { t0: 40.2, t1: 44.4, big: true, title: "Topic Dial", text: ios ? "A patch for Telegram iOS" : "Ready-to-use code for Telegram Android" }
    ]
  };
})();
