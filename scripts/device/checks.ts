// A tiny runner for on-device checks: reset, run, report, exit code.
export class CheckFailed extends Error {}

export type Check = [name: string, run: () => Promise<void>];

export const sleep = (ms: number) => new Promise((done) => setTimeout(done, ms));

/** Polls [read] until [accept] holds, or fails after [timeoutMs] with the last value. */
export async function waitFor<T>(
  what: string,
  read: () => T,
  accept: (value: T) => boolean,
  timeoutMs = 5_000,
): Promise<T> {
  const deadline = Date.now() + timeoutMs;
  let value = read();
  while (!accept(value)) {
    if (Date.now() > deadline) {
      throw new CheckFailed(`expected ${what}, got ${JSON.stringify(value)}`);
    }
    await sleep(300);
    value = read();
  }
  return value;
}

/** Fails if [accept] stops holding within [durationMs]. */
export async function holds<T>(what: string, read: () => T, accept: (value: T) => boolean, durationMs: number) {
  const deadline = Date.now() + durationMs;
  while (Date.now() < deadline) {
    const value = read();
    if (!accept(value)) {
      throw new CheckFailed(`expected ${what} to hold, got ${JSON.stringify(value)}`);
    }
    await sleep(500);
  }
}

export async function runChecks(checks: Check[], reset: () => Promise<void>): Promise<void> {
  let failures = 0;
  for (const [name, check] of checks) {
    const started = Date.now();
    try {
      await reset();
      await check();
      console.log(`✓ ${name} (${((Date.now() - started) / 1_000).toFixed(1)} s)`);
    } catch (error) {
      failures++;
      console.log(`✗ ${name}\n    ${error instanceof Error ? error.message : String(error)}`);
    }
  }
  await reset();
  console.log(failures === 0 ? `\nall ${checks.length} checks passed` : `\n${failures} of ${checks.length} checks failed`);
  process.exitCode = failures === 0 ? 0 : 1;
}
