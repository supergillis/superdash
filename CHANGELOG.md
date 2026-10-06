# Changelog

## [1.1.0](https://github.com/supergillis/superdash/compare/v1.0.0...v1.1.0) (2026-10-06)


### Features

* **feed:** play rtsp:// camera URLs directly ([#69](https://github.com/supergillis/superdash/issues/69)) ([3c8dd4d](https://github.com/supergillis/superdash/commit/3c8dd4de2e4b261d06e02c37c2527a7becb2c7b7))


### Bug Fixes

* **feed:** stop waiting on an HA camera stream after 15 seconds ([#72](https://github.com/supergillis/superdash/issues/72)) ([9adddc1](https://github.com/supergillis/superdash/commit/9adddc135c6f81ea27aabbcca7db52b80e013bc2))
* **screensaver:** stop idle polling while paused and cap video waits ([#75](https://github.com/supergillis/superdash/issues/75)) ([9845fc5](https://github.com/supergillis/superdash/commit/9845fc54949f2a245a63074312f1b0bad1d9a844))
* translate missing strings and darken Settings status bar icons ([#73](https://github.com/supergillis/superdash/issues/73)) ([46ce9d8](https://github.com/supergillis/superdash/commit/46ce9d89d85e28fba9824c64e449917b001a6173))

## [1.0.0](https://github.com/supergillis/superdash/compare/v0.3.0...v1.0.0) (2026-10-06)


### ⚠ BREAKING CHANGES

* the ESPHome entities exposed to Home Assistant are renamed from doorbell_enabled, doorbell_ringing, and doorbell_count to feed_enabled, feed_showing, and feed_count. The doorbell_auto_close_sec number is removed; auto-close is configured per camera in the app. Update Home Assistant automations and dashboards that reference the old entity ids.

### Features

* **camera:** cap capture frame rate with a camera_max_fps setting ([#55](https://github.com/supergillis/superdash/issues/55)) ([a564ba4](https://github.com/supergillis/superdash/commit/a564ba4392ccca45b7c1c86a47d33e2274e5fd07))
* **camera:** expose the tablet camera to Home Assistant over ESPHome ([#37](https://github.com/supergillis/superdash/issues/37)) ([10990d9](https://github.com/supergillis/superdash/commit/10990d9d4ffe4a52eebb7a28e6cc7e6a2ee6fada))
* **debug:** add an adb control surface, sd script and feed smoke test ([#66](https://github.com/supergillis/superdash/issues/66)) ([ddae78c](https://github.com/supergillis/superdash/commit/ddae78c1ff3df3c9deb600453258d0e9522d08a4))
* generalize the doorbell overlay into camera feeds ([#57](https://github.com/supergillis/superdash/issues/57)) ([fe4b2f8](https://github.com/supergillis/superdash/commit/fe4b2f81ba202c27664a95947456413f7c4f95b5))
* make the settings sidebar discoverable (pin by default + edge handle) ([#40](https://github.com/supergillis/superdash/issues/40)) ([80d779a](https://github.com/supergillis/superdash/commit/80d779a19726ff90da0398e07541f552475203f9))


### Bug Fixes

* **a11y:** truly clear background semantics behind open modal sidebar ([#45](https://github.com/supergillis/superdash/issues/45)) ([25ef15c](https://github.com/supergillis/superdash/commit/25ef15cb8b3f67a4a8f85b40ae3ad36b2c25773c))
* batch of low-effort HA-client and kiosk fixes ([#11](https://github.com/supergillis/superdash/issues/11), [#12](https://github.com/supergillis/superdash/issues/12), [#13](https://github.com/supergillis/superdash/issues/13), [#18](https://github.com/supergillis/superdash/issues/18)) ([7bc6d2e](https://github.com/supergillis/superdash/commit/7bc6d2e89d016b33214066a3ebe7879a5a077915))
* **camera:** recover capture when the camera permission is granted, and quiet ESPHome disconnects ([#50](https://github.com/supergillis/superdash/issues/50)) ([7d28790](https://github.com/supergillis/superdash/commit/7d28790f3f47b6a6e298852832769ef8cd40a6b0))
* **immich:** load album assets via search/metadata for Immich v3 ([#49](https://github.com/supergillis/superdash/issues/49)) ([2b03664](https://github.com/supergillis/superdash/commit/2b036648ad2e7e6d2396a62b76e9270a463ffefe))
* restore screensaver swipes and stop swipes closing feeds ([#68](https://github.com/supergillis/superdash/issues/68)) ([51ef825](https://github.com/supergillis/superdash/commit/51ef825305ef6d1261da0f8e98751af7220f1a5c))
* **settings:** keep Settings clear of the status bar and taskbar ([#67](https://github.com/supergillis/superdash/issues/67)) ([f5a734b](https://github.com/supergillis/superdash/commit/f5a734b8090f8d7557fda85a4031d49b00f63352))

## [0.3.0](https://github.com/supergillis/superdash/compare/v0.2.0...v0.3.0) (2026-07-07)


### Features

* lower minSdk to 28 (Android 9) ([#41](https://github.com/supergillis/superdash/issues/41)) ([8e99449](https://github.com/supergillis/superdash/commit/8e99449153e16e50bb97f950cbbfe550a0595d23))
