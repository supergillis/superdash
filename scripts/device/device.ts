// adb access to a superdash debug build with -Psuperdash.debugTools=true.
import { execFileSync } from "node:child_process";
import { existsSync } from "node:fs";
import { homedir } from "node:os";
import { join, resolve } from "node:path";

export const PACKAGE = "com.superdash";
export const REPO_ROOT = resolve(import.meta.dirname, "../..");

const RECEIVER = `${PACKAGE}/.debug.DebugToolsReceiver`;
const RESULT_OK = -1;

export class UsageError extends Error {}

export interface FeedConfig {
  id: string;
  name: string;
  triggerEntity: string;
  cameraEntity: string;
  trigger?: { type: "sustained"; activeStates?: string[] };
  autoCloseSec?: number;
  wakeScreen?: boolean;
  order?: number;
}

export interface FeedStateReply {
  enabled: boolean;
  idle: boolean;
  showing: string | null;
  active: Record<string, number>;
  feeds: FeedConfig[];
}

const adbPath = [
  process.env.ADB,
  process.env.ANDROID_HOME && join(process.env.ANDROID_HOME, "platform-tools/adb"),
  join(homedir(), "Android/Sdk/platform-tools/adb"),
  "/opt/homebrew/share/android-commandlinetools/platform-tools/adb",
].find((path): path is string => !!path && existsSync(path)) ?? "adb";

// adb shell re-joins arguments into one remote command line, so anything with
// spaces or quotes travels base64-encoded and every plain token is checked.
function shellToken(value: string): string {
  if (!/^[\w.:@\-/]+$/.test(value)) {
    throw new UsageError(`unsafe argument: ${value}`);
  }
  return value;
}

export interface UiNode {
  text: string;
  desc: string;
  id: string;
  x: number;
  y: number;
}

function decodeXml(value: string): string {
  return value.replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&amp;/g, "&");
}

export class Device {
  readonly serial: string;

  private constructor(serial: string) {
    this.serial = serial;
  }

  /** --device, then $ANDROID_SERIAL, then the only connected device. */
  static pick(requested?: string): Device {
    const explicit = requested ?? process.env.ANDROID_SERIAL;
    if (explicit) {
      return new Device(explicit);
    }
    const devices = execFileSync(adbPath, ["devices", "-l"], { encoding: "utf8" })
      .split("\n")
      .slice(1)
      .map((line) => line.split(/\s+/))
      .filter((fields) => fields[1] === "device");
    // A wireless device can be listed twice, by ip:port and by its mDNS name; the
    // transport-independent "device:" field tells the duplicates apart.
    const unique = new Map(devices.map((fields) => [fields.find((field) => field.startsWith("device:")), fields[0]]));
    const [only, ...others] = unique.values();
    if (!only || others.length > 0) {
      throw new UsageError(`expected one connected device, found ${unique.size}; pass --device`);
    }
    return new Device(only);
  }

  adb(args: string[]): Buffer {
    return execFileSync(adbPath, ["-s", this.serial, ...args], { maxBuffer: 64 * 1024 * 1024 });
  }

  /** Runs one receiver command and returns its reply; a failed command throws. */
  send(command: string, extras: Record<string, string | undefined> = {}): string {
    const args = ["shell", "am", "broadcast", "-n", RECEIVER, "--es", "cmd", shellToken(command)];
    for (const [name, value] of Object.entries(extras)) {
      if (value === undefined) {
        continue;
      }
      if (name === "value") {
        args.push("--es", "value_b64", Buffer.from(value).toString("base64"));
      } else {
        args.push("--es", shellToken(name), shellToken(value));
      }
    }
    const output = this.adb(args).toString();
    const match = /Broadcast completed: result=(-?\d+)(?:, data="([\s\S]*)")?\s*$/.exec(output);
    if (!match || match[2] === undefined) {
      throw new Error(
        `no reply from ${RECEIVER}. Is a debug build with -Psuperdash.debugTools=true installed? Try: sd install\n${output.trim()}`,
      );
    }
    if (Number(match[1]) !== RESULT_OK) {
      throw new Error(match[2]);
    }
    return match[2];
  }

  tap(x: number, y: number): void {
    this.adb(["shell", "input", "tap", String(Math.round(x)), String(Math.round(y))]);
  }

  swipe(fromX: number, fromY: number, toX: number, toY: number, durationMs = 250): void {
    const points = [fromX, fromY, toX, toY, durationMs].map((value) => String(Math.round(value)));
    this.adb(["shell", "input", "swipe", ...points]);
  }

  /** Types into the focused field. `input text` treats %s as a space. */
  text(value: string): void {
    const escaped = value.replace(/%/g, "%%").replace(/ /g, "%s").replace(/'/g, "'\\''");
    this.adb(["shell", `input text '${escaped}'`]);
  }

  key(name: string): void {
    this.adb(["shell", "input", "keyevent", shellToken(`KEYCODE_${name.toUpperCase()}`)]);
  }

  /** The on-screen accessibility tree, flattened to labelled nodes with their centers. */
  ui(): UiNode[] {
    const xml = this.adb(["exec-out", "uiautomator", "dump", "/dev/tty"]).toString();
    const nodes: UiNode[] = [];
    for (const [, attributes] of xml.matchAll(/<node ([^>]*?)\/?>/g)) {
      const attribute = (name: string) => decodeXml(new RegExp(`${name}="([^"]*)"`).exec(attributes ?? "")?.[1] ?? "");
      const bounds = /\[(\d+),(\d+)\]\[(\d+),(\d+)\]/.exec(attribute("bounds"));
      const node = { text: attribute("text"), desc: attribute("content-desc").trim(), id: attribute("resource-id") };
      if (bounds && (node.text || node.desc || node.id)) {
        const [left, top, right, bottom] = bounds.slice(1).map(Number) as [number, number, number, number];
        nodes.push({ ...node, x: (left + right) / 2, y: (top + bottom) / 2 });
      }
    }
    return nodes;
  }

  /** The first node whose text, description or id equals [label]. */
  find(label: string): UiNode | undefined {
    return this.ui().find((node) => node.text === label || node.desc === label || node.id === label);
  }

  /** Lines since the last `logs --clear`, for the superdash tag. */
  logs(): string {
    return this.adb(["logcat", "-d", "-v", "time", "-s", "superdash"]).toString();
  }

  clearLogs(): void {
    this.adb(["logcat", "-c"]);
  }

  feedState(): FeedStateReply {
    return JSON.parse(this.send("feed_state")) as FeedStateReply;
  }

  upsertFeed(config: FeedConfig): void {
    this.send("feed_upsert", { value: JSON.stringify(config) });
  }

  toggle(entity: string, state: "on" | "off"): void {
    this.send("ha_state", { entity, value: state });
  }
}
