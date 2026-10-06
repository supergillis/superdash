#!/usr/bin/env node
// Real touch gestures on a device: sidebar edge swipe, feed overlay, screensaver.
// Needs at least one configured feed; uses the first one.
import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { parseArgs } from "node:util";
import { CheckFailed, type Check, holds, runChecks, sleep, waitFor } from "./checks.ts";
import { Device, REPO_ROOT } from "./device.ts";

// Mirrors detectEdgeSwipe in EdgeSwipeDetector.kt.
const EDGE_ZONE_DP = 32;
const MIN_TRAVEL_DP = 80;
const MAX_DRIFT_DP = 120;
const SLIDESHOW_MODES = new Set(["immich", "picsum", "media_library"]);

const { values } = parseArgs({ options: { device: { type: "string", short: "d" } } });
const device = Device.pick(values.device);

/** The sidebar scrim's label in every app locale, so the check does not depend on the device language. */
function localizedStrings(name: string): Set<string> {
  const resources = join(REPO_ROOT, "packages/app/src/main/res");
  const labels = new Set<string>();
  for (const folder of readdirSync(resources).filter((entry) => entry.startsWith("values"))) {
    try {
      const xml = readFileSync(join(resources, folder, "strings.xml"), "utf8");
      const match = new RegExp(`<string name="${name}">([^<]*)</string>`).exec(xml);
      if (match?.[1]) {
        labels.add(match[1]);
      }
    } catch {
      // Not every values folder has strings.
    }
  }
  return labels;
}

const closeSidebarLabels = localizedStrings("sidebar_dismiss_content_description");
const density = Number(/(\d+)\s*$/.exec(device.adb(["shell", "wm", "density"]).toString().trim())?.[1]) / 160;
const dp = (value: number) => value * density;
const content = device.ui().find((node) => node.id === "android:id/content");
if (!content || !density) {
  throw new Error("could not read screen size or density");
}
const width = content.x * 2;
const height = content.y * 2;
const midY = height * 0.3;

const sidebarScrim = () => device.ui().find((node) => closeSidebarLabels.has(node.desc));
const sidebarOpen = () => sidebarScrim() !== undefined;
const state = () => device.feedState();

async function expectSidebar(open: boolean, timeoutMs = 2_000) {
  if (open) {
    await waitFor("sidebar open", sidebarOpen, (value) => value, timeoutMs);
  } else {
    await holds("sidebar closed", sidebarOpen, (value) => !value, timeoutMs);
  }
}

function edgeSwipe(options: { startDp?: number; travelDp?: number; driftDp?: number } = {}) {
  const startX = dp(options.startDp ?? 2);
  device.swipe(startX, midY, startX + dp(options.travelDp ?? 300), midY + dp(options.driftDp ?? 0));
}

function navigations(): string[] {
  return [...device.logs().matchAll(/SlideshowLoop: navigate direction=(\w+)/g)].map((match) => match[1] as string);
}

async function startScreensaver() {
  device.clearLogs();
  device.send("idle");
  await waitFor("idle", () => state().idle, (idle) => idle);
  // Let the first photo load so a swipe has something to leave.
  await sleep(2_000);
}

const settings = JSON.parse(device.send("dump")) as Record<string, unknown>;
const screensaverMode = settings[settings.night_mode_active === true ? "night_screensaver_mode" : "day_screensaver_mode"];
const feed = state().feeds[0];

const checks: Check[] = [
  [
    "an edge swipe opens the sidebar",
    async () => {
      edgeSwipe();
      await expectSidebar(true);
    },
  ],
  [
    "a swipe starting outside the edge zone does not",
    async () => {
      edgeSwipe({ startDp: EDGE_ZONE_DP * 1.5 });
      await expectSidebar(false);
    },
  ],
  [
    "an edge swipe shorter than the minimum travel does not",
    async () => {
      edgeSwipe({ travelDp: MIN_TRAVEL_DP * 0.75 });
      await expectSidebar(false);
    },
  ],
  [
    "an edge swipe that drifts too far sideways does not",
    async () => {
      edgeSwipe({ driftDp: MAX_DRIFT_DP * 1.25 });
      await expectSidebar(false);
    },
  ],
  [
    "a vertical scroll on the dashboard does not",
    async () => {
      device.swipe(width / 2, height * 0.75, width / 2, height * 0.25);
      await expectSidebar(false);
    },
  ],
  [
    "swipes over a feed keep it open and a tap closes it",
    async () => {
      if (!feed) {
        throw new CheckFailed("no feed configured");
      }
      device.send("feed_show", { id: feed.id });
      await waitFor(`showing ${feed.name}`, () => state().showing, (id) => id === feed.id);
      device.swipe(width / 2, height * 0.75, width / 2, height * 0.25);
      device.swipe(width * 0.3, height / 2, width * 0.7, height / 2);
      await holds("feed showing", () => state().showing, (id) => id === feed.id, 2_000);
      device.tap(width / 2, height / 2);
      await waitFor("feed closed", () => state().showing, (id) => id === null);
    },
  ],
  [
    "swiping on the screensaver changes photo without waking",
    async () => {
      if (typeof screensaverMode !== "string" || !SLIDESHOW_MODES.has(screensaverMode)) {
        throw new CheckFailed(`screensaver mode ${String(screensaverMode)} is not a slideshow`);
      }
      await startScreensaver();
      device.swipe(width * 0.8, height / 2, width * 0.2, height / 2);
      await holds("idle after a swipe", () => state().idle, (idle) => idle, 1_500);
      await waitFor("a forward navigation", navigations, (seen) => seen.includes("forward"));
      device.swipe(width * 0.2, height / 2, width * 0.8, height / 2);
      await waitFor("a back navigation", navigations, (seen) => seen.includes("back"));
      await holds("idle", () => state().idle, (idle) => idle, 1_500);
    },
  ],
  [
    "an edge swipe on the screensaver does not open the sidebar behind it",
    async () => {
      await startScreensaver();
      edgeSwipe();
      await sleep(1_000);
      device.send("wake");
      await expectSidebar(false);
    },
  ],
  [
    "a tap wakes the screensaver",
    async () => {
      await startScreensaver();
      device.tap(width / 2, height / 2);
      await waitFor("awake", () => state().idle, (idle) => !idle);
    },
  ],
];

await runChecks(checks, async () => {
  device.send("feed_close");
  device.send("wake");
  const scrim = sidebarScrim();
  if (scrim) {
    device.tap(scrim.x, scrim.y);
  }
  await waitFor("sidebar closed", sidebarOpen, (open) => !open);
});
