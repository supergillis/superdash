# Changelog

## [1.2.0](https://github.com/supergillis/superdash/compare/v1.1.0...v1.2.0) (2026-10-21)


### Features

* **settings:** warn about unencrypted Home Assistant, Immich and ESPHome connections ([#87](https://github.com/supergillis/superdash/issues/87)) ([d3cb4cd](https://github.com/supergillis/superdash/commit/d3cb4cd1a5d3c82f2dd43768fc64279d8bcd3279)), closes [#21](https://github.com/supergillis/superdash/issues/21) [#22](https://github.com/supergillis/superdash/issues/22)


### Bug Fixes

* **app:** reload the dashboard WebView directly instead of through the bridge ([#86](https://github.com/supergillis/superdash/issues/86)) ([d1a19ec](https://github.com/supergillis/superdash/commit/d1a19ec611a1bcdb1915f09a6d8bfafdf5453aac)), closes [#23](https://github.com/supergillis/superdash/issues/23)
* **app:** show the dashboard after login on WebViews without document-start scripts ([#93](https://github.com/supergillis/superdash/issues/93)) ([0502489](https://github.com/supergillis/superdash/commit/0502489be292060193f337d0178947659937787a)), closes [#38](https://github.com/supergillis/superdash/issues/38) [#39](https://github.com/supergillis/superdash/issues/39)
* **esphome:** close the listener on startup failure and give mdns one owner ([#84](https://github.com/supergillis/superdash/issues/84)) ([cced1ed](https://github.com/supergillis/superdash/commit/cced1ed1b87a4afccd1308cd2f3709df06baabee)), closes [#8](https://github.com/supergillis/superdash/issues/8) [#9](https://github.com/supergillis/superdash/issues/9)
* **esphome:** give every tablet a distinct MAC and dedupe frame decoding ([#92](https://github.com/supergillis/superdash/issues/92)) ([af74051](https://github.com/supergillis/superdash/commit/af74051072a1d2010e0e0a1d22e5d25cef8071b8)), closes [#26](https://github.com/supergillis/superdash/issues/26) [#31](https://github.com/supergillis/superdash/issues/31)
* **ha-client:** check the OAuth token exchange status and match callbacks case-insensitively ([#91](https://github.com/supergillis/superdash/issues/91)) ([b416917](https://github.com/supergillis/superdash/commit/b416917dd1e024eb1b33518da2553d18f317cb0e)), closes [#26](https://github.com/supergillis/superdash/issues/26)
* **ha-client:** keep a reconnect wake-up that arrives during a failing attempt ([#85](https://github.com/supergillis/superdash/issues/85)) ([8ee115f](https://github.com/supergillis/superdash/commit/8ee115f1f2e38149e588c6c82d499b3d8ee69a7f)), closes [#29](https://github.com/supergillis/superdash/issues/29)
* **ha-client:** route websocket frames by id and probe dead sockets ([#79](https://github.com/supergillis/superdash/issues/79)) ([b6b4163](https://github.com/supergillis/superdash/commit/b6b41639d067d0aaff867058c02bc4617e5c7975))
* **immich:** fail the catalog refresh on a bad page and tolerate missing dates ([#83](https://github.com/supergillis/superdash/issues/83)) ([ffb4484](https://github.com/supergillis/superdash/commit/ffb44849b4632f11c9708386626858ce59b68b30)), closes [#15](https://github.com/supergillis/superdash/issues/15) [#16](https://github.com/supergillis/superdash/issues/16)
* **kiosk:** make start on boot and launch on wake actually launch ([#77](https://github.com/supergillis/superdash/issues/77)) ([ec4e56d](https://github.com/supergillis/superdash/commit/ec4e56d7826bbe238ffb7b3b06e5514d622c2baa))
* **security:** survive keystore outages without crashing or wiping secrets ([#96](https://github.com/supergillis/superdash/issues/96)) ([903b32f](https://github.com/supergillis/superdash/commit/903b32f0aa92b6814ab071c01bf18dd8693b22b6))
* **voice:** close hand-off, wake and shutdown races in the voice pipeline ([#94](https://github.com/supergillis/superdash/issues/94)) ([09017d6](https://github.com/supergillis/superdash/commit/09017d62ffbd1149a0ce2e3383f994ded55a8c60)), closes [#26](https://github.com/supergillis/superdash/issues/26) [#31](https://github.com/supergillis/superdash/issues/31)
* **voice:** throw Java exceptions from JNI instead of aborting ([#90](https://github.com/supergillis/superdash/issues/90)) ([8c7bf50](https://github.com/supergillis/superdash/commit/8c7bf50efa6053f23e461f881cdb671da2f1d4fd)), closes [#20](https://github.com/supergillis/superdash/issues/20)

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
