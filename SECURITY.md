# Security Policy

superdash is a Home Assistant kiosk app for Android tablets. It stores Home
Assistant OAuth tokens on the device and runs an ESPHome native API server on
the local network, so vulnerabilities in either area (token storage/handling,
the Noise/NNpsk0 handshake, or the protobuf API surface) are of particular
interest.

## Supported versions

Only the latest release receives security fixes. Please reproduce the issue on
the most recent release before reporting.

## Reporting a vulnerability

Please do **not** open a public GitHub issue for security problems.

Instead, report privately via a GitHub security advisory:
<https://github.com/supergillis/superdash/security/advisories/new>

Include what you can: affected version, setup (Android version, HA version),
steps to reproduce, and impact.

## What to expect

- Acknowledgement within 7 days.
- An assessment and, for confirmed issues, a fix or mitigation plan within
  30 days where feasible. This is a spare-time open-source project, so complex
  issues may take longer — you will be kept informed.
- Credit in the release notes if you would like it.

Please give us a reasonable opportunity to release a fix before disclosing
publicly.
