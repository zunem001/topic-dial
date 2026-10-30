// The phone screen of the Topic Dial demo: a forum group whose topic bar can roll up into a
// small wheel. Rolled up, the wheel and the pinned message are one glass island, like a radio: the
// wheel is the dial, the pinned message its display. Swipe the wheel and it turns to the next or
// previous topic, one click per topic, while the display tunes to the topic under the lens and its
// latest message; tap it and the bar rolls back out across the row like a carpet; pick a topic and
// it winds back up. "Keep open" keeps the bar out. Rolled out, the bar keeps the size it always had,
// and its surface is the wheel's track, with the wheel's scale and lens. At the bottom it winds up
// into a wheel at the bottom; on the side the rail rolls up into a wheel standing at its top. It
// follows the numbers of TopicsTabsView (36 dp bar, 48 dp island, 7 dp margins, 64 dp rail, 108 dp
// wheel, 34 dp per topic on the wheel) and runs on the page clock (performance.now,
// requestAnimationFrame), so render.js can step it frame by frame. Phone.interactive() makes it
// touchable in a browser.
//
// Phone.init(el, { skin: "ios" }) shows the same feature in Telegram iOS on an iPhone instead (with
// phone-ios.css): the bar becomes the first row of the glass card that holds the pinned message
// under the navigation bar, the rail and the bottom bar take iOS's own sizes, and everything moves
// by the same rules and timings.
(function () {
  "use strict";
  // Both phones share the wheel and its track, 36 high like the bar, and the 48 high pinned panel.
  var BAR = 36, PANEL = 48;
  var WHEEL_W = 108, WHEEL_R = 46, CELL = 34, STEP = CELL / WHEEL_R;
  // Rolled up at the top, the wheel sits in a well 6 dp inside a 48 dp island, so their rounded ends
  // are concentric. The display (the pinned message, or the topic under the lens) starts after a
  // seam. At the bottom the wheel's track is the island, in the bar's own row. On the side the island
  // stands up at the top of the rail, the wheel on its side in it.
  var RIM = (PANEL - BAR) / 2, ISLAND_ALONE = WHEEL_W + RIM * 2, V_ISLAND_W = PANEL, V_ISLAND_H = ISLAND_ALONE;
  var UNROLL_MS = 540, ROLLUP_MS = 480, RESERVE_MS = 420, PICK_DELAY = 340, SNAP_MS = 420;
  var SELECT_MS = 320, LEAVE_MS = 150, LOAD_MS = 90, ENTER_DELAY_MS = 40, ENTER_MS = 380, GHOST_MS = 200, POP_MS = 420, HEADER_MS = 300, NUDGE_MS = 900, PANEL_MS = 320, DISPLAY_MS = 170;
  // A fling of the chat, and how long the floating date stays once it rests.
  var FLING_MS = 720, DATE_HOLD_MS = 650;
  // The bar's surface is the wheel's track: the toggle sits in its left end, « or Keep open in its
  // right end (KEY wide), and the tabs lie between them on the wheel's scale, a tick every TICK. A
  // tab: the topic's icon in a SLOT (28 dp, TAB_PAD in), its name and its counter. The lens is a puck
  // of glass under the topic you are in, 3 dp inside the bar, and glides from tab to tab on two springs.
  var TICK = 17, LENS_H = 30;
  // The lens's glide: each edge on a critically damped spring (radians a second), the leading one
  // first; seconds until it has landed. The drop of glass under a finger: its bloom and the tab's way
  // back up (radians a second), how far the tab sinks, how long the drop takes to melt (seconds).
  var LENS_LEAD = 34, LENS_TRAIL = 27, LENS_END = 0.36;
  var DROP_BLOOM = 30, DROP_SETTLE = 20, DROP_SINK = 0.07, DROP_MELT = 0.2;

  // The rest depends on the phone, in dp on Android and in pt on iOS (one CSS px each).
  var SKINS = {
    android: {
      // A 56 dp action bar under the status bar, a 48 dp field over the navigation bar, panels 7 dp
      // from the edges and from each other.
      W: 360, H: 760, CONTENT_TOP: 80, M: 7, GAP: 7, INPUT_TOP: 760 - 30 - 8 - 48,
      // The bar is the wheel's track from end to end, at the top and at the bottom, and the pinned
      // message a panel of its own under it.
      CARD: false, TAB_ROW: 36, T_RIM_OUT: 0, B_RIM_IN: 0, B_RIM_OUT: 0,
      // A 64 dp rail; kept open, the chat gives it the rail's column. Rolled up, the upright wheel's
      // island floats over the avatars.
      RAIL: 64, RAIL_RIM: 0, RAIL_R: 16, SIDE_INSET: 64 + 7, SIDE_ROLLED: 0, RAIL_ICON: 22, TAB_ICON: 20,
      // The pinned message's line, 14 dp in its panel (12 in the bottom bar), the display's words
      // after it. The bubbles start 54 dp in, after the avatars.
      LINE_X: 14, DISPLAY_PAD: 26, DISPLAY_END: 16, B_LINE_X: 12, B_DISPLAY_PAD: 24, B_DISPLAY_END: 14, BUBBLE_X: 54,
      KEY: 36, TAB_PAD: 4, SLOT: 28, FONT: "Roboto"
    },
    // Telegram iOS 13 on an iPhone 16 Pro, as in ChatFloatingTopicsPanel and
    // HeaderPanelContainerComponent.
    ios: {
      // A 44 pt navigation bar under the 62 pt status bar, the 40 pt field over the home indicator,
      // glass 16 pt from the edges and 8 pt from each other.
      W: 402, H: 874, CONTENT_TOP: 62 + 44, M: 16, GAP: 8, INPUT_TOP: 874 - 34 - 40,
      // The tabs are the 44 pt first row of the glass card that holds the pinned message, the track
      // 4 pt inside it; at the bottom a 40 pt capsule over the field, which winds up into a 48 pt
      // island like the one at the top.
      CARD: true, TAB_ROW: 44, T_RIM_OUT: 4, B_RIM_IN: 6, B_RIM_OUT: 2,
      // An 80 pt rail with its tabs 72 pt wide, 4 pt inside it; kept open, the chat moves 72 + 20 pt
      // to the right. Rolled up, the upright wheel's island is wider than the avatars, so the chat
      // keeps 24 pt of the column: the avatars end under the island and the bubbles clear it. The
      // icons' boxes: 18 pt on the bar, 32 on the rail, where an emoji is about 30 pt.
      RAIL: 80, RAIL_RIM: 4, RAIL_R: 20, SIDE_INSET: 72 + 20, SIDE_ROLLED: 24, RAIL_ICON: 32, TAB_ICON: 18,
      // The pinned message's line, 18 pt in, the display's words 12 pt from the end. The bottom
      // island's display has no line: its two smaller lines start 10 pt after the well. The bubbles
      // start 48 pt in (their tails 6 pt before).
      LINE_X: 18, DISPLAY_PAD: 28, DISPLAY_END: 12, B_LINE_X: 12, B_DISPLAY_PAD: 12, B_DISPLAY_END: 12, BUBBLE_X: 48,
      KEY: 40, TAB_PAD: 12, SLOT: 18, FONT: "Inter"
    }
  };
  var IOS, W, H, CONTENT_TOP, M, GAP, INPUT_TOP, CARD, TAB_ROW, T_RIM_OUT, B_RIM_IN, B_RIM_OUT;
  var RAIL, RAIL_RIM, RAIL_R, SIDE_INSET, SIDE_ROLLED, RAIL_ICON, TAB_ICON, LINE_X, DISPLAY_PAD, DISPLAY_END, B_LINE_X, B_DISPLAY_PAD, B_DISPLAY_END, BUBBLE_X;
  var KEY, TAB_PAD, SLOT, FONT, ASIDE_SCALE, Y0, FULL, YB_EDGE, PANEL_STEP, BOTTOM_ROW, B_ISLAND, SEAM_X, DISPLAY_X, B_DISPLAY_X;
  function useSkin(name) {
    var s = SKINS[name] || SKINS.android;
    IOS = s === SKINS.ios;
    W = s.W; H = s.H; CONTENT_TOP = s.CONTENT_TOP; M = s.M; GAP = s.GAP; INPUT_TOP = s.INPUT_TOP;
    CARD = s.CARD; TAB_ROW = s.TAB_ROW; T_RIM_OUT = s.T_RIM_OUT; B_RIM_IN = s.B_RIM_IN; B_RIM_OUT = s.B_RIM_OUT;
    RAIL = s.RAIL; RAIL_RIM = s.RAIL_RIM; RAIL_R = s.RAIL_R; SIDE_INSET = s.SIDE_INSET; SIDE_ROLLED = s.SIDE_ROLLED;
    RAIL_ICON = s.RAIL_ICON; TAB_ICON = s.TAB_ICON;
    LINE_X = s.LINE_X; DISPLAY_PAD = s.DISPLAY_PAD; DISPLAY_END = s.DISPLAY_END;
    B_LINE_X = s.B_LINE_X; B_DISPLAY_PAD = s.B_DISPLAY_PAD; B_DISPLAY_END = s.B_DISPLAY_END; BUBBLE_X = s.BUBBLE_X;
    KEY = s.KEY; TAB_PAD = s.TAB_PAD; SLOT = s.SLOT; FONT = s.FONT;
    // How far the chat steps back while side tabs are rolled out over it: about its right edge, until
    // its bubbles clear the rail with 6 px to spare.
    ASIDE_SCALE = (W - (M + RAIL + 6)) / (W - (BUBBLE_X + SIDE_ROLLED));
    // The top row and the bottom row's lower edge. The pinned message steps down by the bar's row
    // (and the gap under it, on Android), and the chat keeps the bottom bar's row.
    Y0 = CONTENT_TOP + GAP; FULL = W - M * 2; YB_EDGE = INPUT_TOP - GAP;
    PANEL_STEP = CARD ? TAB_ROW : BAR + GAP;
    BOTTOM_ROW = BAR + B_RIM_IN * 2 + GAP;
    // The island at the bottom: the wheel's track itself on Android, the 48 pt island on iOS.
    B_ISLAND = WHEEL_W + B_RIM_IN * 2;
    SEAM_X = M + RIM + WHEEL_W + 10; DISPLAY_X = SEAM_X - LINE_X;
    B_DISPLAY_X = M + B_RIM_IN + WHEEL_W + 10 - B_LINE_X;
  }

  // Tab order as Telegram shows it: All, the pinned topics, then the rest.
  var TOPICS = [
    { id: 0, icon: "", title: "All topics", short: "All", unread: 0, color: "#8fa0ab", count: 0 },
    { id: 2, icon: "🏔️", title: "Trips", pinned: true, unread: 0, color: "#6fb9f0", count: 214 },
    { id: 4, icon: "🗺️", title: "Routes", pinned: true, unread: 2, mention: true, color: "#8eee98", count: 158 },
    { id: 3, icon: "🎒", title: "Gear swap", pinned: true, unread: 11, color: "#ffd67e", count: 96 },
    { id: 1, icon: "#", title: "General", unread: 0, color: "#8fa0ab", count: 1840 },
    { id: 5, icon: "📸", title: "Photos", unread: 0, color: "#cb86db", count: 402 },
    { id: 6, icon: "🍲", title: "Camp food", unread: 5, muted: true, color: "#fb6f5f", count: 37 },
    { id: 7, icon: null, title: "Carpool", unread: 0, color: "#6fb9f0", count: 63 },
    { id: 8, icon: null, title: "Weather", unread: 1, color: "#ff93b2", count: 29 }
  ];
  var MESSAGES = {
    1: [["Nadia", "Welcome to everyone who joined this week! Say hi and tell us your favourite trail.", "8:02"], ["Tomás", "Hi all, mostly day hikes around the lakes for me.", "8:15"], [null, "Hello! Ridge walks for me.", "8:21"]],
    2: [["Priya", "Saturday is on: Eagle Ridge loop, 14 km, about 900 m up.", "9:04"], ["Ben", "Can we start a bit earlier? Afternoon storms are forecast.", "9:10"], ["Priya", "Moved to 7:30 at the north lot.", "9:12"], [null, "Count me in, bringing two extra headlamps.", "9:20"], ["Lena", "First time on this one. How steep is the last part?", "9:31"]],
    3: [["Ollie", "Selling a 2-person tent, 1.6 kg, used three times.", "7:45"], ["Mara", "Price?", "7:52"], ["Ollie", "60, or swap for a good 40 L pack.", "7:55"], ["Jonas", "Anyone have microspikes in size L to lend?", "8:30"]],
    4: [["Sam", "Is the river crossing on the east trail passable now?", "9:02"], ["Ines", "Water was knee-high on Sunday.", "9:06"], ["Sam", "GPX for the variant that skips it is pinned.", "9:08"], [null, "Perfect, thanks Sam!", "9:15"], ["Ines", "@alex that variant stays dry the whole way.", "9:26"]],
    5: [["Lena", "Sunrise from the saddle last weekend 🌄", "Yesterday"], ["Ben", "That light is unreal.", "Yesterday"], [null, "Saving this as my wallpaper.", "Yesterday"], ["Priya", "Next time we camp up there.", "Yesterday"]],
    6: [["Mara", "Dehydrated chili recipe, feeds four.", "Mon"], ["Tomás", "Tried it, add more cumin.", "Mon"], [null, "Bringing the big pot on Saturday.", "Tue"], ["Lena", "Oat bars from the last trip were a hit, recipe please!", "Tue"]],
    7: [["Jonas", "Two free seats from the station on Saturday, leaving 6:45.", "8:50"], [null, "I'll take one, thanks!", "8:58"], ["Mara", "Room for a bike rack?", "9:01"], ["Jonas", "Yes, two bikes fit.", "9:03"]],
    8: [["Ines", "Morning looks clear, 9 °C at the trailhead.", "9:18"], ["Sam", "Wind picks up on the ridge after noon.", "9:21"], [null, "So we turn around by 13:00 at the latest.", "9:27"], ["Ines", "Rain after 15:00 on Saturday, 60 percent.", "9:31"]]
  };
  // Older messages above the latest ones, so there is something to scroll back through.
  var HISTORY = [
    ["Lena", "Anyone up for a short loop on Thursday evening?", "18:02"],
    ["Ben", "If it stays dry, count me in.", "18:04"],
    ["Mara", "Reminder: the north lot fills up by 8 on weekends.", "18:10"],
    [null, "Good to know, I'll come early.", "18:12"],
    ["Jonas", "Does anyone have a spare water filter?", "18:18"],
    ["Ollie", "Mine is in the gear box, grab it on Friday.", "18:21"],
    ["Priya", "Photos from last month are in the album 📷", "18:30"],
    ["Tomás", "That fog shot over the lake is perfect.", "18:33"],
    ["Ines", "Trail report from Sunday is up.", "18:40"],
    ["Nadia", "Who's bringing the stove?", "18:44"],
    ["Ben", "Rope is in my car.", "18:47"],
    ["Ollie", "Lost a glove at the lot, anyone?", "18:52"]
  ];
  // What arrives during the story, so the display can show it.
  var NEWS = {
    8: ["Sam", "Hail is possible near the summit after 14:00.", "9:40"],
    5: ["Lena", "Uploaded the full ridge panorama.", "9:41"],
    3: ["Ollie", "@alex the tent is yours if you want it.", "9:42"]
  };

  // Pinned messages belong to topics. Photos, Camp food and Weather have none.
  var PINNED = {
    0: "Saturday: Eagle Ridge loop, meet 7:30 at the north lot",
    2: "Saturday: Eagle Ridge loop, meet 7:30 at the north lot",
    4: "GPX for the variant that skips the river crossing",
    3: "Post a photo and a price with every item",
    1: "Read the group rules before your first post",
    7: "Saturday: 2 seats left from the station"
  };

  var ICON_TOGGLE = '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><rect x="3.5" y="4.5" width="17" height="15" rx="3"/><path d="M9 4.5v15"/></svg>';
  var ICON_ROLLUP = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M12.5 7L7.5 12l5 5M18 7l-5 5 5 5"/></svg>';
  // All topics: the chat bubbles the app uses for All on the side rail and the wheel.
  var ICON_ALL_PATH = "M56.03,23.58C57.28,24.1 58.45,24.74 59.55,25.48C64.67,28.91 68,34.44 68,40.68C68,45.92 65.65,50.66 61.85,54.08C62.3,55.7 63.42,57.76 65.22,60.27C65.3,60.38 65.12,60.69 64.96,60.72C61.95,61.31 60.76,60.85 59.1,60.39C57.36,59.91 56.43,58.61 55.69,57.92C53.11,58.97 50.26,59.55 47.25,59.55C45.25,59.55 43.32,59.29 41.48,58.81C40.71,58.61 39.95,58.36 39.21,58.08C50.42,54.45 58.41,44.95 58.41,33.8C58.41,30.29 57.62,26.94 56.18,23.88L56.03,23.58ZM28.39,10C41.86,10 52.78,19.88 52.78,32.08C52.78,44.27 41.86,54.15 28.39,54.15C25.86,54.15 23.43,53.8 21.14,53.16C20.26,53.94 19.11,54.68 17.7,55.37C14.43,56.98 10.36,57.3 5.48,56.34C5.16,56.28 4.96,55.98 5.02,55.67C5.05,55.47 5.19,55.31 5.38,55.24C8.85,53.98 10.57,51.28 10.56,47.14C6.49,43.19 4,37.9 4,32.08C4,19.88 14.92,10 28.39,10Z";
  function allIcon(size) { return '<svg width="' + size + '" height="' + size + '" viewBox="0 0 72 72" fill="currentColor"><path fill-rule="evenodd" d="' + ICON_ALL_PATH + '"/></svg>'; }
  // A topic without an emoji wears the app's topic bubble in its colour, with its initial.
  var BUBBLE_PATH = "M42,4.47C20.7,4.47 3.47,20.33 3.47,39.83C3.47,49.33 7.58,58.24 14.76,64.83L15.29,65.32L15.24,66.03C15.1,67.84 14.77,69.58 14.23,71.25C13.69,72.91 12.37,75.61 10.26,79.4C10.25,79.42 10.25,79.44 10.25,79.45C10.25,79.5 10.29,79.53 10.33,79.53L10.97,79.46C15.93,78.89 20,77.78 23.19,76.13C24.57,75.42 26.17,74.5 28,73.35L28.58,72.99L29.23,73.2C33.3,74.51 37.6,75.19 42,75.19C63.3,75.19 80.53,59.33 80.53,39.83C80.53,20.33 63.3,4.47 42,4.47Z";
  var BUBBLE_TOP = "M9.68,24.61C9.87,24.81 10.19,24.82 10.39,24.63L10.41,24.61C20.52,13.73 31.31,8.5 42.8,8.93C54.26,9.36 64.92,15.37 74.79,26.97C75.03,27.24 75.44,27.29 75.74,27.07C76.03,26.85 76.11,26.44 75.91,26.13C67.78,13.34 56.75,6.69 42.8,6.19C28.82,5.69 17.76,11.55 9.61,23.78C9.43,24.04 9.46,24.39 9.68,24.61Z";
  var BUBBLE_COLORS = { "#6fb9f0": ["#015ec1", "#4bb7ff"], "#ffd67e": ["#ea5800", "#ffdb5c"], "#cb86db": ["#a438bb", "#e57aff"],
    "#8eee98": ["#11b411", "#97e334"], "#ff93b2": ["#e4215a", "#ff7999"], "#fb6f5f": ["#c61505", "#ff714c"] };
  function bubbleIcon(t, size) {
    var c = BUBBLE_COLORS[t.color] || BUBBLE_COLORS["#6fb9f0"], id = "tb" + t.id;
    return '<svg class="tb" width="' + size + '" height="' + size + '" viewBox="0 0 84 84"><defs><linearGradient id="' + id + '" x1="0" y1="1" x2="0" y2="0">' +
      '<stop offset="0" stop-color="' + c[0] + '"/><stop offset="1" stop-color="' + c[1] + '"/></linearGradient></defs>' +
      '<path d="' + BUBBLE_PATH + '" fill="url(#' + id + ')" stroke="' + c[0] + '" stroke-opacity=".55" stroke-width="3"/>' +
      '<path d="' + BUBBLE_TOP + '" fill="#fff" fill-opacity=".28"/>' +
      '<text x="42" y="41" text-anchor="middle" dominant-baseline="central" font-family="' + FONT + ', sans-serif" font-weight="700" font-size="40" fill="#fff">' + t.title.charAt(0).toUpperCase() + "</text></svg>";
  }
  var ICON_PIN_MINI = '<svg width="11" height="11" viewBox="0 0 24 24" fill="currentColor"><path d="M9 3h6l-1 7 3.5 3.5h-11L10 10z"/><rect x="11" y="13" width="2" height="8" rx="1"/></svg>';
  var ICON_KEEP_V = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>';

  // The phone around the chat: the status bar and the header over it, the message field and the
  // system's bar under it, and (on iOS) the hairline between the rows of the glass card.
  var IOS_SIGNAL = '<svg width="18" height="12" viewBox="0 0 18 12" fill="currentColor"><rect y="7.6" width="3.2" height="4.4" rx="1"/><rect x="4.9" y="5.2" width="3.2" height="6.8" rx="1"/><rect x="9.8" y="2.7" width="3.2" height="9.3" rx="1"/><rect x="14.7" width="3.2" height="12" rx="1"/></svg>';
  var IOS_WIFI = '<svg width="17" height="12" viewBox="0 0 17 12" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round"><path d="M1.7 4.2a9.9 9.9 0 0113.6 0"/><path d="M4.6 7.1a5.8 5.8 0 017.8 0"/><path d="M7.4 9.9a1.7 1.7 0 012.2 0" stroke-width="2.6"/></svg>';
  var IOS_BATTERY = '<svg width="27" height="13" viewBox="0 0 27 13"><rect x=".5" y=".5" width="23" height="12" rx="3.8" fill="none" stroke="currentColor" stroke-opacity=".4"/>' +
    '<rect x="2" y="2" width="17.6" height="9" rx="2.3" fill="currentColor"/><path d="M25 4.4v4.2c.8-.3 1.3-1.1 1.3-2.1s-.5-1.8-1.3-2.1z" fill="currentColor" fill-opacity=".45"/></svg>';
  var IOS_CHEVRON = '<svg width="12" height="20" viewBox="0 0 12 20" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M9.8 2L2 10l7.8 8"/></svg>';
  var IOS_CLIP = '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M16.6 7.6l-7.3 7.3a2 2 0 002.9 2.9l7.5-7.5a4.1 4.1 0 00-5.8-5.8l-7.7 7.7a6.1 6.1 0 008.6 8.6l6.4-6.4"/></svg>';
  var IOS_STICKER = '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M20.2 12.6V8.6a4.8 4.8 0 00-4.8-4.8H8.6a4.8 4.8 0 00-4.8 4.8v6.8a4.8 4.8 0 004.8 4.8h4z"/><path d="M12.6 20.2c0-4.3 3.3-7.6 7.6-7.6"/><path d="M8.8 13.7c.8.9 1.9 1.4 3.1 1.4"/><circle cx="9.3" cy="9.8" r=".7" fill="currentColor"/><circle cx="14.7" cy="9.8" r=".7" fill="currentColor"/></svg>';
  var IOS_MIC = '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"><rect x="8.8" y="2.8" width="6.4" height="12" rx="3.2"/><path d="M5.4 11.3a6.6 6.6 0 0013.2 0M12 17.9v3.3"/></svg>';
  var CHROME = {
    // A glass action bar: Back, the group or the topic you are in, and the menu, each in its own glass;
    // under the chat a glass field and the navigation bar.
    android: {
      top: '<div class="statusbar"><span>9:41</span><span>5G ▮▮▮ 87%</span></div><div class="punch"></div>' +
        '<div class="appbar"><span class="hkey back glass" data-el="back-top"><span class="kwell"></span><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5M11 6l-6 6 6 6"/></svg></span>' +
        '<div class="htitle glass"><div class="avatar"><span class="grp">TC</span><span class="tpc"></span></div><div class="titles"><b class="ttl"></b><span class="subtitle"></span></div></div>' +
        '<span class="hkey menu glass" data-el="menu-top"><span class="kwell"></span><svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="5" r="1.8"/><circle cx="12" cy="12" r="1.8"/><circle cx="12" cy="19" r="1.8"/></svg></span></div>',
      separator: "",
      bottom: '<div class="inputbar glass"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="9"/><path d="M8.5 14.5c1 1.2 2.1 1.8 3.5 1.8s2.5-.6 3.5-1.8M9 9.5h.01M15 9.5h.01" stroke-linecap="round"/></svg><span class="fill"></span>' +
        '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M15 7l-6.5 6.5a2.1 2.1 0 003 3L18 10a4.2 4.2 0 00-6-6l-6.5 6.5a6.4 6.4 0 009 9L20 14"/></svg></div>' +
        '<div class="navbar"><svg data-el="back" width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><path d="M11 2.5L4 8l7 5.5z"/></svg><svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="8" cy="8" r="5.5"/></svg><svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="10" height="10" rx="2"/></svg></div>'
    },
    // The iOS status bar beside the Dynamic Island, a navigation bar with a round glass Back (in its
    // well, like the wheel), the group's name and its avatar; under the chat the glass attach button,
    // field and microphone, and the home indicator.
    ios: {
      top: '<div class="statusbar"><span class="clock">9:41</span><span class="sys">' + IOS_SIGNAL + IOS_WIFI + IOS_BATTERY + '</span></div><div class="dynamic-island"></div>' +
        '<div class="appbar"><span class="hkey back glass" data-el="back-top"><span class="kwell"></span>' + IOS_CHEVRON + '</span>' +
        '<div class="htitle"><div class="titles"><b class="ttl"></b><span class="subtitle"></span></div></div>' +
        '<span class="hkey menu" data-el="menu-top"><span class="kwell"></span><span class="avatar"><span class="grp">TC</span><span class="tpc"></span></span></span></div>',
      separator: '<div class="hsep"></div>',
      bottom: '<div class="inputbar"><span class="ibtn attach glass">' + IOS_CLIP + '</span><span class="field glass"><span class="fill"></span>' + IOS_STICKER + '</span>' +
        '<span class="ibtn mic glass">' + IOS_MIC + '</span></div><div class="homebar"></div>'
    }
  };
  var PATTERN = '<svg xmlns="http://www.w3.org/2000/svg"><defs><pattern id="p" width="120" height="120" patternUnits="userSpaceOnUse" patternTransform="rotate(-8)">' +
    '<g fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">' +
    '<path d="M8 40l14-20 9 12 6-7 13 15z"/><path d="M70 20l10 16H60z M70 20v-6"/><circle cx="96" cy="70" r="9"/><path d="M96 63l3 7-3 7-3-7z"/>' +
    '<path d="M20 88l8-13 8 13z M28 88v6"/><path d="M58 92c4-8 14-8 18 0"/><path d="M104 104l6-10 6 10"/></g></pattern></defs>' +
    '<rect width="100%" height="100%" fill="url(#p)"/></svg>';

  function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }
  function lerp(a, b, t) { return a + (b - a) * t; }
  function easeOutQuint(t) { return 1 - Math.pow(1 - t, 5); }
  // How far a critically damped spring has come at x = its rate times the time: no jump, no overshoot.
  function spring(x) { return x <= 0 ? 0 : 1 - (1 + x) * Math.exp(-x); }
  function easeOutQuart(t) { return 1 - Math.pow(1 - t, 4); }
  function easeOutCubic(t) { return 1 - Math.pow(1 - t, 3); }
  function easeInCubic(t) { return t * t * t; }
  function easeInOutCubic(t) { return t < .5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2; }
  // A spring that lands with a little give, for the wheel settling on a topic.
  function easeOutBack(t) { var c1 = 1.15, c3 = c1 + 1; return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2); }
  function smooth(e0, e1, x) { var t = clamp((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t); }
  function now() { return performance.now(); }

  var topics, msgs, state, root, els = {}, listeners = [], tweens = {}, tabSel = {}, popAt = -1e9, pickTimer = 0, scrubTimer = 0;
  var dateTimer = 0, maxChatScroll = 0;
  var wheelIcons = [], knurls = [], lights = [], glows = [];

  function emit(type, data) { listeners.forEach(function (f) { f(type, data); }); }
  function topicById(id) { for (var i = 0; i < topics.length; i++) if (topics[i].id === id) return topics[i]; return topics[0]; }
  function idxOf(id) { for (var i = 0; i < topics.length; i++) if (topics[i].id === id) return i; return 0; }
  function tween(key, to, ms, ease) {
    var from = state[key];
    if (from === to) { delete tweens[key]; return; }
    tweens[key] = { from: from, to: to, start: now(), ms: Math.max(1, ms), ease: ease || easeOutQuint };
  }

  // Every topic wears its icon, the same one everywhere: on the wheel, on the bar, on the rail, in
  // the header. ink: the colour of the All and General glyphs (they follow the selection on the bar).
  function topicIcon(t, size, ink) {
    var c = ink || "var(--paccent)";
    if (t.id === 0) return '<span class="glyph" style="color:' + c + '">' + allIcon(Math.round(size * 1.05)) + "</span>";
    if (t.id === 1) return '<span class="glyph" style="color:' + c + ';font:700 ' + Math.round(size * 0.92) + 'px/1 ' + FONT + ',sans-serif">#</span>';
    if (!t.icon) return bubbleIcon(t, Math.round(size * 1.08));
    return '<span class="emo" style="font-size:' + Math.round(size * 0.9) + 'px">' + t.icon + "</span>";
  }
  function countHtml(t, cls) {
    if (t.mention) return '<span class="' + cls + (t.muted ? " muted" : "") + '">@</span>';
    if (t.unread > 0) return '<span class="' + cls + (t.muted ? " muted" : "") + '">' + t.unread + "</span>";
    return "";
  }
  // Unread messages in the topics other than the one you are in.
  function elsewhere() {
    var unread = 0, mutedUnread = 0, mention = false;
    topics.forEach(function (t) {
      if (t.id === 0 || t.id === state.current) return;
      if (t.muted && !t.mention) mutedUnread += t.unread; else unread += t.unread;
      if (t.mention) mention = true;
    });
    var muted = unread <= 0 && !mention;
    var count = muted ? mutedUnread : unread;
    return { count: count, mention: mention, muted: muted, show: count > 0 || mention };
  }

  function listJoin(names) {
    if (names.length > 3) return names.slice(0, 2).join(", ") + " and " + (names.length - 2) + " more";
    if (names.length > 1) return names.slice(0, -1).join(", ") + " and " + names[names.length - 1];
    return names[0] || "";
  }
  // What the island's display says about the topic under the lens while the wheel turns: its name,
  // its counter, and its latest message, like the topic's row in the list. For All, where the news is.
  function fillDisplay(tp) {
    var chip, sub;
    if (tp.id === 0) {
      var s = elsewhere();
      chip = s.show ? '<span class="count' + (s.muted ? " muted" : "") + '">' + (s.mention ? "@" : "") + (s.count > 0 ? s.count : "") + "</span>" : "";
      var names = topics.filter(function (x) {
        return x.id !== 0 && x.id !== state.current && (x.unread > 0 || x.mention) && (s.muted || !x.muted || x.mention);
      }).map(function (x) { return x.title; });
      // iOS lists up to three of them, as ChatTopicsRollWheelView.newsTitles does.
      if (IOS) sub = names.length ? names.slice(0, 3).join(", ") : "No new messages";
      else sub = names.length ? "New in " + listJoin(names) : "Every topic in one place";
    } else {
      chip = countHtml(tp, "count");
      var list = msgs[tp.id] || [], last = list[list.length - 1];
      // Who is typing, in the words each app uses ("%@ is typing" on iOS).
      sub = typingIn(tp.id) ? '<span class="typing">' + typingIn(tp.id) + (IOS ? " is typing" : " is typing…") + "</span>" : last ? "<b>" + (last[0] === null ? "You" : last[0]) + ":</b> " + last[1] : "";
      if (typingIn(tp.id)) emit("displayTyping", tp.id);
    }
    els.dTitle.innerHTML = (tp.pinned && !IOS ? ICON_PIN_MINI : "") + "<span>" + tp.title + "</span>" + chip;
    els.dSub.innerHTML = sub;
  }

  var DOTS = "<b></b><b></b><b></b>";
  // The bar and the rail: every topic wears its icon. The pinned topics come first and end with a pin
  // and a taller tick on the scale, where the wheel clicks firmer; the lens wraps the topic you are in.
  function buildTabs() {
    var h = '<div class="tdrop puck"></div><div class="tlens puck"></div><svg class="tscale" aria-hidden="true"></svg>';
    var v = '<div class="vdrop puck"></div><div class="vlens puck"></div><svg class="vscale" aria-hidden="true"></svg>';
    // Someone typing in a topic: a small badge in its counter's colour at the icon's lower corner
    // (on Android; iOS's tabs keep to their counters).
    var badge = IOS ? "" : '<span class="tb">' + DOTS + "</span>";
    topics.forEach(function (t, i) {
      var next = topics[i + 1];
      h += '<div class="tab" data-el="tab:' + t.id + '"><span class="ic">' + topicIcon(t, TAB_ICON, "currentColor") + badge + '</span><span class="nm">' + (t.short || t.title) + "</span>" + countHtml(t, "count") + "</div>";
      if (t.pinned && !(next && next.pinned)) h += '<div class="pinmark">' + ICON_PIN_MINI + "</div>";
      v += '<div class="vtab" data-el="vtab:' + t.id + '"><span class="ic">' + topicIcon(t, RAIL_ICON, "currentColor") + badge + countHtml(t, "count") + '</span><span class="t">' + (t.short || t.title) + "</span></div>";
    });
    els.tabs.innerHTML = h;
    els.vtabs.innerHTML = v;
    els.tlens = els.tabs.querySelector(".tlens"); els.vlens = els.vtabs.querySelector(".vlens");
    els.tdrop = els.tabs.querySelector(".tdrop"); els.vdrop = els.vtabs.querySelector(".vdrop");
    refreshTyping();
  }

  // The wheel's scale, unrolled under the tabs (and along the rail): a tick every TICK and a taller
  // tick where the pinned topics end. The tabs keep Telegram's own unread counters for news: the
  // scale's lights are the wheel's alone.
  function buildScale() {
    if (!els.tabs.querySelector(".tscale")) return;
    // Only the bar or the rail is laid out at a time; the other one's scale is built when it shows.
    state.scaleMode = state.mode;
    var NS = "http://www.w3.org/2000/svg";
    function scale(svg, length, horizontal, edge) {
      // iOS's gear line is the plain ticks, without the taller one where the pinned topics end.
      var pin = edge && !IOS ? edge() : -1, d = "";
      for (var x = TICK / 2; x < length; x += TICK) {
        if (pin >= 0 && Math.abs(pin - x) < 6) continue;
        d += horizontal ? "M" + x.toFixed(1) + " 0v3.5" : "M0 " + x.toFixed(1) + "h3.5";
      }
      if (pin >= 0) d += horizontal ? "M" + pin.toFixed(1) + " -2.5v6" : "M-2.5 " + pin.toFixed(1) + "h6";
      svg.setAttribute(horizontal ? "width" : "height", Math.ceil(length));
      svg.innerHTML = '<path d="' + d + '"/>';
    }
    scale(els.tabs.querySelector(".tscale"), els.tabs.scrollWidth + TICK, true, function () {
      var mark = els.tabs.querySelector(".pinmark");
      return mark ? mark.offsetLeft + mark.offsetWidth / 2 : -1;
    });
    scale(els.vtabs.querySelector(".vscale"), els.vtabs.scrollHeight + TICK, false, function () {
      var last = null;
      topics.forEach(function (tp) { if (tp.pinned) last = vtabEl(tp.id); });
      return last ? last.offsetTop + last.offsetHeight : -1;
    });
  }
  // The drum curves away at the ends of the well: a topic nearing either end of the bar shrinks a
  // little about its middle, its lens with it, like the icons on the wheel as they turn away. visW:
  // how much of the row the tabs show.
  function drumAt(x, visW) {
    // On iOS the tabs keep their size along the whole bar.
    if (IOS) return 1;
    var cx = x - state.hScroll;
    return lerp(0.84, 1, smooth(-24, 36, Math.min(cx, visW - cx)));
  }
  function drumScale(tab, visW) { return drumAt(tab.offsetLeft + tab.offsetWidth / 2, visW); }
  function drum(visW) {
    var tabs = els.tabs.querySelectorAll(".tab");
    for (var i = 0; i < tabs.length; i++) {
      var s = drumScale(tabs[i], visW) * pressScale(tabs[i]);
      tabs[i].style.transform = s < 0.999 ? "scale(" + s.toFixed(3) + ")" : "";
    }
    state.drumW = visW;
  }
  function tabEl(id) { return els.tabs.querySelector('.tab[data-el="tab:' + id + '"]'); }
  function vtabEl(id) { return els.vtabs.querySelector('.vtab[data-el="vtab:' + id + '"]'); }
  // The middle of a tab's icon, in the bar's tab row.
  function iconX(tab) { return tab.offsetLeft + TAB_PAD + SLOT / 2; }

  // Someone typing in a topic: its light on the scale turns into three dots that rise and fall, on
  // the wheel and on the bar alike, and the header says so while you are in it.
  function typingIn(id) { return state.typing[id] || null; }
  function refreshTyping() {
    buildScale();
    var who = typingIn(state.current);
    if (els.subtitle) {
      els.subtitle.classList.toggle("typing", !!who);
      if (who) els.subtitle.textContent = who + " is typing…"; else buildHeaderSubtitle();
    }
  }

  function buildWheel() {
    els.belt.innerHTML = "";
    // Glows at the ends of the well, for news further along the wheel than it shows.
    glows = ["l", "r"].map(function (side) {
      var g = document.createElement("div");
      g.className = "eg " + side;
      els.belt.appendChild(g);
      return g;
    });
    wheelIcons = topics.map(function (t) {
      var e = document.createElement("div");
      e.className = "wi";
      e.innerHTML = topicIcon(t, 20);
      els.belt.appendChild(e);
      return e;
    });
    knurls = [];
    for (var k = 0; k < 17; k++) {
      var n = document.createElement("div");
      n.className = "kn";
      els.belt.appendChild(n);
      knurls.push(n);
    }
    // A light on the dial's scale under every topic with news, like a station marker on a radio.
    lights = topics.map(function () {
      var l = document.createElement("div");
      l.innerHTML = "<b></b><b></b><b></b>";
      els.belt.appendChild(l);
      return l;
    });
    refreshWheelDots();
  }
  function hasNews(t) { return t.id !== 0 && t.id !== state.current && (t.mention || t.unread > 0); }
  function isTyping(t) { return t.id !== 0 && t.id !== state.current && !!typingIn(t.id); }
  // A light on the wheel's scale: news lights it, someone typing makes it move.
  function lit(t) { return hasNews(t) || isTyping(t); }
  function refreshWheelDots() {
    topics.forEach(function (t, i) {
      if (!lights[i]) return;
      lights[i].className = "lt" + (isTyping(t) ? " typing" : !hasNews(t) ? " off" : t.mention ? " mention" : t.muted ? " muted" : "");
    });
    var s = elsewhere();
    els.badge.style.display = s.show ? "" : "none";
    els.badge.className = "badge" + (s.muted ? " muted" : "");
    els.badge.textContent = (s.mention ? "@" : "") + (s.count > 0 ? s.count : "");
  }

  function buildChat() {
    var list = HISTORY.slice();
    if (state.current === 0) {
      [2, 3, 4, 8].forEach(function (id) { var m = msgs[id]; list.push(["topic", topicById(id)]); list.push(m[m.length - 1]); });
    } else {
      list.push(["topic", topicById(state.current)]);
      list = list.concat(msgs[state.current] || []);
    }
    // As in a Telegram group: a run of messages from one person shows their name on the first and
    // their avatar beside the last (and, on iOS, the bubble's tail).
    var html = "";
    list.forEach(function (m, i) {
      if (m[0] === "topic") { html += '<div class="topic-start">' + (m[1].id === 1 ? "#" : m[1].icon || bubbleIcon(m[1], 14)) + " " + m[1].title + "</div>"; return; }
      var out = m[0] === null, prev = list[i - 1], next = list[i + 1];
      var firstOfRun = !prev || prev[0] !== m[0], lastOfRun = !next || next[0] !== m[0];
      var av = !out && lastOfRun ? '<i class="av" style="background:' + avatarFill(m[0]) + '">' + m[0].charAt(0) + "</i>" : "";
      html += '<div class="msg' + (out ? " out" : "") + (firstOfRun ? "" : " cont") + (lastOfRun && IOS ? " last" : "") + '">' + av + (out || !firstOfRun ? "" : '<span class="who" style="color:' + nameColor(m[0]) + '">' + m[0] + "</span>") + m[1] + '<span class="when">' + m[2] + "</span></div>";
    });
    els.chatInner.innerHTML = html;
    state.chatShown = state.current;
    // A new chat opens at its latest messages. It scrolls back until its first message comes down
    // to just under the island.
    state.chatScroll = 0; delete tweens.chatScroll;
    var restTop = INPUT_TOP - 8 - els.chatInner.offsetHeight;
    maxChatScroll = Math.max(0, Y0 + PANEL + GAP - restTop);
  }

  // Telegram's avatar colours, picked by name. Telegram iOS paints avatars as gradients, lighter at the
  // top, and names in deeper shades of the same colours.
  var AVATAR_COLORS = ["#e17076", "#f0a35a", "#a695e7", "#7bc862", "#6ec9cb", "#65aadd", "#ee7aae"];
  var IOS_AVATARS = [["#ff885e", "#ff516a"], ["#ffcd6a", "#ffa85c"], ["#82b1ff", "#665fff"], ["#a0de7e", "#54cb68"], ["#53edd6", "#28c9b7"], ["#72d5fd", "#2a9ef1"], ["#e0a2f3", "#d669ed"]];
  var IOS_NAMES = ["#d5434e", "#e07a1f", "#7a5ce0", "#3a9f3a", "#1f9ea0", "#2f84d0", "#c94f96"];
  function avatarIndex(name) {
    var h = 0;
    for (var i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) % 997;
    return h % AVATAR_COLORS.length;
  }
  function avatarColor(name) { return AVATAR_COLORS[avatarIndex(name)]; }
  function avatarFill(name) {
    var g = IOS_AVATARS[avatarIndex(name)];
    return IOS ? "linear-gradient(180deg, " + g[0] + ", " + g[1] + ")" : avatarColor(name);
  }
  function nameColor(name) { return IOS ? IOS_NAMES[avatarIndex(name)] : avatarColor(name); }

  // The header shows the group in All, and the topic itself once you are in one, like the app.
  // Telegram iOS keeps the group and its members in the navigation bar; the tabs say which topic you
  // are in.
  function buildHeaderSubtitle() {
    var t = topicById(state.current);
    els.subtitle.textContent = t.id === 0 || IOS ? "1,204 members, 87 online" : t.count.toLocaleString("en-US") + " messages";
  }
  function buildHeader() {
    var t = topicById(state.current);
    if (t.id === 0 || IOS) {
      els.title.textContent = "Trailhead Club";
      els.avGroup.style.opacity = 1;
      els.avTopic.style.opacity = 0;
      els.inputHint.textContent = "Message";
    } else {
      els.title.textContent = t.title;
      els.avTopic.innerHTML = t.id === 1 ? '<span style="color:var(--paccent);font:700 24px/1 Roboto,sans-serif">#</span>' : topicIcon(t, 27);
      els.avTopic.style.background = t.id === 1 ? "var(--chip)" : "transparent";
      els.avGroup.style.opacity = 0;
      els.avTopic.style.opacity = 1;
      els.inputHint.textContent = "Message in " + t.title;
    }
    buildHeaderSubtitle();
    refreshTyping();
  }

  function bindPinned(animated) {
    var text = PINNED[state.current], title = IOS ? "Pinned Message" : "Pinned message";
    if (text) {
      if (animated && state.pinnedText && state.pinnedText !== text && state.panelVis > 0.5) {
        state.pinnedPrev = state.pinnedText;
        state.pinnedAt = now();
      }
      state.pinnedText = text;
      els.ptextA.innerHTML = "<b>" + title + "</b><span>" + text + "</span>";
      if (state.pinnedPrev) els.ptextB.innerHTML = "<b>" + title + "</b><span>" + state.pinnedPrev + "</span>";
      tween("panelVis", 1, animated ? PANEL_MS : 1, easeOutCubic);
    } else {
      tween("panelVis", 0, animated ? PANEL_MS : 1, easeOutCubic);
    }
  }

  function refresh(pop) {
    var before = elsewhere();
    buildTabs(); refreshWheelDots();
    var after = elsewhere();
    if (pop && (after.count > before.count || (after.mention && !before.mention))) popAt = now();
  }

  function setRect(el, x, y, w, h) { el.style.left = x + "px"; el.style.top = y + "px"; el.style.width = Math.max(0, w) + "px"; el.style.height = Math.max(0, h) + "px"; }
  function q(sel) { return root.querySelector(sel); }

  // Where along the bar each tab's icon is, so the wheel shows the icon it is passing, as if it laid
  // the icons down on the bar.
  function tabIndexAt(x, barX) {
    var tabs = els.tabs.querySelectorAll(".tab"), n = tabs.length;
    if (!n) return 0;
    var base = barX + KEY - state.hScroll, c = [];
    for (var i = 0; i < n; i++) c.push(base + iconX(tabs[i]));
    if (x <= c[0]) return clamp(-(c[0] - x) / CELL, -0.5, 0);
    for (var j = 0; j < n - 1; j++) if (x < c[j + 1]) return j + (x - c[j]) / (c[j + 1] - c[j]);
    return clamp(n - 1 + (x - c[n - 1]) / CELL, n - 1, n - 0.5);
  }

  function nudgeOffset(t) {
    if (!state.nudgeAt) return 0;
    var s = (t - state.nudgeAt) / 1000;
    if (s < 0 || s > NUDGE_MS / 1000) return 0;
    return state.nudgeDir * 0.32 * Math.sin(s * Math.PI * 2 * 1.9) * Math.exp(-s * 5.2);
  }

  // A key pressed into its well: the icon gives a little and the well's shadow deepens.
  function pressKeyStyle(key, well, p) {
    key.lastElementChild.style.transform = p > 0.001 ? "scale(" + (1 - 0.08 * p).toFixed(3) + ")" : "";
    well.style.boxShadow = p > 0.001 ? "inset 0 " + (1 + 1.2 * p).toFixed(2) + "px " + (2.5 + 2 * p).toFixed(2) + "px var(--well-shade), inset 0 -1px 0 var(--well-lip)" : "";
  }

  function render(once) {
    var t = now();
    for (var k in tweens) {
      var tw = tweens[k], p = clamp((t - tw.start) / tw.ms, 0, 1);
      state[k] = lerp(tw.from, tw.to, tw.ease(p));
      if (p >= 1) { state[k] = tw.to; delete tweens[k]; }
    }
    // A finger on a tab (see pressTab): how far the tab has sunk into the drop of glass under it, all
    // the way as the drop forms and back up once the finger lifts.
    var finger = state.press;
    state.pressSink = 0;
    if (finger) {
      if (finger.up == null) state.pressSink = spring(DROP_BLOOM * (t - finger.down) / 1000);
      else { var sx = DROP_SETTLE * (t - finger.up) / 1000; state.pressSink = finger.sinkAtUp * (1 + sx) * Math.exp(-sx); }
      if (finger.up != null && t - finger.up > 700) state.press = null;
    }
    var res = state.reserveT, rt = state.rollT, mode = state.mode;
    var side = mode === "side", bottom = mode === "bottom", top = !side && !bottom;
    var pv = state.panelVis;
    // How much the pinned message shares the wheel's island: fully while the topics are rolled up
    // in the top row. At the bottom and on the side it keeps its own glass at the top.
    var join = top ? pv * (1 - res) : 0;
    // How far the pinned message has stepped out of the island, down out of the bar's way.
    var open = Math.max(res, smooth(0, 0.6, rt));

    // The chat: its top gets the bar's row back while the topics are rolled up, and its side the
    // rail's column, where the upright wheel floats over the avatars. At the bottom the bar keeps its
    // row whatever it does, the wheel's track in it.
    var topSpace = top ? PANEL_STEP * res : 0;
    var sideSpace = side ? lerp(SIDE_ROLLED, SIDE_INSET, res) : 0;
    var bottomSpace = bottom ? BOTTOM_ROW : 0;
    var chatTop = Y0 + topSpace + (PANEL + GAP) * pv;
    els.chatInner.style.paddingLeft = (10 + sideSpace) + "px";
    els.chatInner.style.bottom = (H - INPUT_TOP + 8 + bottomSpace) + "px";
    // Switching topics, as in the app (see beginTopicSwitch in ChatActivity): at once the glass goes to
    // the topic and the chat lets go, dimming a little and starting to slide away from the side the
    // new topic is on. Once the glass has all but landed, the chat is caught in a picture that holds
    // still while the topic opens behind it (the frames that takes in the app cannot show: nothing
    // moves); then the picture slides on and fades while the new messages glide in as one piece from
    // that side (across for a row of tabs, up or down for side tabs), crossing it. On iOS, whose
    // animations run outside the app and never wait for it, the topic opens at once behind the chat
    // as it is, and the two cross as soon as it has loaded.
    var shift = 0, chatAlpha = 1;
    if (state.opening && IOS) {
      chatAlpha = state.leaveAlpha; shift = state.leaveShift;
      openTopic(t);
    } else if (state.opening) {
      var lp = clamp((t - state.slideAt) / LEAVE_MS, 0, 1), le = easeOutCubic(lp);
      chatAlpha = state.leaveAlpha * lerp(1, 0.85, le); shift = lerp(state.leaveShift, -state.leaveDir * 8, le);
      if (!glassMoving() || t - state.slideAt > 500) openTopic(t);
    }
    if (!state.opening) {
      if (state.chatShown !== state.current) buildChat();
      var since = t - state.openAt - LOAD_MS;
      if (since < 0) { shift = state.slideDir * 36; chatAlpha = 0; }
      else {
        if (els.ghost && !state.ghostLeaving) { state.ghostLeaving = true; state.ghostAt = t; }
        // The picture gets a head start of a few frames, so the two cross rather than stack.
        var ep = clamp((since - (IOS ? 60 : ENTER_DELAY_MS)) / ENTER_MS, 0, 1);
        if (ep < 1) { shift = state.slideDir * 36 * (1 - easeOutQuint(ep)); chatAlpha = easeOutCubic(Math.min(1, ep / 0.5)); }
      }
    }
    state.chatAlpha = chatAlpha; state.chatShift = shift;
    var across = state.slideUp ? 0 : shift, down = state.slideUp ? shift : 0;
    // Side tabs rolled out over the chat: the conversation steps aside for them, back and to the right
    // about its bottom right corner, just far enough that no message hides under the rail, in one
    // motion with the rail, and comes back as it winds up. Kept open, the rail has its own column.
    var aside = side ? state.keepT * rt : 0, chatScale = 1 - (1 - ASIDE_SCALE) * aside;
    els.chatInner.style.transform = "scale(" + chatScale.toFixed(4) + ") translate(" + across.toFixed(2) + "px," + (state.chatScroll + down).toFixed(2) + "px)";
    els.chatInner.style.opacity = chatAlpha.toFixed(3);
    tuckAvatars(side, rt);
    // The picture of the chat being left: still while the topic opens, then it slides on, recedes a
    // little and fades.
    if (els.ghost) {
      var gp = state.ghostLeaving ? clamp((t - state.ghostAt) / (IOS ? 220 : GHOST_MS), 0, 1) : 0;
      if (gp >= 1) {
        els.ghost.remove(); els.ghost = null;
      } else {
        var ge = easeOutCubic(gp), gShift = -state.leaveDir * (IOS ? 20 : 12) * ge, gw = els.ghost.offsetWidth / 2, gh = els.ghost.offsetHeight / 2;
        els.ghost.style.transform = "translate(" + (state.slideUp ? 0 : gShift).toFixed(2) + "px," + (state.slideUp ? gShift : 0).toFixed(2) + "px) " +
          "translate(" + (-gw) + "px," + (-gh) + "px) scale(" + (1 - 0.03 * ge).toFixed(4) + ") translate(" + gw + "px," + gh + "px) " + state.ghostTransform;
        els.ghost.style.opacity = (state.ghostAlpha * (1 - ge)).toFixed(3);
      }
    }
    els.date.style.top = (chatTop + 4) + "px";
    els.date.style.opacity = state.dateT.toFixed(3);
    els.date.style.marginLeft = (sideSpace / 2) + "px";
    els.inputbar.style.top = INPUT_TOP + "px";

    var hp = clamp((t - state.headerAt) / HEADER_MS, 0, 1);
    els.titles.style.opacity = hp < 1 ? easeOutCubic(hp) : 1;
    els.titles.style.transform = hp < 1 ? "translateY(" + (6 * (1 - easeOutCubic(hp))) + "px)" : "";

    // Back and the menu sit in wells, like the wheel in its own: one instrument, rolled up or out.
    // A press pushes a key further in, like the wheel.
    els.kwBack.style.opacity = 1; els.kwMenu.style.opacity = 1;
    pressKeyStyle(els.keyBack, els.kwBack, state.keyBackT);
    pressKeyStyle(els.keyMenu, els.kwMenu, state.keyMenuT);

    // Where the wheel is. Rolled up it sits in its well at the island's start; rolling out it travels
    // to the far end of the row, laying the tabs down behind it, and rises out of its well onto the
    // bar's line (at the bottom it is on it already). On the side it stands at the top of the rail,
    // which drops down out of it like a blind. The rim around the wheel's track: in its island rolled
    // up, in the bar's row rolled out (on Android the bar is the track itself).
    var wx, wy, wheelAlpha, barX = 0;
    var rimIn = bottom ? B_RIM_IN : RIM, rimOut = bottom ? B_RIM_OUT : T_RIM_OUT;
    var rowIn = BAR + rimIn * 2, rowOut = BAR + rimOut * 2;
    if (!side) {
      wx = lerp(M + rimIn, M + FULL - rimOut - WHEEL_W, rt);
      wy = bottom ? lerp(YB_EDGE - BAR - rimIn, YB_EDGE - BAR - rimOut, rt) : lerp(Y0 + rimIn, Y0 + rimOut, rt);
      wheelAlpha = 1 - smooth(0.8, 0.97, rt);
      barX = lerp(rimIn, rimOut, rt);
    } else {
      wx = M + lerp(V_ISLAND_W, RAIL, rt) / 2 - BAR / 2; wy = Y0 + RIM;
      wheelAlpha = 1 - clamp(rt / 0.3, 0, 1);
    }
    var phys = side ? state.wheelPos : tabIndexAt(wx - M + WHEEL_W / 2, barX);
    var belt = lerp(state.wheelPos, phys, side ? 0 : smooth(0, 0.3, rt)) + nudgeOffset(t);
    state.belt = belt;

    // The display tunes to the topic under the lens while the wheel turns.
    var dv = state.scrubT * wheelAlpha * (1 - smooth(0, 0.3, rt));
    els.display.style.display = dv > 0.01 ? "flex" : "none";
    els.display.classList.toggle("compact", bottom);
    if (dv > 0.01) {
      var di = clamp(Math.round(belt), 0, topics.length - 1);
      if (di !== state.displayIndex) {
        var first = state.displayIndex == null;
        fillDisplay(topics[di]);
        state.displayDir = first ? 0 : Math.sign(di - state.displayIndex);
        state.displayIndex = di; state.displayAt = t;
        var dw0 = els.dl.offsetWidth;
        if (first) { delete tweens.dispW; state.dispW = dw0; } else tween("dispW", dw0, 220, easeOutCubic);
      }
    } else {
      state.displayIndex = null;
    }

    // The island. At the top, alone it holds just the wheel; with a pinned message it spans the row
    // and the message is its display; while the wheel turns with nothing pinned it stretches to show
    // the topic under the lens. At the bottom it is the wheel's track, in the bar's own row, and it
    // stretches the same way. Rolling out, it becomes the bar, at the bar's own size.
    var stretch, aloneW;
    if (bottom) {
      stretch = clamp(B_DISPLAY_X - M + B_DISPLAY_PAD + state.dispW + B_DISPLAY_END, B_ISLAND, FULL);
      aloneW = lerp(B_ISLAND, stretch, state.scrubT * (1 - smooth(0, 0.3, rt)));
    } else {
      stretch = clamp(DISPLAY_X - M + DISPLAY_PAD + state.dispW + DISPLAY_END, ISLAND_ALONE, FULL);
      aloneW = lerp(ISLAND_ALONE, stretch, state.scrubT * (1 - smooth(0, 0.3, rt)));
    }
    var islandW = lerp(aloneW, FULL, join);
    var islandRight, kp = 0;
    if (!side) {
      // The island's rim gives way as the wheel rises onto the bar's line, and the wheel's track
      // stretches with it into the bar's surface: the wheel is its far end until it fades there.
      var bw = lerp(islandW, FULL, rt), bh = lerp(rowIn, rowOut, rt), ch = bh, cr = bh / 2;
      if (CARD && top) {
        // On iOS that row is the first of the glass card, and the pinned message, stepping down out
        // of the island, becomes its second row, under a hairline. A single row is a capsule, two
        // rows have corners of 20 pt.
        ch = Math.max(bh, TAB_ROW * open + PANEL * pv);
        cr = Math.min(ch / 2, lerp(PANEL / 2, 20, smooth(PANEL + 2, PANEL + 22, ch)));
        els.hsep.style.top = (TAB_ROW * open) + "px";
        els.hsep.style.opacity = (pv * open).toFixed(3);
      }
      setRect(els.hShell, M, bottom ? YB_EDGE - bh : Y0, bw, ch);
      els.hShell.style.borderRadius = cr + "px";
      els.hShell.style.display = "block";
      els.vShell.style.display = "none";
      islandRight = M + bw;
      setRect(els.hwell, barX, barX, wx - M + WHEEL_W - barX, BAR);
      els.hbar.style.left = barX + "px"; els.hbar.style.top = barX + "px";
      // The track's ends are the drum's, shaded where it curves away. Rolled up the wheel shades its
      // own; rolling out, the track's left end takes over as the wheel leaves it, its right end as the
      // wheel fades there.
      els.esL.style.opacity = smooth(0, 0.2, rt).toFixed(3);
      els.esR.style.opacity = smooth(0.8, 1, rt).toFixed(3);
      // The tabs show up to the wheel, which hides them under it until it fades at the far end.
      var local = wx - M - barX, reveal = smooth(0.8, 1, rt) * (WHEEL_W + 20);
      var mask = rt < 0.999 ? "linear-gradient(90deg, #000 0, #000 " + (local - 20 + reveal) + "px, transparent " + (local + reveal) + "px)" : "none";
      els.hbar.style.webkitMaskImage = mask; els.hbar.style.maskImage = mask;
      els.hRoll.style.opacity = res;
      els.hRoll.style.visibility = res > 0.02 ? "visible" : "hidden";
      // Keep open only ever shows on a bar rolled out from the wheel; « winding a kept bar up just fades.
      kp = state.keepT * smooth(0.92, 1, rt);
      els.keep.style.opacity = kp;
      els.keep.style.visibility = kp > 0.02 ? "visible" : "hidden";
      els.keep.style.transform = "translateX(" + (10 * (1 - kp)) + "px)";
      // The tabs fade into the track's shaded ends where there is more of them, and before Keep open.
      var clipW = els.tabsClip.offsetWidth, cover = kp * Math.max(0, els.keep.offsetWidth - KEY);
      var maxScroll = Math.max(0, els.tabs.scrollWidth - clipW + cover);
      var fadeL = smooth(0, 12, state.hScroll) * 18, fadeR = Math.max(cover > 1 ? 18 : 0, smooth(0, 12, maxScroll - state.hScroll) * 18);
      var cm = "linear-gradient(90deg, transparent 0, #000 " + fadeL.toFixed(1) + "px, #000 " + (clipW - cover - fadeR).toFixed(1) + "px, transparent " + (clipW - cover).toFixed(1) + "px)";
      els.tabsClip.style.webkitMaskImage = cm; els.tabsClip.style.maskImage = cm;
      state.tabsVisR = KEY + clipW - cover;
      drum(clipW - cover);
    } else {
      var railH = INPUT_TOP - GAP - Y0;
      var vw = lerp(V_ISLAND_W, RAIL, rt), vh = lerp(V_ISLAND_H, railH, rt), inset = lerp(RIM, RAIL_RIM, rt);
      setRect(els.vShell, M, Y0, vw, vh);
      els.vShell.style.borderRadius = lerp(PANEL / 2, RAIL_R, rt) + "px";
      els.vShell.style.display = "block";
      els.hShell.style.display = "none";
      islandRight = M + vw;
      // The rail's surface is the wheel's track standing up: it grows down out of the wheel's well as
      // the rail drops, its ends shaded where the drum curves away.
      setRect(els.vwell, inset, inset, vw - inset * 2, vh - inset * 2);
      els.vwell.style.borderRadius = lerp(BAR / 2, RAIL_R - RAIL_RIM, rt) + "px";
      els.esT.style.opacity = els.esB.style.opacity = smooth(0.3, 0.8, rt).toFixed(3);
      // The gear line, down the track's right edge, while the rail winds up into the upright wheel or
      // drops down out of it. The rail's tabs fade early, so it is the track's; it hands over to the
      // wheel's own pale ticks as the wheel appears.
      els.vgear.style.opacity = (Math.pow(1 - rt, 1.5) * clamp(rt / 0.3, 0, 1)).toFixed(3);
      els.vrail.style.height = (railH - RAIL_RIM * 2) + "px";
      els.vrail.style.opacity = clamp((rt - 0.2) / 0.5, 0, 1);
      els.vRoll.style.opacity = res;
      els.vRoll.style.display = res > 0.02 ? "" : "none";
      els.vKeep.style.display = state.keepT < 0.02 ? "none" : "";
      els.vKeep.style.opacity = state.keepT * smooth(0.6, 1, rt);
    }
    els.tabs.style.transform = "translateX(" + (-state.hScroll) + "px)";
    // The gear line, the wheel's knurled ticks under the tabs or down the rail: none while the bar is
    // out or kept open. Winding up it shows, subtly at first, until it is the wheel's own pale ticks,
    // and rolling out it goes the other way. The lights stay, for news and who is typing.
    var tsc = els.tabs.querySelector(".tscale");
    if (tsc) tsc.style.opacity = Math.pow(1 - rt, 1.5).toFixed(3);
    if (state.scaleMode !== state.mode || !state.scaleLaidOut) { state.scaleLaidOut = true; buildScale(); }

    // The pinned message. At the top it is the island's display while rolled up and its own panel
    // under the bar while the bar is out: it clears the row quickly, before the bar rolls over it, and
    // takes its own glass back as it goes; in the island it keeps its height and narrows with the
    // island instead. On the side it sits at the top beside the wheel's island or the rail, and the
    // display takes its place while the wheel turns. At the bottom it keeps to the top. On iOS, at the
    // top, it is the glass card's second row and never has glass of its own.
    var panelLeft, panelTop = Y0, docked = 0, asDisplay = 0;
    if (top) {
      panelLeft = lerp(DISPLAY_X, M, open);
      panelTop = Y0 + PANEL_STEP * open;
      docked = (1 - res) * (1 - smooth(0, 0.6, rt));
    } else if (side) {
      panelLeft = lerp(M + V_ISLAND_W + GAP, M + RAIL + GAP, open);
      asDisplay = dv;
    } else {
      panelLeft = M;
    }
    var panelW = W - M - panelLeft;
    if (docked > 0.01) panelW = lerp(panelW, Math.min(panelW, islandRight - panelLeft), docked);
    // With nothing pinned, the display on the side brings its own panel, as wide as it needs. Its
    // glass comes first and goes last, around the display's words.
    if (asDisplay > 0.01 && pv < 1) panelW = lerp(Math.min(panelW, DISPLAY_PAD + state.dispW + DISPLAY_END), panelW, pv);
    var ownGlass = smooth(0, 0.25, asDisplay), pshow = Math.max(pv, ownGlass);
    setRect(els.pinned, panelLeft, panelTop, panelW, PANEL * lerp(pv, 1, Math.max(docked, ownGlass)));
    els.pinned.style.opacity = pshow;
    els.pinned.style.display = pshow > 0.01 && panelW > 1 ? "block" : "none";
    els.pglass.style.opacity = CARD && top ? 0 : 1 - docked;
    els.pcontent.style.opacity = side && pv <= 0.01 ? 0 : 1;
    // The pinned message's words are gone by the time the display's come in, over the same line.
    els.ptexts.style.opacity = (1 - smooth(0, 0.5, side ? dv : state.scrubT * docked)).toFixed(3);
    var pp = clamp((t - state.pinnedAt) / PANEL_MS, 0, 1);
    if (pp < 1) {
      els.ptextA.style.transform = "translateY(" + (14 * (1 - easeOutCubic(pp))) + "px)";
      els.ptextA.style.opacity = easeOutCubic(pp);
      els.ptextB.style.display = "flex";
      els.ptextB.style.transform = "translateY(" + (-14 * easeOutCubic(pp)) + "px)";
      els.ptextB.style.opacity = 1 - easeOutCubic(pp);
    } else {
      els.ptextA.style.transform = ""; els.ptextA.style.opacity = 1; els.ptextB.style.display = "none";
    }

    // The wheel itself, standing up on the side. A press pushes the dial into its well.
    els.wheel.classList.toggle("v", side);
    els.wheel.style.display = wheelAlpha > 0.01 ? "block" : "none";
    var along = side ? "translateY(" : "translateX(", squeeze = side ? "px) scaleY(" : "px) scaleX(";
    if (wheelAlpha > 0.01) {
      var press = 1 - 0.05 * state.pressT * (1 - rt);
      els.wheel.style.left = wx + "px";
      els.wheel.style.top = wy + "px";
      els.wheel.style.transform = "scale(" + press + ")";
      els.wheel.style.opacity = wheelAlpha;
      // Its well is the island's: the wheel turns in it, rolled up or rolling.
      els.well.style.opacity = 0;
      // Its lens stays on your topic as it rolls on: the bar's lens takes over from the wheel's.
      els.lens.style.opacity = side ? "" : (1 - smooth(0.02, 0.3, rt)).toFixed(3);
      for (var i = 0; i < wheelIcons.length; i++) {
        var th = (i - belt) * STEP, e = wheelIcons[i];
        if (Math.abs(th) > 1.52) { e.style.display = "none"; continue; }
        var c = Math.cos(th), face = clamp(1 - Math.abs(i - belt), 0, 1);
        var sc = (0.46 + 0.54 * c) * (1 + 0.08 * face);
        e.style.display = "grid";
        e.style.transform = along + (WHEEL_R * Math.sin(th)).toFixed(2) + "px) scale(" + sc.toFixed(3) + ")";
        e.style.opacity = (Math.pow(c, 1.4) * (0.62 + 0.38 * face)).toFixed(3);
        e.style.zIndex = Math.round(c * 10);
      }
      // The lights turn with the drum on its scale; the tick under a lit topic gives way to it.
      var glowL = 0, glowR = 0, quietL = 0, quietR = 0;
      for (var li = 0; li < lights.length; li++) {
        var lt = lights[li], tp = topics[li], lth = (li - belt) * STEP;
        var news = lit(tp);
        if (news) {
          // Out of sight beyond either end, a topic with news makes that end of the well glow.
          var away = smooth(1.3, 2.3, Math.abs(li - belt)), loud = !tp.muted || tp.mention;
          if (li < belt) { if (loud) glowL += away; else quietL += away; }
          else { if (loud) glowR += away; else quietR += away; }
        }
        if (!news || Math.abs(lth) > 1.45) { lt.style.display = "none"; continue; }
        lt.style.display = isTyping(tp) ? "flex" : "block";
        lt.style.transform = along + (WHEEL_R * Math.sin(lth)).toFixed(2) + squeeze + Math.max(0.35, Math.cos(lth)).toFixed(2) + ")";
        lt.style.opacity = Math.pow(Math.cos(lth), 1.2).toFixed(2);
      }
      glows[0].className = "eg l" + (glowL < 0.05 && quietL > 0 ? " muted" : "");
      glows[1].className = "eg r" + (glowR < 0.05 && quietR > 0 ? " muted" : "");
      glows[0].style.opacity = Math.min(1, glowL + 0.6 * quietL).toFixed(2);
      glows[1].style.opacity = Math.min(1, glowR + 0.6 * quietR).toFixed(2);
      var base = Math.floor(belt * 2);
      for (var j = 0; j < knurls.length; j++) {
        var kp = (base + j - 8) / 2, u = kp - belt, kt = u * STEP, kn = knurls[j];
        var under = kp === Math.round(kp) && kp >= 0 && kp < topics.length && lit(topics[kp]);
        if (Math.abs(kt) > 1.45 || under) { kn.style.display = "none"; continue; }
        kn.style.display = "block";
        kn.style.transform = along + (WHEEL_R * Math.sin(kt)).toFixed(2) + squeeze + Math.max(0.3, Math.cos(kt)).toFixed(2) + ")";
        kn.style.opacity = Math.cos(kt).toFixed(2);
      }
      var bp = (t - popAt) / POP_MS;
      var bs = bp >= 0 && bp < 1 ? (bp < 0.6 ? lerp(0.7, 1.22, easeOutCubic(bp / 0.6)) : lerp(1.22, 1, easeOutCubic((bp - 0.6) / 0.4))) : 1;
      els.badge.style.transform = "scale(" + bs + ")";
    }

    // The display, in the pinned message's place (or the stretched island), behind the same seam.
    if (dv > 0.01) {
      if (side) setRect(els.display, panelLeft, Y0, panelW, PANEL);
      else if (bottom) setRect(els.display, B_DISPLAY_X, YB_EDGE - rowIn, Math.min(W - M, islandRight) - B_DISPLAY_X, rowIn);
      else setRect(els.display, DISPLAY_X, Y0, Math.min(W - M, islandRight) - DISPLAY_X, PANEL);
      // The words come in from the side the wheel turns to: up or down on Android, along the row on iOS.
      var dp = clamp((t - state.displayAt) / DISPLAY_MS, 0, 1);
      if (IOS) els.dl.style.transform = "translateX(" + (state.displayDir * 14 * (1 - easeOutCubic(dp))) + "px)";
      else els.dl.style.transform = "translateY(" + (state.displayDir * 9 * (1 - easeOutCubic(dp))) + "px)";
      els.dl.style.opacity = (smooth(0.5, 1, dv) * (0.35 + 0.65 * easeOutCubic(dp))).toFixed(3);
      els.display.style.opacity = smooth(0, 0.25, dv).toFixed(3);
    }

    // The lens: a puck of glass under the whole topic you are in, its icon in the puck's round end.
    // Rolling out, the wheel's round bead stays on your topic and opens into it; pick another topic
    // and it glides there, taking that topic's width, from wherever it is. Its leading edge goes
    // first and its trailing edge follows, each on a critically damped spring, so it leaves without
    // a jump, stretches a little on its way, a touch thinner, and gathers itself as it lands.
    var gt = (t - state.lensAt) / 1000, gliding = gt < LENS_END;
    var lead = gliding ? spring(LENS_LEAD * gt) : 1, trail = gliding ? spring(LENS_TRAIL * gt) : 1;
    state.glassMoving = false;
    var onLens = state.press && state.press.onLens && state.press.id === state.current;
    var lensDip = onLens ? 1 - DROP_SINK * 0.6 * state.pressSink : 1;
    var selTab = tabEl(state.current), selV = vtabEl(state.current);
    if (selTab) {
      var tx = selTab.offsetLeft + 1, tw = selTab.offsetWidth - 2;
      var fx = state.lensFromX == null ? tx : state.lensFromX, fw = state.lensFromW == null ? tw : state.lensFromW;
      var toRight = tx > fx;
      var lx = lerp(fx, tx, toRight ? trail : lead), lr = lerp(fx + fw, tx + tw, toRight ? lead : trail), lw = lr - lx;
      if (!side) glassProgress(gliding, Math.max(Math.abs(lx - tx), Math.abs(lr - tx - tw)), Math.max(Math.abs(fx - tx), Math.abs(fw - tw)));
      var lrest = lerp(fw, tw, (lead + trail) / 2), lnarrow = 1 - 0.1 * clamp((lw - lrest) / lrest, 0, 1);
      state.lensX = lx; state.lensW = lw;
      var lh = LENS_H * lnarrow;
      if (!side && rt < 1) {
        var open2 = smooth(0, 0.32, rt), lc = iconX(selTab);
        lx = lerp(lc - 16, lx, open2); lw = lerp(32, lw, open2); lh = lerp(32, lh, open2);
      }
      els.tlens.style.left = lx.toFixed(2) + "px";
      els.tlens.style.width = lw.toFixed(2) + "px";
      els.tlens.style.top = ((BAR - lh) / 2).toFixed(2) + "px";
      els.tlens.style.height = lh.toFixed(2) + "px";
      els.tlens.style.borderRadius = (lh / 2).toFixed(2) + "px";
      var ls = (side || !state.drumW ? 1 : drumAt(lx + lw / 2, state.drumW)) * lensDip;
      els.tlens.style.transform = ls < 0.999 ? "scale(" + ls.toFixed(3) + ")" : "";
      els.tlens.style.display = "";
    } else if (els.tlens) els.tlens.style.display = "none";
    if (selV) {
      var ty = selV.offsetTop + 2, th = selV.offsetHeight - 4;
      var fy = state.vlensFromY == null ? ty : state.vlensFromY, fh = state.vlensFromH == null ? th : state.vlensFromH;
      var goDown = ty > fy;
      var vy = lerp(fy, ty, goDown ? trail : lead), vb = lerp(fy + fh, ty + th, goDown ? lead : trail), vh2 = vb - vy;
      if (side) glassProgress(gliding, Math.max(Math.abs(vy - ty), Math.abs(vb - ty - th)), Math.max(Math.abs(fy - ty), Math.abs(fy + fh - ty - th)));
      var vrest = lerp(fh, th, (lead + trail) / 2), vnarrow = 1 - 0.1 * clamp((vh2 - vrest) / vrest, 0, 1);
      state.vlensY = vy; state.vlensH = vh2;
      els.vlens.style.top = vy.toFixed(2) + "px";
      els.vlens.style.height = vh2.toFixed(2) + "px";
      els.vlens.style.transform = vnarrow < 0.999 || lensDip < 0.999 ? "scale(" + (vnarrow * lensDip).toFixed(3) + "," + lensDip.toFixed(3) + ")" : "";
    }
    renderDrop(t, gliding, lead);
    // Names, and the All and General glyphs, turn from grey to ink as the puck arrives (320 ms).
    for (var s = 0; s < topics.length; s++) {
      var id = topics[s].id, ts = tabSel[id], sel = id === state.current ? 1 : 0;
      if (ts) { var qq = clamp((t - ts.start) / SELECT_MS, 0, 1); sel = lerp(ts.from, ts.to, easeOutQuint(qq)); }
      var h = tabEl(id), v = vtabEl(id), ink = "color-mix(in srgb, var(--ptext) " + Math.round(sel * 100) + "%, var(--pmuted))";
      if (h) { h.style.color = ink; h.firstChild.style.transform = sel > 0.001 ? "scale(" + (1 + 0.06 * sel).toFixed(3) + ")" : ""; }
      if (v) v.style.color = ink;
    }

    // Typing: the dots rise and fall one after another.
    var dots = root.querySelectorAll(".lt.typing b");
    for (var di2 = 0; di2 < dots.length; di2++) {
      var ph = ((t / 1000) * 1.6 - (di2 % 3) * 0.2) % 1;
      dots[di2].style.opacity = (0.35 + 0.65 * Math.pow(Math.sin(Math.PI * clamp(ph, 0, 1)), 2)).toFixed(2);
    }
    if (!IOS) renderTypingBadges(t);

    // Clicks while the wheel settles after a swipe.
    if (state.settling) {
      var r = clamp(Math.round(state.wheelPos), 0, topics.length - 1);
      if (r !== state.lastTick) tick(r);
      if (!tweens.wheelPos) state.settling = false;
    }
    if (once !== true) requestAnimationFrame(render);
  }

  // Meanwhile the avatars tuck into their bubbles as the rail's edge passes them, top to bottom as it
  // drops and bottom to top as it winds up, so none peeks out from under the rail: into the bubble's
  // tail on Android, away into the corner on iOS, the way each app tucks them in for side tabs.
  function tuckAvatars(side, rt) {
    var out = side ? state.keepT * smooth(0, 0.15, rt) : 0;
    if (out <= 0 && !state.avatarsTucked) return;
    state.avatarsTucked = out > 0;
    var avs = els.chatInner.querySelectorAll(".av");
    var edge = Y0 + lerp(V_ISLAND_H, INPUT_TOP - GAP - Y0, rt);
    var box = root.getBoundingClientRect(), k = box.width / W;
    for (var i = 0; i < avs.length; i++) {
      var av = avs[i], tuck = 0;
      if (out > 0) {
        var y = (av.parentNode.getBoundingClientRect().bottom - box.top) / k - av.offsetHeight / 2;
        tuck = rt >= 1 ? out : out * smooth(-24, 24, edge - y);
      }
      av.style.transformOrigin = IOS ? "0 100%" : "100% 100%";
      av.style.transform = tuck > 0 ? (IOS ? "" : "translateX(" + (24 * tuck).toFixed(2) + "px) ") + "scale(" + (1 - tuck).toFixed(3) + ")" : "";
      av.style.opacity = tuck > 0 ? (1 - tuck).toFixed(3) : "";
    }
  }

  // The drop of glass under a finger on a tab, as on Android: it blooms from where the finger is
  // into the lens's shape there, fainter than the lens, while the tab sinks into it a little; on the
  // topic you are in, the lens gives instead. Pick the topic and the lens glides over and takes the
  // drop's place; let go anywhere else and it melts away.
  function renderDrop(t, gliding, lead) {
    var pr = state.press, vert = !!pr && pr.name.indexOf("vtab:") === 0, shown = null;
    var vtabs = els.vtabs.querySelectorAll(".vtab");
    for (var i = 0; i < vtabs.length; i++) {
      var k = pressScale(vtabs[i]);
      vtabs[i].style.transform = k < 0.999 ? "scale(" + k.toFixed(4) + ")" : "";
    }
    if (pr && !pr.onLens) {
      var tab = vert ? vtabEl(pr.id) : tabEl(pr.id), dark = root.classList.contains("dark");
      var bloom = spring(DROP_BLOOM * (t - pr.down) / 1000), melt = 0;
      var alpha = (dark ? 0.8 : 0.7) * smooth(0, 0.5, bloom);
      if (pr.id === state.current) alpha *= gliding ? 1 - smooth(0.2, 0.85, lead) : 0;
      else if (pr.up != null) { melt = smooth(0, DROP_MELT, (t - pr.up) / 1000); alpha *= 1 - melt; }
      if (tab && alpha > 0.002) {
        var L, T, DW, DH, R;
        if (vert) { L = els.vlens.offsetLeft; DW = els.vlens.offsetWidth; T = tab.offsetTop + 2; DH = tab.offsetHeight - 4; R = IOS ? 16 : 14; }
        else { L = tab.offsetLeft + 1; DW = tab.offsetWidth - 2; T = (BAR - LENS_H) / 2; DH = LENS_H; R = LENS_H / 2; }
        // From a small round drop under the finger to the lens's shape, a little smaller as it melts.
        var r0 = Math.min(10, DW / 2, DH / 2);
        var cx = clamp(tab.offsetLeft + tab.offsetWidth * pr.fx, L + r0, L + DW - r0), cy = clamp(tab.offsetTop + tab.offsetHeight * pr.fy, T + r0, T + DH - r0);
        var x0 = lerp(cx - r0, L, bloom), y0 = lerp(cy - r0, T, bloom), x1 = lerp(cx + r0, L + DW, bloom), y1 = lerp(cy + r0, T + DH, bloom);
        var d = vert ? els.vdrop : els.tdrop;
        d.style.left = x0.toFixed(2) + "px"; d.style.top = y0.toFixed(2) + "px";
        d.style.width = (x1 - x0).toFixed(2) + "px"; d.style.height = (y1 - y0).toFixed(2) + "px";
        d.style.borderRadius = lerp(r0, R, bloom).toFixed(2) + "px";
        d.style.opacity = alpha.toFixed(3);
        var ds = (vert || !state.drumW ? 1 : drumAt((x0 + x1) / 2, state.drumW)) * (1 - 0.06 * melt);
        d.style.transform = ds < 0.999 ? "scale(" + ds.toFixed(3) + ")" : "";
        shown = d;
      }
    }
    if (els.tdrop) els.tdrop.style.display = shown === els.tdrop ? "block" : "none";
    if (els.vdrop) els.vdrop.style.display = shown === els.vdrop ? "block" : "none";
  }
  // Someone typing in a topic other than yours, on its tabs: a small badge in its counter's colour
  // at the icon's lower corner, growing in from its middle (260 ms), with three dots that rise and
  // fall one after another, as on the wheel. Yours says so in the header instead.
  function renderTypingBadges(t) {
    topics.forEach(function (tp) {
      var fx = state.typingFx[tp.id], on = isTyping(tp);
      if (!fx || fx.on !== on) fx = state.typingFx[tp.id] = { on: on, at: fx ? t : -1e9 };
      var q = easeOutQuint(clamp((t - fx.at) / 260, 0, 1)), a = on ? q : 1 - q;
      [tabEl(tp.id), vtabEl(tp.id)].forEach(function (tab) {
        var b = tab && tab.querySelector(".tb");
        if (!b) return;
        b.style.display = a > 0.002 ? "flex" : "none";
        if (a <= 0.002) return;
        b.classList.toggle("muted", !!tp.muted);
        b.style.opacity = a.toFixed(3);
        b.style.transform = "scale(" + lerp(0.4, 1, a).toFixed(3) + ")";
        var ds = b.children;
        for (var k = 0; k < ds.length; k++) {
          var ph = ((t / 1000) * 1.6 - k * 0.2) % 1, rise = Math.sin(Math.PI * clamp(ph, 0, 1));
          ds[k].style.opacity = (0.5 + 0.5 * rise * rise).toFixed(2);
          ds[k].style.transform = "translateY(" + (-0.1 * b.offsetHeight * rise).toFixed(2) + "px)";
        }
      });
    });
  }
  // How a tab is scaled as it sinks under a finger, about its middle.
  function pressScale(el) {
    var pr = state.press;
    return pr && el && el.getAttribute("data-el") === pr.name ? 1 - DROP_SINK * state.pressSink : 1;
  }

  // Whether the glass, or the wheel, is still on its way: more than 3 px (or 1.5% of the way, on a
  // long glide) from where it lands. The chat opens the topic only once it is not.
  function glassProgress(gliding, remaining, travel) {
    state.glassMoving = gliding && remaining > Math.max(3, travel * 0.015);
  }
  function glassMoving() {
    var wheel = state.dragging || (tweens.wheelPos && Math.abs(state.wheelPos - tweens.wheelPos.to) > 0.12);
    return !!state.glassMoving || !!wheel;
  }
  // The topic opens behind a picture of the chat: its messages, its header and its pinned message.
  function openTopic(t) {
    catchGhost();
    state.opening = false;
    state.openAt = t;
    buildChat();
    if (!IOS) state.headerAt = t;
    buildHeader(); bindPinned(true);
  }

  // The chat as it is now, caught as a picture that holds still while the topic opens, then leaves.
  function catchGhost() {
    if (els.ghost) els.ghost.remove();
    var g = els.chatInner.cloneNode(true);
    g.classList.add("ghost");
    g.style.pointerEvents = "none";
    els.chatInner.parentNode.insertBefore(g, els.chatInner);
    els.ghost = g;
    state.ghostLeaving = false;
    state.ghostTransform = els.chatInner.style.transform || "";
    state.ghostAlpha = parseFloat(els.chatInner.style.opacity || "1");
  }

  function setSelected(id) {
    var t = now();
    // The lens sets off from where it is.
    state.lensAt = t; state.lensFromX = state.lensX; state.lensFromW = state.lensW; state.vlensFromY = state.vlensY; state.vlensFromH = state.vlensH;
    topics.forEach(function (tp) {
      var target = tp.id === id ? 1 : 0, cur = tp.id === state.current ? 1 : 0;
      if (target !== cur) tabSel[tp.id] = { from: cur, to: target, start: t };
    });
  }

  // One click of the wheel. Leaving the pinned topics clicks a little firmer.
  function tick(index) {
    var prev = state.lastTick;
    state.lastTick = index;
    if (prev == null) return;
    var a = topics[prev], b = topics[index];
    // The soundtrack plays each detent as a note, so it gets the topic's place on the wheel too.
    emit("tick", (a && b && !!a.pinned !== !!b.pinned ? "edge " : "") + index);
  }

  function select(id, dir, from) {
    if (id === state.current) return;
    setSelected(id);
    state.current = id;
    var t = topicById(id);
    if (t.id !== 0) { t.unread = 0; t.mention = false; }
    // The chat leaves from wherever it is (it may be on its way in from the last switch); the topic,
    // its header (its title comes in; on iOS it stays the group's) and its pinned message come
    // when it opens.
    if (!state.opening) { state.leaveAlpha = state.chatAlpha; state.leaveShift = state.chatShift; state.slideUp = state.mode === "side"; state.leaveDir = dir || 0; }
    state.opening = true;
    // The glass sets off when the phone next draws it; until then it counts as moving.
    state.glassMoving = true;
    state.slideAt = now(); state.slideDir = dir || 0;
    refresh(false);
    emit("select", from || "");
  }

  // A bar out over the chat winds up a moment after a pick, but not while the topic is still opening,
  // as in the app (see rollUpAfterPick in TopicsTabsView): it goes once its messages are in.
  function windUpAfterPick() {
    var t = now();
    if (state.opening || t - state.openAt < LOAD_MS + 50) { pickTimer = setTimeout(windUpAfterPick, 16); return; }
    windUp("pick");
  }

  function windUp(why) {
    clearTimeout(pickTimer);
    if (!state.rollable || !state.out) return;
    state.out = false;
    state.wheelPos = idxOf(state.current); state.lastTick = state.wheelPos;
    tween("rollT", 0, ROLLUP_MS, easeInOutCubic);
    emit("rollUp", why || "tap");
  }

  function unroll() {
    if (!state.rollable || state.out) return;
    clearTimeout(pickTimer);
    state.out = true;
    // Its far end reads Keep open; the end is out of sight until the bar has nearly rolled out.
    delete tweens.keepT; state.keepT = 1;
    // The bar comes back scrolled to the topic you are in, its icon right where the wheel's lens is.
    var tab = tabEl(state.current);
    var maxScroll = Math.max(0, els.tabs.scrollWidth - els.tabsClip.offsetWidth + (els.keep.offsetWidth - KEY));
    state.hScroll = tab ? clamp(tab.offsetLeft, 0, maxScroll) : 0;
    delete tweens.hScroll;
    tween("rollT", 1, UNROLL_MS, easeOutQuart);
    emit("rollOut", "tap");
  }

  var api = {
    renderNow: function () { render(true); },
    // options.skin: "ios" for Telegram iOS on an iPhone; the Android phone otherwise.
    init: function (el, options) {
      useSkin(options && options.skin);
      api.W = W; api.H = H;
      var chrome = IOS ? CHROME.ios : CHROME.android;
      root = el;
      root.classList.add("screen");
      if (IOS) root.classList.add("ios");
      root.innerHTML =
        '<div class="pattern">' + PATTERN + '</div>' +
        '<div class="chat" data-el="chat"><div class="chat-inner"></div></div>' +
        '<div class="date-pill">Today</div>' +
        chrome.top +
        '<div class="pinned" data-el="pinned"><div class="pglass glass"></div><div class="pcontent"><span class="bar"></span><div class="ptexts"><div class="ptext pa"></div><div class="ptext pb"></div></div></div></div>' +
        '<div class="display"><span class="dbar"></span><div class="dl"><div class="dt"></div><div class="ds"></div></div></div>' +
        '<div class="shell glass h">' + chrome.separator + '<div class="hwell"><div class="es l"></div><div class="es r"></div></div><div class="hbar"><div class="toggle" data-el="toggle">' + ICON_TOGGLE + '</div><div class="tabs-clip"><div class="tabs"></div></div><div class="rollbtn" data-el="roll">' + ICON_ROLLUP + '</div><div class="keep" data-el="keep"><span>Keep open</span></div></div></div>' +
        '<div class="shell glass v"><div class="vwell"><div class="es t"></div><div class="es b"></div><div class="vgear"></div></div><div class="vrail"><div class="toggle" data-el="vtoggle">' + ICON_TOGGLE + '</div><div class="vtabs-clip"><div class="vtabs"></div></div><div class="rollbtn" data-el="vroll"><span style="display:grid;transform:rotate(90deg)">' + ICON_ROLLUP + '</span></div><div class="vkeep" data-el="vkeep"><i>' + ICON_KEEP_V + '</i>Keep open</div></div></div>' +
        '<div class="wheel" data-el="wheel"><div class="well"></div><div class="lens puck"></div><div class="belt"></div><div class="shade"></div><div class="badge"></div></div>' +
        chrome.bottom +
        '<div class="dip"></div>';
      els = {
        chat: q(".chat"), chatInner: q(".chat-inner"), date: q(".date-pill"), pinned: q(".pinned"),
        title: q(".titles .ttl"), subtitle: q(".titles .subtitle"), titles: q(".titles"), avGroup: q(".avatar .grp"), avTopic: q(".avatar .tpc"),
        hShell: q(".shell.h"), vShell: q(".shell.v"), hbar: q(".hbar"), vrail: q(".vrail"), tabs: q(".tabs"), vtabs: q(".vtabs"),
        hRoll: q('.hbar [data-el="roll"]'), keep: q('[data-el="keep"]'), vRoll: q('[data-el="vroll"]'), vKeep: q('[data-el="vkeep"]'),
        wheel: q(".wheel"), belt: q(".wheel .belt"), badge: q(".wheel .badge"), well: q(".wheel .well"), lens: q(".wheel .lens"), shade: q(".wheel .shade"),
        keyBack: q('[data-el="back-top"]'), keyMenu: q('[data-el="menu-top"]'), kwBack: q(".hkey.back .kwell"), kwMenu: q(".hkey.menu .kwell"),
        pglass: q(".pinned .pglass"), pcontent: q(".pinned .pcontent"), ptexts: q(".pinned .ptexts"), ptextA: q(".pinned .pa"), ptextB: q(".pinned .pb"),
        display: q(".display"), dl: q(".display .dl"), dTitle: q(".display .dt"), dSub: q(".display .ds"),
        inputbar: q(".inputbar"), inputHint: q(".inputbar .fill"), dip: q(".dip"),
        hwell: q(".hwell"), vwell: q(".vwell"), tabsClip: q(".tabs-clip"),
        esL: q(".hwell .es.l"), esR: q(".hwell .es.r"), esT: q(".vwell .es.t"), esB: q(".vwell .es.b"), vgear: q(".vwell .vgear"),
        hsep: q(".hsep")
      };
      api.reset({});
      requestAnimationFrame(render);
    },
    reset: function (o) {
      if (els.ghost) { els.ghost.remove(); els.ghost = null; }
      topics = JSON.parse(JSON.stringify(TOPICS));
      msgs = JSON.parse(JSON.stringify(MESSAGES));
      var rolled = !!o.rolled;
      state = { mode: o.mode || "top", lastRow: o.mode === "bottom" ? "bottom" : "top", rollable: rolled, out: false, rollT: rolled ? 0 : 1, reserveT: rolled ? 0 : 1,
        current: o.current == null ? 2 : o.current, hScroll: 0, pressT: 0, scrubT: 0, keepT: 0,
        panelVis: 1, pinnedText: null, pinnedPrev: null, pinnedAt: -1e9, displayIndex: null, displayAt: -1e9, displayDir: 0, dispW: 0,
        wheelPos: 0, lastTick: null, settling: false, slideAt: -1e9, slideDir: 0, slideUp: false, chatShown: null, headerAt: -1e9, nudgeAt: 0, nudgeDir: 0, belt: 0,
        dateT: 0, chatScroll: 0, chatDragging: false, chatFrom: 0, keyBackT: 0, keyMenuT: 0,
        opening: false, openAt: -1e9, leaveAlpha: 1, leaveShift: 0, leaveDir: 0, chatAlpha: 1, chatShift: 0, glassMoving: false, ghostLeaving: false, ghostAt: 0,
        press: null, pressSink: 0, typing: {}, typingFx: {}, lensAt: -1e9, lensFromX: null, lensFromW: null, lensX: null, lensW: null, vlensFromY: null, vlensFromH: null, vlensY: null, vlensH: null, tabsVisR: 0, drumW: 0 };
      state.wheelPos = idxOf(state.current); state.lastTick = state.wheelPos;
      tweens = {}; tabSel = {}; popAt = -1e9;
      clearTimeout(pickTimer); clearTimeout(scrubTimer); clearTimeout(dateTimer);
      root.classList.toggle("dark", !!o.dark);
      buildChat(); buildHeader(); buildTabs(); buildWheel();
      bindPinned(false);
      state.panelVis = PINNED[state.current] ? 1 : 0; delete tweens.panelVis;
    },
    on: function (f) { listeners.push(f); },
    state: function () { return state; },
    // « at the end of the classic bar: the topics wind up into the wheel.
    rollUp: function () {
      if (state.rollable) return;
      state.rollable = true; state.out = true;
      state.wheelPos = idxOf(state.current); state.lastTick = state.wheelPos;
      tween("reserveT", 0, RESERVE_MS);
      windUp("roll");
    },
    // "Keep open" on a bar that rolled out over the chat: it stays, the classic way.
    keepOpen: function () {
      if (!state.rollable) return;
      clearTimeout(pickTimer);
      state.rollable = false; state.out = false;
      tween("rollT", 1, 200);
      tween("reserveT", 1, RESERVE_MS);
      tween("keepT", 0, RESERVE_MS);
      emit("keep");
    },
    tapWheel: function () { unroll(); },
    pressWheel: function (down) { tween("pressT", down ? 1 : 0, down ? 80 : 350); },
    // A finger comes down on a tab or lifts off it (name: "tab:6" on the bar, "vtab:6" on the rail;
    // at: where on the tab, as fractions of its width and height).
    pressTab: function (name, down, at) {
      var t = now(), pr = state.press;
      if (down) {
        var id = Number(name.split(":")[1]);
        state.press = { name: name, id: id, down: t, up: null, onLens: id === state.current, sinkAtUp: 0, fx: at ? at[0] : 0.5, fy: at ? at[1] : 0.5 };
      } else if (pr && pr.name === name && pr.up == null) {
        pr.sinkAtUp = spring(DROP_BLOOM * (t - pr.down) / 1000);
        pr.up = t;
      }
    },
    pickTopic: function (id, from) {
      var dir = Math.sign(idxOf(id) - idxOf(state.current));
      select(id, dir, from || "tab");
      if (state.rollable && state.out) {
        clearTimeout(pickTimer);
        pickTimer = setTimeout(windUpAfterPick, PICK_DELAY);
      }
    },
    tapChat: function () { if (state.out) windUp("chat"); },
    back: function () { if (state.out) { windUp("back"); return true; } return false; },
    setMode: function (mode) {
      if (mode !== "side") state.lastRow = mode;
      state.mode = mode; emit("mode", mode);
    },
    // The toggle at the start of the bar, as in the app: a row goes to the side, and the side goes
    // back to a row at the other end of the chat (top, then bottom, then top again).
    toggleMode: function () { api.setMode(state.mode === "side" ? (state.lastRow === "bottom" ? "top" : "bottom") : "side"); },
    newMessage: function (id, mention, message) {
      var tp = topicById(id), m = message || NEWS[id];
      if (m && msgs[id]) msgs[id].push(m);
      // In the topic you are in, it just comes into the chat.
      if (id === state.current) { if (state.chatShown === id) buildChat(); return; }
      tp.unread += 1; if (mention) tp.mention = true;
      refresh(true);
      if (state.rollable && !state.out && !state.dragging && !tweens.wheelPos) {
        var d = Math.sign(idxOf(id) - state.wheelPos);
        if (d) { state.nudgeAt = now(); state.nudgeDir = d; }
      }
      emit("pop");
    },
    setDark: function (d) { root.classList.toggle("dark", d); },
    // Someone starts (name) or stops (null) typing in a topic; stopping can send the message.
    typing: function (id, name, message) {
      if (name) state.typing[id] = name; else delete state.typing[id];
      refreshTyping(); refreshWheelDots();
      emit("typing", name ? "start" : "stop");
      if (!name && message) api.newMessage(id, false, message);
    },
    look: function (cls) { cls.split(" ").forEach(function (c) { if (c) root.classList.add(c); }); },
    setDip: function (a) { els.dip.style.opacity = a; },
    scrollTabs: function (px, ms) { tween("hScroll", px, ms || 500); },
    scrollTabsBy: function (px, ms) {
      var maxScroll = Math.max(0, els.tabs.scrollWidth - els.tabsClip.offsetWidth + (els.keep.offsetWidth - KEY));
      tween("hScroll", clamp(state.hScroll + px, 0, maxScroll), ms || 500, easeInOutCubic);
    },
    // Scrolls the tabs so the given topic's tab is in view, as far as the row allows.
    scrollTabsTo: function (id, ms) {
      var tab = tabEl(id);
      var maxScroll = Math.max(0, els.tabs.scrollWidth - els.tabsClip.offsetWidth + (els.keep.offsetWidth - KEY));
      tween("hScroll", tab ? clamp(tab.offsetLeft - 4, 0, maxScroll) : 0, ms || 500, easeInOutCubic);
    },
    // Swiping the wheel: it follows the finger, clicks at every topic, and settles on the nearest
    // one when let go; a flick carries it a little further. The chat moves to that topic.
    dragStart: function () {
      if (!state.rollable || state.out) return;
      clearTimeout(scrubTimer);
      delete tweens.wheelPos;
      state.dragging = true; state.settling = false;
      state.dragFrom = state.wheelPos;
      state.lastTick = clamp(Math.round(state.wheelPos), 0, topics.length - 1);
      tween("scrubT", 1, 160, easeOutCubic);
    },
    dragTo: function (px) {
      if (!state.dragging) return;
      var p = state.dragFrom - px / CELL, n = topics.length - 1;
      // Past either end the wheel gives a little and pulls back.
      if (p < 0) p = -0.45 * (1 - Math.exp(p / 0.9)); else if (p > n) p = n + 0.45 * (1 - Math.exp(-(p - n) / 0.9));
      state.wheelPos = p;
      var r = clamp(Math.round(p), 0, n);
      if (r !== state.lastTick) tick(r);
    },
    dragEnd: function (velocity) {
      if (!state.dragging) return;
      state.dragging = false;
      var n = topics.length - 1, from = idxOf(state.current);
      var v = -(velocity || 0) / CELL;
      // A flick carries the wheel on by up to two more topics.
      var target = clamp(Math.round(state.wheelPos + clamp(v * 0.08, -2, 2)), 0, n);
      var moved = state.wheelPos - state.dragFrom;
      if (target === from && Math.abs(moved) > 0.28) target = clamp(from + Math.sign(moved), 0, n);
      state.settling = true;
      tween("wheelPos", target, SNAP_MS, easeOutBack);
      if (target !== from) select(topics[target].id, Math.sign(target - from), "wheel");
      scrubTimer = setTimeout(function () { tween("scrubT", 0, 260); }, 520);
    },
    // The chat under a finger: older messages come down from under the header, and the floating
    // date shows until a moment after the chat rests.
    chatDragStart: function () {
      delete tweens.chatScroll;
      clearTimeout(dateTimer);
      state.chatDragging = true; state.chatFrom = state.chatScroll;
      tween("dateT", 1, 160, easeOutCubic);
      emit("scroll");
    },
    chatDragTo: function (py) {
      if (!state.chatDragging) return;
      var s = state.chatFrom + py;
      // Past either end it gives a little.
      if (s < 0) s = -18 * (1 - Math.exp(s / 60));
      else if (s > maxChatScroll) s = maxChatScroll + 18 * (1 - Math.exp(-(s - maxChatScroll) / 60));
      state.chatScroll = s;
    },
    chatDragEnd: function (velocity) {
      if (!state.chatDragging) return;
      state.chatDragging = false;
      tween("chatScroll", clamp(state.chatScroll + (velocity || 0) * 0.28, 0, maxChatScroll), FLING_MS, easeOutCubic);
      clearTimeout(dateTimer);
      dateTimer = setTimeout(function () { tween("dateT", 0, 520); }, FLING_MS + DATE_HOLD_MS);
    },
    pressKey: function (name, down) { tween(name === "menu" ? "keyMenuT" : "keyBackT", down ? 1 : 0, down ? 80 : 300); },
    el: function (name) { return root.querySelector('[data-el="' + name + '"]'); },
    // Makes the phone respond to taps and drags, for the prototype page.
    interactive: function (scaleOf) {
      var scale = scaleOf || function () { return 1; };
      root.addEventListener("click", function (e) {
        var hit = e.target.closest("[data-el]");
        if (!hit) return;
        var name = hit.getAttribute("data-el");
        if (name.indexOf("tab:") === 0 || name.indexOf("vtab:") === 0) api.pickTopic(Number(name.split(":")[1]), "tab");
        else if (name === "roll" || name === "vroll") api.rollUp();
        else if (name === "keep" || name === "vkeep") api.keepOpen();
        else if (name === "toggle" || name === "vtoggle") api.toggleMode();
        else if (name === "back" || name === "back-top") { if (!api.back()) emit("leave"); }
        else if (name === "menu-top") emit("menu");
      });
      // A finger on a tab brings the drop of glass; lifting it, or leaving the tab, lets it go.
      root.addEventListener("pointerdown", function (e) {
        var hit = e.target.closest('[data-el^="tab:"], [data-el^="vtab:"]');
        if (!hit) return;
        var r = hit.getBoundingClientRect();
        api.pressTab(hit.getAttribute("data-el"), true, [(e.clientX - r.left) / r.width, (e.clientY - r.top) / r.height]);
      });
      ["pointerup", "pointercancel", "pointerleave"].forEach(function (type) {
        root.addEventListener(type, function () { if (state.press) api.pressTab(state.press.name, false); });
      });
      // A touch anywhere else rolls a bar that is out over the chat back up.
      root.addEventListener("pointerdown", function (e) {
        if (!state.out || e.target.closest(".shell") || e.target.closest(".wheel")) return;
        if (e.target.closest('[data-el="back"]') || e.target.closest('[data-el="back-top"]')) return;
        api.tapChat();
      }, true);
      // The header's keys press into their wells.
      [["back", els.keyBack], ["menu", els.keyMenu]].forEach(function (k) {
        k[1].addEventListener("pointerdown", function () { api.pressKey(k[0], true); });
        ["pointerup", "pointercancel", "pointerleave"].forEach(function (type) {
          k[1].addEventListener(type, function () { api.pressKey(k[0], false); });
        });
      });
      // The chat scrolls under a finger.
      els.chat.style.touchAction = "none";
      var sc = null;
      els.chat.addEventListener("pointerdown", function (e) {
        sc = { y: e.clientY, drag: false, samples: [] };
        els.chat.setPointerCapture(e.pointerId);
      });
      els.chat.addEventListener("pointermove", function (e) {
        if (!sc) return;
        sc.samples.push({ v: e.clientY, t: performance.now() }); if (sc.samples.length > 6) sc.samples.shift();
        if (!sc.drag && Math.abs((e.clientY - sc.y) / scale()) > 6) { sc.drag = true; sc.y = e.clientY; api.chatDragStart(); }
        if (sc.drag) api.chatDragTo((e.clientY - sc.y) / scale());
      });
      function chatEnd() {
        if (!sc) return;
        if (sc.drag) {
          var s = sc.samples, vel = 0;
          if (s.length > 1) vel = (s[s.length - 1].v - s[0].v) / Math.max(1, s[s.length - 1].t - s[0].t) * 1000 / scale();
          api.chatDragEnd(vel);
        }
        sc = null;
      }
      els.chat.addEventListener("pointerup", chatEnd);
      els.chat.addEventListener("pointercancel", chatEnd);
      els.wheel.style.pointerEvents = "auto";
      var down = null;
      els.wheel.addEventListener("pointerdown", function (e) {
        if (!state.rollable || state.out || state.rollT > 0.05) return;
        down = { from: state.mode === "side" ? e.clientY : e.clientX, drag: false, samples: [] };
        els.wheel.setPointerCapture(e.pointerId);
        api.pressWheel(true);
      });
      // Standing up on the side, the wheel turns up and down.
      els.wheel.addEventListener("pointermove", function (e) {
        if (!down) return;
        var v = state.mode === "side", at = v ? e.clientY : e.clientX;
        down.samples.push({ v: at, t: performance.now() }); if (down.samples.length > 6) down.samples.shift();
        if (!down.drag && Math.abs((at - down.from) / scale()) > 6) { down.drag = true; api.pressWheel(false); api.dragStart(); down.from = at; }
        if (down.drag) api.dragTo((at - down.from) / scale());
      });
      function end(cancel) {
        if (!down) return;
        api.pressWheel(false);
        if (down.drag) {
          var s = down.samples, vel = 0;
          if (s.length > 1) vel = (s[s.length - 1].v - s[0].v) / Math.max(1, s[s.length - 1].t - s[0].t) * 1000 / scale();
          api.dragEnd(vel);
        } else if (!cancel) api.tapWheel();
        down = null;
      }
      els.wheel.addEventListener("pointerup", function () { end(false); });
      els.wheel.addEventListener("pointercancel", function () { end(true); });
    }
  };
  window.Phone = api;
})();
