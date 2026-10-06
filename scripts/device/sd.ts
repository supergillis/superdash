#!/usr/bin/env node
// Drive a superdash debug build over adb. Needs a build made with
// -Psuperdash.debugTools=true; `sd install` makes and installs one.
import { spawnSync } from "node:child_process";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { parseArgs } from "node:util";
import { Device, PACKAGE, REPO_ROOT, UsageError } from "./device.ts";

const APK = join(REPO_ROOT, "packages/app/build/outputs/apk/debug/app-debug.apk");

const USAGE = `Usage: sd [--device <serial>] <command> [args]

  install                        build with debug tools, upgrade-install, launch
  dump                           all settings as JSON, secrets redacted
  get <key>                      one setting
  set <key> <value> [--type t]   write a setting; t is bool|int|long|float|double|string,
                                 inferred from the stored value when omitted
  remove <key>                   delete a setting
  feed state                     enabled, idle, showing, active triggers, configured feeds
  feed show <id|name>            force a feed open
  feed close                     close the shown feed
  feed upsert <json|@file>       add or replace a feed by id
  feed remove <id|name>          delete a feed
  ha <entity> [on|off]           read an HA entity, or toggle an input_boolean.superdash_test* helper
  ha --list [prefix]             entity ids and states, e.g. --list camera.
  idle | wake                    force the screensaver idle state, or touch to leave it
  ui                             on-screen elements: center, text, description, id
  tap <x> <y> | tap <label>      tap a point, or the element with that text, description or id
  swipe <x1> <y1> <x2> <y2> [ms] swipe between two points (default 250 ms)
  text <value>                   type into the focused field
  key <name>                     press a key, e.g. back, home, enter
  screenshot [file]              save a PNG (default build/sd-screenshot.png)
  logs [--clear]                 dump the superdash log tag

The device is --device, then $ANDROID_SERIAL, then the only connected device.`;

function printReply(reply: string): void {
  try {
    console.log(JSON.stringify(JSON.parse(reply), null, 2));
  } catch {
    console.log(reply);
  }
}

function resolveFeedId(device: Device, idOrName: string): string {
  const state = device.feedState();
  const feed =
    state.feeds.find((candidate) => candidate.id === idOrName) ??
    state.feeds.find((candidate) => candidate.name.toLowerCase() === idOrName.toLowerCase());
  if (!feed) {
    throw new UsageError(`no feed with id or name ${idOrName}; have: ${state.feeds.map((it) => it.name).join(", ")}`);
  }
  return feed.id;
}

function install(device: Device): void {
  const build = spawnSync("./gradlew", [":packages:app:assembleDebug", "-Psuperdash.debugTools=true"], {
    cwd: REPO_ROOT,
    stdio: "inherit",
  });
  if (build.status !== 0) {
    throw new Error("gradle build failed");
  }
  // -r upgrades in place, which keeps the HA login and settings.
  device.adb(["install", "-r", APK]);
  device.adb(["shell", "am", "start", "-n", `${PACKAGE}/.MainActivity`]);
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
  const device = Device.pick(values.device);
  const need = (index: number, name: string): string => requireArg(rest, index, name);

  switch (command) {
    case "install":
      install(device);
      break;
    case "dump":
      printReply(device.send("dump"));
      break;
    case "get": {
      const key = need(0, "key");
      const settings = JSON.parse(device.send("dump")) as Record<string, unknown>;
      if (!(key in settings)) {
        throw new Error(`${key} is not stored (the app uses its default)`);
      }
      printReply(JSON.stringify(settings[key]));
      break;
    }
    case "set":
      printReply(device.send("set", { key: need(0, "key"), value: need(1, "value"), type: values.type }));
      break;
    case "remove":
      printReply(device.send("remove", { key: need(0, "key") }));
      break;
    case "feed":
      feedCommand(device, need(0, "state|show|close|upsert|remove"), rest.slice(1));
      break;
    case "ui":
      for (const node of device.ui()) {
        const label = [node.text, node.desc && `desc=${node.desc}`, node.id && `id=${node.id}`].filter(Boolean).join("  ");
        console.log(`${String(Math.round(node.x)).padStart(5)},${String(Math.round(node.y)).padEnd(5)} ${label}`);
      }
      break;
    case "tap": {
      const first = need(0, "x|label");
      if (rest[1] !== undefined && /^\d+$/.test(first)) {
        device.tap(Number(first), Number(rest[1]));
      } else {
        const node = device.find(first);
        if (!node) {
          throw new Error(`nothing on screen labelled ${first}; see sd ui`);
        }
        device.tap(node.x, node.y);
      }
      break;
    }
    case "swipe": {
      const [fromX, fromY, toX, toY] = [0, 1, 2, 3].map((index) => Number(need(index, "x1 y1 x2 y2"))) as [number, number, number, number];
      device.swipe(fromX, fromY, toX, toY, rest[4] === undefined ? undefined : Number(rest[4]));
      break;
    }
    case "text":
      device.text(need(0, "value"));
      break;
    case "key":
      device.key(need(0, "name"));
      break;
    case "idle":
    case "wake":
      printReply(device.send(command));
      break;
    case "ha":
      if (values.list) {
        printReply(device.send("ha_list", { prefix: rest[0] }));
      } else {
        printReply(device.send("ha_state", { entity: need(0, "entity"), value: rest[1] }));
      }
      break;
    case "screenshot": {
      const file = rest[0] ?? join(REPO_ROOT, "build/sd-screenshot.png");
      mkdirSync(dirname(file), { recursive: true });
      writeFileSync(file, device.adb(["exec-out", "screencap", "-p"]));
      console.log(file);
      break;
    }
    case "logs":
      if (values.clear) {
        device.clearLogs();
      } else {
        process.stdout.write(device.logs());
      }
      break;
    default:
      throw new UsageError(`unknown command ${command}`);
  }
}

function feedCommand(device: Device, action: string, args: string[]): void {
  const arg = (name: string): string => requireArg(args, 0, name);
  switch (action) {
    case "state":
      printReply(device.send("feed_state"));
      break;
    case "show":
      printReply(device.send("feed_show", { id: resolveFeedId(device, arg("id|name")) }));
      break;
    case "close":
      printReply(device.send("feed_close"));
      break;
    case "upsert": {
      const source = arg("json|@file");
      const json = source.startsWith("@") ? readFileSync(source.slice(1), "utf8") : source;
      printReply(device.send("feed_upsert", { value: JSON.stringify(JSON.parse(json)) }));
      break;
    }
    case "remove":
      printReply(device.send("feed_remove", { id: resolveFeedId(device, arg("id|name")) }));
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
