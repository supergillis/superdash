// Regenerates the bundled Material Design Icons font, name lookup and license.
// Run from anywhere: node scripts/icons/generate-mdi.ts
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { copyFileSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";

const MDI_VERSION = "7.4.47";
const PINNED_INTEGRITY: Record<string, string> = {
  "@mdi/font": "sha512-43MtGpd585SNzHZPcYowu/84Vz2a2g31TvPMTm9uTiCSWzaheQySUcSyUH/46fPnuPQWof2yd0pGBtzee/IQWw==",
  "@mdi/svg": "sha512-WQ2gDll12T9WD34fdRFgQVgO8bag3gavrAgJ0frN4phlwdJARpE6gO1YvLEMJR0KKgoc+/Ea/A0Pp11I00xBvw==",
};
const REGISTRY = "https://registry.npmjs.org";
const APP_MAIN = resolve(import.meta.dirname, "../../packages/app/src/main");
const FONT_OUTPUT = join(APP_MAIN, "res/font/materialdesignicons.ttf");
const ICONS_DIRECTORY = join(APP_MAIN, "assets/mdi");

type IconMeta = { name: string; codepoint: string; aliases: string[] };

async function downloadPackage(packageName: string, targetDirectory: string): Promise<string> {
  const baseName = packageName.split("/").pop() ?? packageName;
  const metadataResponse = await fetch(`${REGISTRY}/${packageName}/${MDI_VERSION}`);
  if (!metadataResponse.ok) {
    throw new Error(`${packageName} metadata returned ${metadataResponse.status}`);
  }
  const { dist } = (await metadataResponse.json()) as { dist: { tarball: string } };

  const tarballResponse = await fetch(dist.tarball);
  if (!tarballResponse.ok) {
    throw new Error(`${dist.tarball} returned ${tarballResponse.status}`);
  }
  const tarball = Buffer.from(await tarballResponse.arrayBuffer());
  const actualIntegrity = `sha512-${createHash("sha512").update(tarball).digest("base64")}`;
  if (actualIntegrity !== PINNED_INTEGRITY[packageName]) {
    throw new Error(`${packageName} integrity mismatch: ${actualIntegrity}`);
  }

  const archivePath = join(targetDirectory, `${baseName}.tgz`);
  writeFileSync(archivePath, tarball);
  const extractDirectory = join(targetDirectory, baseName);
  mkdirSync(extractDirectory);
  execFileSync("tar", ["-xzf", archivePath, "-C", extractDirectory, "--strip-components=1"]);
  return extractDirectory;
}

const workDirectory = mkdtempSync(join(tmpdir(), "mdi-"));
try {
  const fontDirectory = await downloadPackage("@mdi/font", workDirectory);
  const svgDirectory = await downloadPackage("@mdi/svg", workDirectory);

  const metadata: IconMeta[] = JSON.parse(readFileSync(join(svgDirectory, "meta.json"), "utf8"));
  const icons: Record<string, number> = {};
  for (const icon of metadata) {
    icons[icon.name] = Number.parseInt(icon.codepoint, 16);
  }
  // A real icon name always wins over an alias that reuses it, and an alias claimed by
  // several icons is ambiguous, so it is dropped.
  const aliasOwners: Record<string, string[]> = {};
  for (const icon of metadata) {
    for (const alias of icon.aliases) {
      if (!(alias in icons)) {
        (aliasOwners[alias] ??= []).push(icon.name);
      }
    }
  }
  const aliases: Record<string, string> = {};
  for (const [alias, owners] of Object.entries(aliasOwners)) {
    if (owners.length === 1) {
      aliases[alias] = owners[0];
    }
  }

  mkdirSync(join(APP_MAIN, "res/font"), { recursive: true });
  mkdirSync(ICONS_DIRECTORY, { recursive: true });
  copyFileSync(join(fontDirectory, "fonts", "materialdesignicons-webfont.ttf"), FONT_OUTPUT);
  copyFileSync(join(fontDirectory, "LICENSE"), join(ICONS_DIRECTORY, "LICENSE"));
  writeFileSync(join(ICONS_DIRECTORY, "codepoints.json"), JSON.stringify({ icons, aliases }));
  console.log(`wrote ${Object.keys(icons).length} icons and ${Object.keys(aliases).length} aliases`);
} finally {
  rmSync(workDirectory, { recursive: true, force: true });
}
