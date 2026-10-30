// Renders stage.html frame by frame at 60 fps on Playwright's fake clock, so every animation lands
// exactly on its frame no matter how slow the machine is.
//
//   node render.js --out frames [--workers 3] [--dsf 1.5]   all frames as JPEG + events.json
//   node render.js --out frames --from 63                  only the frames from 63 s on (+ events.json)
//   node render.js --stills 5,12.5,20 --out stills          a few PNG stills to check the look
//   node render.js --events --out sound                     only events.json, to remake the soundtrack
//   --skin ios with any of them                             Telegram iOS on an iPhone (stage.html?skin=ios)
// Needs Playwright (npm i -g playwright, then NODE_PATH=$(npm root -g)).
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");

const here = __dirname;
const args = Object.fromEntries(process.argv.slice(2).reduce((acc, a, i, all) => {
  if (a.startsWith("--")) acc.push([a.slice(2), all[i + 1] && !all[i + 1].startsWith("--") ? all[i + 1] : true]);
  return acc;
}, []));
const FPS = 60;
const OUT = path.resolve(args.out || "frames");
const DSF = Number(args.dsf || 1.5);
const WORKERS = Number(args.workers || 3);
const PAGE = "file://" + path.join(here, "stage.html") + (args.skin ? "?skin=" + args.skin : "");
fs.mkdirSync(OUT, { recursive: true });

async function openStage(browser) {
  const page = await browser.newPage({ viewport: { width: 1080, height: 1080 }, deviceScaleFactor: DSF });
  page.on("pageerror", (e) => console.error("page error:", e.message));
  await page.clock.install({ time: 0 });
  await page.goto(PAGE);
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(300);
  await page.clock.pauseAt(100000);
  return page;
}

// Steps the stage from frame 0 to `end`, saving frames that `keep` asks for (true: paint, no file).
async function run(page, end, keep) {
  let clockMs = 0;
  for (let f = 0; f < end; f++) {
    const t = f / FPS;
    await page.evaluate((t) => window.stage.prepare(t), t);
    const target = Math.round((f + 1) * 1000 / FPS);
    await page.clock.runFor(target - clockMs);
    clockMs = target;
    const name = keep(f);
    if (name === true) {
      await page.evaluate((t) => window.stage.paint(t), t);
    } else if (name) {
      await page.evaluate((t) => window.stage.paint(t), t);
      await page.screenshot({ path: path.join(OUT, name), type: name.endsWith(".png") ? "png" : "jpeg", quality: name.endsWith(".png") ? undefined : 93, clip: { x: 0, y: 0, width: 1080, height: 1080 } });
    }
  }
}

(async () => {
const browser = await chromium.launch({ args: ["--disable-lcd-text", "--font-render-hinting=none"] });
const first = await openStage(browser);
const duration = await first.evaluate(() => window.stage.duration);
const total = Math.round(duration * FPS);

if (args.events) {
  // The whole story, painted but not saved: the same events as a full render, in a minute.
  await run(first, total, () => true);
  const events = await first.evaluate(() => window.stage.events);
  fs.writeFileSync(path.join(OUT, "events.json"), JSON.stringify({ duration, fps: FPS, events }, null, 1));
  console.log(`recorded ${events.length} events over ${duration} s`);
} else if (args.stills) {
  const want = new Map(String(args.stills).split(",").map((s) => [Math.round(Number(s) * FPS), `still_${s}.png`]));
  // Every frame is painted, saved or not: a topic switch moves on as the phone draws.
  await run(first, Math.max(...want.keys()) + 1, (f) => want.get(f) || true);
} else {
  // Every frame from --from on (the story is the same every time, so earlier frames still match).
  const start = Math.round(Number(args.from || 0) * FPS);
  const per = Math.ceil((total - start) / WORKERS);
  const started = Date.now();
  let done = 0;
  const pages = [first];
  for (let w = 1; w < WORKERS; w++) pages.push(await openStage(browser));
  await Promise.all(pages.map((page, w) => {
    const from = start + w * per, to = Math.min(total, from + per);
    return run(page, to, (f) => {
      // Frames before this worker's share are painted but not saved: a topic opens once the glass
      // has all but landed, which the phone sees as it draws, so it draws them all, as in one run.
      if (f < from) return true;
      if (++done % 300 === 0) console.log(`${done}/${total} frames, ${((Date.now() - started) / 1000).toFixed(0)} s`);
      return `f_${String(f).padStart(5, "0")}.jpg`;
    });
  }));
  const events = await pages[pages.length - 1].evaluate(() => window.stage.events);
  fs.writeFileSync(path.join(OUT, "events.json"), JSON.stringify({ duration, fps: FPS, events }, null, 1));
  console.log(`rendered ${total - start} frames in ${((Date.now() - started) / 1000).toFixed(0)} s`);
}
await browser.close();
})();
