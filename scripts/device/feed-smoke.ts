#!/usr/bin/env node
// End-to-end feed checks on a real device: HA trigger -> app -> overlay state.
// Needs HA toggle helpers input_boolean.superdash_test_ring and _monitor.
import { parseArgs } from "node:util";
import { CheckFailed, type Check, runChecks, sleep } from "./checks.ts";
import { Device, type FeedConfig, type FeedStateReply } from "./device.ts";

const RING = "input_boolean.superdash_test_ring";
const MONITOR = "input_boolean.superdash_test_monitor";
const RING_AUTO_CLOSE_SEC = 8;
// FeedWatcher ignores a ring within 5 s of the previous one.
const RING_DEBOUNCE_MS = 5_500;

const { values } = parseArgs({
  options: {
    device: { type: "string", short: "d" },
    camera: { type: "string" },
  },
});
if (!values.camera) {
  throw new Error("pass --camera <HA camera entity or stream URL> for the test feeds to play");
}
const camera = values.camera;
const device = Device.pick(values.device);

const ringFeed: FeedConfig = {
  id: "test-ring",
  name: "test ring",
  triggerEntity: RING,
  cameraEntity: camera,
  autoCloseSec: RING_AUTO_CLOSE_SEC,
  order: 10,
};
const monitorFeed: FeedConfig = {
  id: "test-monitor",
  name: "test monitor",
  triggerEntity: MONITOR,
  cameraEntity: camera,
  trigger: { type: "sustained", activeStates: ["on"] },
  autoCloseSec: 0,
  order: 5,
};

/** Polls until the shown feed is [expected], or fails after [timeoutMs]. */
async function expectShowing(expected: string | null, timeoutMs = 5_000): Promise<FeedStateReply> {
  const deadline = Date.now() + timeoutMs;
  let state = device.feedState();
  while (state.showing !== expected) {
    if (Date.now() > deadline) {
      throw new CheckFailed(`expected showing=${expected}, got ${state.showing} (active: ${Object.keys(state.active)})`);
    }
    await sleep(300);
    state = device.feedState();
  }
  return state;
}

/** Fails if the shown feed changes away from [expected] within [durationMs]. */
async function expectStays(expected: string | null, durationMs: number): Promise<void> {
  const deadline = Date.now() + durationMs;
  while (Date.now() < deadline) {
    const { showing } = device.feedState();
    if (showing !== expected) {
      throw new CheckFailed(`expected showing=${expected} to hold, got ${showing}`);
    }
    await sleep(500);
  }
}

async function expectEntity(entity: string, expected: string, timeoutMs = 5_000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const { state } = JSON.parse(device.send("ha_state", { entity })) as { state: string };
    if (state === expected) {
      return;
    }
    if (Date.now() > deadline) {
      throw new CheckFailed(`expected ${entity}=${expected}, got ${state}`);
    }
    await sleep(300);
  }
}

let lastRingAt = 0;

async function ring(): Promise<number> {
  const wait = lastRingAt + RING_DEBOUNCE_MS - Date.now();
  if (wait > 0) {
    await sleep(wait);
  }
  device.toggle(RING, "off");
  device.toggle(RING, "on");
  lastRingAt = Date.now();
  return lastRingAt;
}

async function expectWakesFromIdle(): Promise<void> {
  device.send("idle");
  if (!device.feedState().idle) {
    throw new CheckFailed("could not force idle");
  }
  await ring();
  const state = await expectShowing(ringFeed.id);
  if (state.idle) {
    throw new CheckFailed("feed showed but the screen stayed idle");
  }
}

async function reset(): Promise<void> {
  device.toggle(RING, "off");
  device.toggle(MONITOR, "off");
  device.send("wake");
  device.send("feed_close");
  await expectShowing(null);
}

const checks: Check[] = [
  [
    "sustained feed shows while its trigger is on and hides when it turns off",
    async () => {
      device.toggle(MONITOR, "on");
      await expectShowing(monitorFeed.id);
      device.toggle(MONITOR, "off");
      await expectShowing(null);
    },
  ],
  [
    "closing a sustained feed keeps it closed until the trigger re-arms",
    async () => {
      device.toggle(MONITOR, "on");
      await expectShowing(monitorFeed.id);
      device.send("feed_close");
      await expectShowing(null);
      await expectStays(null, 3_000);
      device.toggle(MONITOR, "off");
      device.toggle(MONITOR, "on");
      await expectShowing(monitorFeed.id);
    },
  ],
  [
    "momentary feed opens on a ring and auto-closes",
    async () => {
      const opened = await ring();
      await expectShowing(ringFeed.id);
      device.toggle(RING, "off");
      await expectShowing(null, (RING_AUTO_CLOSE_SEC + 4) * 1_000);
      const seconds = (Date.now() - opened) / 1_000;
      if (seconds < RING_AUTO_CLOSE_SEC - 1) {
        throw new CheckFailed(`closed after ${seconds.toFixed(1)} s, expected about ${RING_AUTO_CLOSE_SEC} s`);
      }
    },
  ],
  [
    "a second ring restarts the auto-close timer",
    async () => {
      const firstRing = await ring();
      await expectShowing(ringFeed.id);
      await ring();
      const firstCloseAt = firstRing + RING_AUTO_CLOSE_SEC * 1_000;
      await expectStays(ringFeed.id, firstCloseAt + 1_500 - Date.now());
      await expectShowing(null, RING_AUTO_CLOSE_SEC * 1_000);
    },
  ],
  [
    "testing a feed and closing it does not silence its trigger",
    async () => {
      device.send("feed_show", { id: ringFeed.id });
      await expectShowing(ringFeed.id);
      device.send("feed_close");
      await expectShowing(null);
      await ring();
      await expectShowing(ringFeed.id);
    },
  ],
  [
    "higher order wins and closing it falls back to the still-active feed",
    async () => {
      device.toggle(MONITOR, "on");
      await expectShowing(monitorFeed.id);
      await ring();
      await expectShowing(ringFeed.id);
      device.send("feed_close");
      await expectShowing(monitorFeed.id);
    },
  ],
  [
    "a feed that does not wake the screen waits until the screen is in use",
    async () => {
      device.upsertFeed({ ...monitorFeed, wakeScreen: false });
      try {
        device.send("idle");
        device.toggle(MONITOR, "on");
        await expectStays(null, 3_000);
        device.send("wake");
        await expectShowing(monitorFeed.id);
      } finally {
        device.upsertFeed(monitorFeed);
      }
    },
  ],
  [
    "HA's feed_showing sensor follows the overlay",
    async () => {
      const sensors = JSON.parse(device.send("ha_list", { prefix: "binary_sensor." })) as Record<string, string>;
      const showingSensor = Object.keys(sensors).find((entity) => entity.endsWith("_feed_showing"));
      if (!showingSensor) {
        throw new CheckFailed("no binary_sensor.*_feed_showing in HA; is ESPHome enabled and adopted?");
      }
      device.toggle(MONITOR, "on");
      await expectShowing(monitorFeed.id);
      await expectEntity(showingSensor, "on");
      device.toggle(MONITOR, "off");
      await expectEntity(showingSensor, "off");
    },
  ],
  [
    "a feed that wakes the screen shows while idle and leaves idle",
    async () => {
      await expectWakesFromIdle();
    },
  ],
  [
    "the same holds in night mode",
    async () => {
      const wasNight = JSON.parse(device.send("dump")).night_mode_active === true;
      device.send("set", { key: "night_mode_active", value: "true", type: "bool" });
      try {
        await expectWakesFromIdle();
      } finally {
        device.send("set", { key: "night_mode_active", value: String(wasNight), type: "bool" });
      }
    },
  ],
];

device.upsertFeed(ringFeed);
device.upsertFeed(monitorFeed);
if (!device.feedState().enabled) {
  throw new Error("feeds are disabled on the device; enable them first");
}

await runChecks(checks, reset);
