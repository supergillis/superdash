#!/usr/bin/env node
// Drive a superdash debug build over adb. Needs a build made with
// -Psuperdash.debugTools=true; `sd install` makes and installs one.
import { execFileSync, spawnSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { parseArgs } from "node:util";

const PACKAGE = "com.superdash";
const RECEIVER = `${PACKAGE}/.debug.DebugToolsReceiver`;
const REPO_ROOT = resolve(import.meta.dirname, "../..");
const APK = join(REPO_ROOT, "packages/app/build/outputs/apk/debug/app-debug.apk");
const RESULT_OK = -1;

const USAGE = `Usage: sd [--device <serial>] <command> [args]

  install                        build with debug tools, upgrade-install, launch
  dump                           all settings as JSON, secrets redacted
  get <key>                      one setting
  set <key> <value> [--type t]   write a setting; t is bool|int|long|float|double|string,
                                 inferred from the stored value when omitted
  remove <key>                   delete a setting
  feed state                     enabled, showing, active triggers, configured feeds
  feed show <id|name>            force a feed open
  feed close                     close the shown feed
  feed upsert <json|@file>       add or replace a feed by id
  feed remove <id|name>          delete a feed
  ha <entity> [on|off]           read an HA entity, or toggle an input_boolean.superdash_test* helper
  ha --list [prefix]             entity ids and states, e.g. --list camera.
  screenshot [file]              save a PNG (default build/sd-screenshot.png)
  logs [--clear]                 dump the superdash log tag

The device is --device, then $ANDROID_SERIAL, then the only connected device.`;

class UsageError extends Error {}

function findAdb(): string {
  const candidates = [
    process.env.ADB,
    process.env.ANDROID_HOME && join(process.env.ANDROID_HOME, "platform-tools/adb"),
    join(homedir(), "Android/Sdk/platform-tools/adb"),
    "/opt/homebrew/share/android-commandlinetools/platform-tools/adb",
  ];
  return candidates.find((path): path is string => !!path && existsSync(path)) ?? "adb";
}

const adb = findAdb();

function pickDevice(requested: string | undefined): string {
  const explicit = requested ?? process.env.ANDROID_SERIAL;
  if (explicit) {
    return explicit;
  }
  const devices = execFileSync(adb, ["devices", "-l"], { encoding: "utf8" })
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
  return only;
}

function adbRun(device: string, args: string[], options: { binary?: boolean } = {}): Buffer {
  return execFileSync(adb, ["-s", device, ...args], { maxBuffer: 64 * 1024 * 1024, encoding: options.binary ? "buffer" : undefined });
}

// adb shell re-joins arguments into one remote command line, so anything with
// spaces or quotes travels base64-encoded and every plain token is checked.
function shellToken(value: string): string {
  if (!/^[\w.:@\-/]+$/.test(value)) {
    throw new UsageError(`unsafe argument: ${value}`);
  }
  return value;
}

function send(device: string, command: string, extras: Record<string, string | undefined> = {}): string {
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
  const output = adbRun(device, args).toString();
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

function printReply(reply: string): void {
  try {
    console.log(JSON.stringify(JSON.parse(reply), null, 2));
  } catch {
    console.log(reply);
  }
}

interface FeedSummary {
  id: string;
  name: string;
}

function resolveFeedId(device: string, idOrName: string): string {
  const state = JSON.parse(send(device, "feed_state")) as { feeds: FeedSummary[] };
  const feed =
    state.feeds.find((candidate) => candidate.id === idOrName) ??
    state.feeds.find((candidate) => candidate.name.toLowerCase() === idOrName.toLowerCase());
  if (!feed) {
    throw new UsageError(`no feed with id or name ${idOrName}; have: ${state.feeds.map((it) => it.name).join(", ")}`);
  }
  return feed.id;
}

function install(device: string): void {
  const build = spawnSync("./gradlew", [":packages:app:assembleDebug", "-Psuperdash.debugTools=true"], {
    cwd: REPO_ROOT,
    stdio: "inherit",
  });
  if (build.status !== 0) {
    throw new Error("gradle build failed");
  }
  // -r upgrades in place, which keeps the HA login and settings.
  adbRun(device, ["install", "-r", APK]);
  adbRun(device, ["shell", "am", "start", "-n", `${PACKAGE}/.MainActivity`]);
  console.log(`installed ${APK}`);
}

function requireArg(args: string[], index: number, name: string): string {
  const value = args[index];
  if (value === undefined) {
    throw new UsageError(`missing <${name}>`);
  }
  return value;
}

function main(argv: string[]): void {
  const { values, positionals } = parseArgs({
    args: argv,
    allowPositionals: true,
    options: {
      device: { type: "string", short: "d" },
      type: { type: "string", short: "t" },
      clear: { type: "boolean" },
      list: { type: "boolean" },
      help: { type: "boolean", short: "h" },
    },
  });
  const [command, ...rest] = positionals;
  if (values.help || !command) {
    console.log(USAGE);
    return;
  }
  const device = pickDevice(values.device);
  const need = (index: number, name: string): string => requireArg(rest, index, name);

  switch (command) {
    case "install":
      install(device);
      break;
    case "dump":
      printReply(send(device, "dump"));
      break;
    case "get": {
      const key = need(0, "key");
      const settings = JSON.parse(send(device, "dump")) as Record<string, unknown>;
      if (!(key in settings)) {
        throw new Error(`${key} is not stored (the app uses its default)`);
      }
      printReply(JSON.stringify(settings[key]));
      break;
    }
    case "set":
      printReply(send(device, "set", { key: need(0, "key"), value: need(1, "value"), type: values.type }));
      break;
    case "remove":
      printReply(send(device, "remove", { key: need(0, "key") }));
      break;
    case "feed":
      feedCommand(device, need(0, "state|show|close|upsert|remove"), rest.slice(1));
      break;
    case "ha":
      if (values.list) {
        printReply(send(device, "ha_list", { prefix: rest[0] }));
      } else {
        printReply(send(device, "ha_state", { entity: need(0, "entity"), value: rest[1] }));
      }
      break;
    case "screenshot": {
      const file = rest[0] ?? join(REPO_ROOT, "build/sd-screenshot.png");
      mkdirSync(dirname(file), { recursive: true });
      writeFileSync(file, adbRun(device, ["exec-out", "screencap", "-p"], { binary: true }));
      console.log(file);
      break;
    }
    case "logs":
      if (values.clear) {
        adbRun(device, ["logcat", "-c"]);
      } else {
        process.stdout.write(adbRun(device, ["logcat", "-d", "-v", "time", "-s", "superdash"]));
      }
      break;
    default:
      throw new UsageError(`unknown command ${command}`);
  }
}

function feedCommand(device: string, action: string, args: string[]): void {
  const arg = (name: string): string => requireArg(args, 0, name);
  switch (action) {
    case "state":
      printReply(send(device, "feed_state"));
      break;
    case "show":
      printReply(send(device, "feed_show", { id: resolveFeedId(device, arg("id|name")) }));
      break;
    case "close":
      printReply(send(device, "feed_close"));
      break;
    case "upsert": {
      const source = arg("json|@file");
      const json = source.startsWith("@") ? readFileSync(source.slice(1), "utf8") : source;
      printReply(send(device, "feed_upsert", { value: JSON.stringify(JSON.parse(json)) }));
      break;
    }
    case "remove":
      printReply(send(device, "feed_remove", { id: resolveFeedId(device, arg("id|name")) }));
      break;
    default:
      throw new UsageError(`unknown feed action ${action}`);
  }
}

try {
  main(process.argv.slice(2));
} catch (error) {
  console.error(`sd: ${error instanceof Error ? error.message : String(error)}`);
  if (error instanceof UsageError) {
    console.error(`\n${USAGE}`);
  }
  process.exit(1);
}
