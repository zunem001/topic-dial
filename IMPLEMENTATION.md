# Topic Dial: how it is built

Topic Dial is client-only: no API or server changes. The same design is written twice, in each app's own
stack: for Telegram Android in Java (`topic-dial-android.patch`) and for Telegram iOS in Swift
(`topic-dial-ios.patch`). `android/` and `ios/` hold the files the patches change, whole, at their places in
the apps' sources.

## Android

Written against Telegram Android 12.10.6 (build 7112), [DrKLO/Telegram](https://github.com/DrKLO/Telegram) commit
`f2908b14133bbffbf7ab04f641ecb5bfaf533242`.

In a forum group shown as tabs, the topic bar can roll up into a little wheel, and roll back out across its row
when you need every topic, at the top, on the side or at the bottom. Rolled up at the top, the wheel and the
pinned message are one glass island, like a radio: the wheel is the dial and the pinned message is its display.
Swipe the wheel to go to the next or previous topic; while it turns, the display shows the topic under the lens
and its latest message.

### What it does

- **Three states.**
  - *Tabs*: the bar keeps the size and the place it has in stock Telegram: a 36 dp row at the top or
    above the input, or a 64 dp rail on the side. Its surface is the wheel's track, 36 dp tall like
    the wheel: the toggle sits in its left end and a `«` in its right end winds it up.
  - *Rolled up*: at the top, the bar becomes a 108 × 36 dp wheel turning in a well, wound up inside a
    48 dp glass island in the panel row under the header, 6 dp from the island's edges so their
    rounded ends are concentric.
    The chat gets the bar's row back. The wheel shows the topic you are in under a bead of glass, with
    the next topics curving away on either side of a drum and knurled ticks along its lower edge, and
    a badge on the well's rim counts unread messages in the other topics (@ for mentions, grey when
    only muted topics have news). Side tabs and a bottom bar roll up too, in their own places (below).
  - *Rolled out*: tap the wheel and it rolls along like a carpet, rising out of its well onto the
    bar's line as the island's rim gives way, the track stretching behind it with the tabs laid down
    on it, scrolled to the topic you are in. Its bead stays on your topic and opens into the bar's
    puck around it, and the wheel fades at the bar's far end. The pinned message steps down out of
    the way and takes its own glass back while the messages stay put. Picking a topic winds it back
    up 0.34 s later (or once the topic has opened, if that takes longer), and so does touching
    anything else, scrolling the chat or pressing Back. *Keep
    open* at the end of the rolled-out bar keeps the tabs out, the classic way.
- **One island.** With a single 48 dp top panel (the pinned message, usually), the island spans the
  row: the panel gives up its own glass and sits in the island after the wheel, its line 10 dp after
  the well, as the island's display. With no panel, the island holds just the wheel and floats over
  the chat; with several panels, it keeps to itself and the panels step aside for it. When a topic's
  pinned message comes or goes, the island widens or narrows with it.
- **One instrument.** Controls sit in wells, readouts sit on the glass. Back and the menu in the
  header sit in wells 6 dp inside their 46 dp keys, the way the wheel and the tabs sit in theirs; the
  title and the pinned message stay on the glass. The keys stay plain while you select messages or
  search.
- **The wheel's features, at the bar's size.** Rolled out or kept open, the bar has what the wheel
  has:
  - *The track*, its ends shaded where the drum curves away, with the toggle and `«` sitting in them.
    Topics nearing either end shrink a little as they turn away, like the icons on the wheel.
  - *The scale* under the tabs: a pin where the pinned topics end, where the wheel clicks firmer
    (stock's grey band behind pinned topics is gone).
  - *The counters*: for news the tabs keep Telegram's own unread counters, the number in its pill
    (grey when muted, @ for a mention, a heart for reactions), which pops when it grows. The scale's
    lights are the wheel's alone, where there is no room for numbers.
  - *The gear line*, the wheel's knurled ticks, a tick every 17 dp along the scale, shows only while
    the bar winds up or rolls out: none while it is out or kept open, then, winding up, subtly at
    first until it is the wheel's own pale ticks; rolling out runs in reverse.
  - *The icons*: every topic wears the wheel's icon, 20 dp in a 28 dp slot: its emoji, drawn the way
    the wheel and the topic's own header draw it, General's #, a plain topic's coloured bubble with its
    initial, All's chat bubbles (stock tabs show no icon for plain topics or All). Then come its name
    and its counter, with @ for mentions and a heart for reactions, as on the side rail.
  - *The glass*: the topic you are in sits on a 30 dp puck of white glass, a little brighter at the
    top, with a soft shadow under it and a fine edge (frosted grey in a dark theme). It is the
    wheel's bead opened out around the whole topic, icon, name and counter, with the icon in its round
    end. The topic's name and icon turn from grey to the text colour, not to the accent, so the glass
    alone marks it. Pick another topic and the puck glides there, taking that topic's width, from
    wherever it is: its leading end sets off first and its trailing end follows, each on a critically
    damped spring (nine tenths of the way in 0.115 s and 0.145 s), so it leaves without a jump,
    stretches a little on its way, a touch thinner as if it kept its volume, and gathers itself as it
    lands. Nothing heavy runs while it moves (see *Switching* below), and it keeps its own clock, a
    frame at a time, so a frame held up anyway never makes it leap ahead.
  - *A drop of glass under your finger*: instead of the list's square highlight, pressing a tab
    blooms a drop of the same glass from where you touch into the puck's shape there, fainter than the
    puck, while the tab sinks into it a little (7%); on the topic you are in, the puck itself gives.
    Lift your finger and the topic opens at once; the puck glides over and takes the drop's place.
    Let go anywhere else, or scroll, and the drop melts away.
  - *Keep open* is a button in the accent colour with a soft shadow of its own colour, so it reads as
    something to press and never as the topic you are in; the tabs fade out just before it.

  The side rail has the same: its track, a puck around the topic's icon and name, and Telegram's own
  counters at the icons' corners. Its gear line is the track's, so it stays while the rail's tabs fade as it
  winds up, and it hands over to the upright wheel's own ticks as the wheel appears. Rolled out over
  the chat from the wheel, the rail hides nothing: the conversation steps aside for it, back and to
  the right about its bottom right corner, just far enough that its bubbles clear the rail, in one
  motion with the rail, and comes back as it winds up. Meanwhile each avatar tucks into its bubble as
  the rail's lower edge passes it, top to bottom as the rail drops and bottom to top as it winds up,
  with the motion Telegram uses when side tabs open, so none peeks out at the rail's edge. The newest
  message stays above the input and outgoing messages stay whole; Keep open moves the chat into the
  rail's own column.
- **Who's typing.** When someone is typing in a topic, its light on the wheel's scale turns into three
  dots that rise and fall. On the bar and the rail, its tab gets a small badge in the colour of its
  counter at the icon's lower corner, with the same three dots, clear of the counter; the icon gives
  way around it as it does around the counter, and when the message lands the badge goes and the
  counter goes up. As the wheel passes that topic, the display reads "Ines is typing...", from the
  same string the topic list uses. In the topic itself, the header says it, as it always has, and its
  tab shows no badge.
- **The display.** While the wheel turns, the pinned message steps aside and the display tunes to the
  topic under the lens: its name (with a pin for pinned topics), its unread counter or @, and its
  latest message as "Sender: text", on the pinned message's own two lines and behind the same line.
  For All it lists the topics with news. Each topic slides in from the side the wheel turns to. With
  nothing pinned, the island stretches out to show it. The pinned message's words are gone before the
  display's come in, over glass and a line that stay, so words never show over other words. The
  display lets go 0.5 s after the wheel settles and the topic's own pinned message comes back.
- **Swipe to switch.** The wheel follows your finger at 34 dp per topic, clicks at every topic it
  passes (a crisp haptic tick, a firmer one where the pinned topics end) and opens the one it settles
  on; a flick carries it up to two topics further. A long press gives the usual topic options.
- **Switching, in one piece, with nothing ever stopping mid-move.** Opening a topic (its messages
  loading, the list laying them out) holds the app's frames up for a moment, 50 to 100 ms on a phone;
  anything moving then stops dead and jumps on, and the puck, at full speed right after a tap, did.
  So a switch runs in three steps, and the heavy part only ever runs while nothing on screen moves:
  1. At once, the puck (or the wheel) goes to the topic, and the chat lets go: it dims a little (to
     85%) and starts to slide away from the side the new topic is on. Nothing else happens yet.
  2. Once the puck or the wheel has all but landed (within 3 dp, or 1.5% of a long glide, where it
     covers 3 or 4 px a frame and a frame held up does not show), the chat is caught in a picture,
     which holds still while the topic opens behind it and the list takes its messages: whatever
     frames that takes, nothing on screen is moving to show them.
  3. Then the picture slides on (12 dp more), recedes a little (to 97%) and fades (0.2 s), while the
     new messages glide in together (0.38 s) from the side the new topic is on, across for a row of
     tabs and up or down for side tabs, crossing it.
  Every part keeps its own clock, a frame at a time and never more than a frame and a half, so a
  frame held up anyway never makes anything leap. The chat is never blank, the old messages never fade
  with the new ones in them, and the new ones never pop in one by one; picking more topics on the way
  opens only the last one, picking the topic on screen again before the other one opens brings the chat
  straight back, and a topic that takes long to load, or fails to, comes in anyway after 1.2 s. A bar
  rolled out over the chat winds back up once the topic has opened, not while it is opening, and the
  wheel settles on the same kind of clock.
- **Pinned topics** come first on the wheel, in the bar's own order (All, the pinned topics, then the
  rest).
- **News, like stations on a dial.** A topic with news lights up on the wheel's scale, a small light
  under its icon that turns with the drum (wider for a mention, grey when muted). News further along
  than the wheel shows makes that end of the well glow, so it is clear which way to turn. The badge
  rides the rim, clear of every icon. When a message arrives in another topic, the badge pops and the
  wheel gives a small nudge toward that topic.
- **Side tabs** roll up into the wheel standing up: a 48 × 120 dp island at the top of the rail, the
  wheel on its side in it, turning under a swipe up or down, with its scale down its right edge. The
  chat gets the rail's column back, and the pinned message sits beside the island and becomes its
  display while it turns (with nothing pinned, the display brings its own glass). Tap it and the rail
  drops down out of it like a blind; pick a topic and it rolls back up into it.
- **A bottom bar** stays at the bottom, by your thumb. Rolled up, the wheel sits at the start of the
  bar's own row, and while it turns the display shares that row on two smaller lines. Tapped, the bar
  rolls out along the bottom. The toggle moves the tabs as in stock Telegram: from the top to the side,
  from the side to the bottom, and back.
- **Motion.** Rolling out takes 0.54 s with a soft landing; winding up takes 0.48 s, gathering speed
  and settling in. The wheel turns to show the tab it is passing while it rolls. A press pushes the
  wheel into its well.
- **Remembered.** Each group keeps its own choice, and your latest choice becomes the default for
  forum groups where you have not chosen yet. A short hint explains the wheel the first two times.
- **Accessibility.** The wheel is a button that also scrolls: TalkBack can open the bar or move to the
  next or previous topic.

### The code

| File | What changes |
| --- | --- |
| `ui/Components/TopicsRollWheel.java` | New. The wheel: drum, glass bead, news and typing lights, haptic detents, badge, accessibility; standing up for side tabs; the track and glass puck the bar shares |
| `ui/Components/TopicsTabsView.java` | The island with its well and display; the bar and the rail on the wheel's track, with their scale, glass puck and the drop of glass under your finger; rolling out and winding up at the top, on the side and at the bottom; Keep open and the saved choice |
| `ui/ChatActivity.java` | The pinned message joins the island and steps aside while the wheel turns; the chat steps aside for the rolled-out rail and its avatars tuck in; a picked topic opens in one piece, once the glass has landed |
| `ui/Components/ChatActivityTopPanelLayout.java` | The panel row gives up its own glass while it shares the island |
| `ui/ActionBar/ActionBar.java` | A small hook that lets the chat draw the wells into Back and the menu |
| `res/values/strings.xml` | 8 new strings |

### What was checked

- The patch applies cleanly to `f2908b1`: the five files it changes there are byte for byte the ones it was
  written against. It contains no keys; to build, use your own `api_id` and `api_hash`.
- It compiles there unchanged, and the app built from it has been installed and used on a phone.
- Telegram has since moved on to 13.1. On 13.1.1 (`3de37c8`) every hunk still applies but one in `ActionBar.java`,
  where 13.1 added a `setDrawGlassTitle` setter right where the hook goes; that hunk has to be placed by hand.

## iOS

Written against [Telegram-iOS](https://github.com/TelegramMessenger/Telegram-iOS) commit
`1dd3e7f4ed5d72caed1caa04033832c28a5920f5` (app 13.1, 20 October 2026), and fits the iOS 26 glass design that
version uses. It contains no keys.

### What it does on iOS

iOS already puts the topic tabs and the pinned message in one floating glass card under the header. Topic Dial
makes that card the radio: the wheel is the dial and the pinned message its display.

- **Top.** Rolled up, the tabs row goes and the wheel (108 × 36 pt) turns in a well at the start of the pinned
  message's row, 7 pt from the card's edges so their rounded ends are concentric; the pinned message's line moves
  10 pt after the well. With nothing pinned, the card is just the wheel, a 120 × 48 pt capsule. Tap the wheel and it
  rolls along the row like a carpet, the tabs laid down behind it, while the pinned message steps down into its
  own row and the messages stay put. Picking a topic winds it back up 0.34 s later, and so does scrolling the chat.
  `«` at the bar's far end winds it up; while the bar is only out for a moment, the far end is *Keep open* instead.
- **The display.** While the wheel turns, the pinned message's words step aside and the row shows the topic under
  the lens: its name, its unread count or @, and its latest message as "Sender: text" (the chat list's own
  preview), or who is typing there ("Ines is typing", in the accent colour). For All it lists the topics with news. Each topic slides in from the side the wheel turns to, after the
  old words have gone; the display lets go 0.5 s after the wheel settles. With nothing pinned, the island stretches
  out along the row to show it.
- **Side tabs** roll up into a 48 × 120 pt island at the top of the rail, the wheel standing up in it and turning
  under a vertical swipe. The chat gets the rail's column back but 24 pt: the island is wider than the avatars, so
  they end under it and the bubbles clear it. Tap it and the rail drops out of it like a blind;
  while it is out over the chat, the conversation steps aside for it (it scales about its bottom right corner just
  far enough that the bubbles clear the rail, in one motion with it), and the avatars tuck into their bubbles as the
  rail's edge passes them, top to bottom as it drops and bottom to top as it winds up, with the motion iOS uses to
  hide them for an open side panel, so none peeks out at the rail's edge. Keep open moves the chat into the rail's
  column. While the wheel turns, the display brings its own glass beside the island.
- **Bottom.** The wheel sits in a 120 × 48 pt island at the start of the bottom row, above the input. It rolls out
  along the row, and while it turns, the island stretches along the row with the display on two smaller lines.
- **The wheel**: topic icons on a drum that curve away at its ends, a bead of glass over your topic, knurled ticks
  that turn with it, a light on the scale for every topic with news (wider for a mention, grey when muted, three
  dots that rise and fall where someone is typing, from the same feed as the chat list's typing), ends
  that glow when there is news further along, a badge for unread messages in the other topics that pops when it
  grows, and a small nudge toward a topic that just got a message. It clicks at every topic (a firmer tick where
  the pinned topics end), a flick carries it up to two topics further, and it gives at its ends.
- **The bar wears the wheel's features**: its tabs lie on the wheel's track (the wheel's well opened out along the
  bar, or down the rail, its ends shaded where the drum curves away, the toggle and `«` in them), the topic you are
  in sits on the wheel's white glass puck instead of the accent underline (its name and icon turn to the text
  colour), and the gear line, the wheel's ticks every 17 pt along the track's lower edge, shows only while the bar
  winds up or rolls out.
- **Switching topics, in one piece**: the chat on screen goes in 0.22 s, fading and sliding 20 pt away from the side
  the new topic is on, while the new topic, already loaded, glides in together from its side in 0.38 s after a
  0.06 s head start for the old one, so the two cross; instead of iOS's full-width page slide.
- **Remembered**: each group keeps its choice (`PersistentPeerData.topicListRolledUp`), and your latest choice is
  the default for groups where you have not chosen.
- **VoiceOver**: the wheel is adjustable (swipe up or down to change topic) and double-tapping it rolls the bar out.

### The code

| File | What changes |
| --- | --- |
| `ChatSideTopicsPanel/Sources/ChatTopicsRollWheel.swift` | New. The wheel, its items (built from the topic list and who is typing), and the drawing it shares with the bar: well, track, glass puck, drum ends, lights and typing dots, gear line |
| `ChatSideTopicsPanel/Sources/ChatTopicsRollDisplay.swift` | New. The display, and the `«` / Keep open control |
| `ChatSideTopicsPanel/Sources/ChatFloatingTopicsPanel.swift` | The side rail and the top and bottom bars roll up into the wheel and back, on the wheel's track |
| `ChatSideTopicsPanel/Sources/ChatSideTopicsPanel.swift` | The glass puck, the roll control at the bar's end, the topic list and who is typing in each topic, shared with the wheel |
| `HeaderPanelContainerComponent.swift` | A panel can share the next panel's row and cover its content; a card of the wheel alone takes only the glass it needs |
| `LegacyChatHeaderPanelComponent.swift` | A left inset for the pinned message, so it leaves room for the wheel |
| `ChatControllerNode.swift` | The roll state, the island sharing the pinned row, the side column, the chat stepping aside and its avatars tucking in, the topic switch |
| `ChatMessageDateHeader.swift`, `ChatMessageItem.swift`, `ChatControllerInteraction.swift` | An avatar can tuck into its bubble, on top of being hidden; one that comes along while the rail is out arrives tucked |
| `ChatInterfaceTitlePanelNodes.swift`, `ChatControllerLoadDisplayNode.swift` | The roll passed to the panels; scrolling winds the bar up |
| `ChatPresentationInterfaceState.swift` | `topicListRolledUp` in the group's persistent data |
| `en.lproj/Localizable.strings` | Four strings: Keep open, Roll up topics, the wheel's VoiceOver hint, No new messages |

### What was checked, and what was not

This was written on Linux, where Xcode cannot run, so the patch has **not** been built with Xcode or run on a
device. Instead:

- every Swift file the patch touches parses with Swift 6.1.2;
- the new and rewritten files (the wheel, the display, the rolling panels and the header container) type-check
  with warnings as errors, as Telegram's build treats them, against stand-ins for UIKit and the Telegram modules
  they use, with the signatures copied from the real sources: `TELEGRAM_IOS=/path/to/Telegram-iOS ./tools/ios-typecheck/run.sh`;
- the edits to `ChatControllerNode`, `ChatSideTopicsPanel`, the avatar header and the panel builders were checked by
  reading them against the APIs they call;
- the patch applies cleanly to a fresh checkout of `1dd3e7f`.

A first build may still need small fixes; the type-check covers most of the code, not all of it.

### Where it differs from Android

- A long press on the wheel rolls the bar out, where every tab carries its topic's options, rather than opening the
  options from the wheel itself.
- As on Android, the tabs on the bar and the rail keep their own unread counters, and the scale's lights (and their
  typing dots) are the wheel's alone; but the tabs keep their size toward the bar's ends, and a topic where someone
  is typing gets no badge on its tab. There is no first-time hint.
- The header's back and avatar keep iOS's own glass buttons.

## The demos

`demo-android.mp4` and `demo-ios.mp4` are rebuilds of the screen, not screen recordings: `tools/demo/` is a web
version of the phone that follows the same rules and numbers as the code (sizes, springs, timings), and renders
the story frame by frame. See `tools/demo/README.md` to render them again.
