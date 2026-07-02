(function () {
    "use strict";

    // Registered as a document-start script for the HA origin, so it
    // fires for both the kiosk shell and the HA iframe. Only the iframe
    // runs HA. Skip at the top level: the shell has no <home-assistant>,
    // no history to trap, no bridge to install.
    if (window.parent === window) {
        return;
    }

    // Two shadow roots. ha-sidebar hides the drawer's slotted content;
    // --ha-sidebar-width: 0 collapses the drawer's reserved space so
    // the panel takes the full viewport. div.header lives in hui-root
    // and needs width: 100% so the dashboard tab bar spans the screen.
    // HA 2026.6 migrated ha-drawer from mwc-drawer (MDC) to wa-drawer
    // (Web Awesome); --mdc-drawer-width is gone. Keep both vars so
    // older HA installs still collapse.
    var MAIN_CSS =
        "ha-sidebar { display: none !important; }" +
        " ha-drawer { --ha-sidebar-width: 0 !important;" +
        " --mdc-drawer-width: 0 !important; }";
    var HUI_CSS = "div.header { width: 100% !important; }";

    function injectStyle(root, id, css) {
        if (!root || root.querySelector("#" + id)) {
            return;
        }
        var style = document.createElement("style");
        style.id = id;
        style.textContent = css;
        root.appendChild(style);
    }

    function injectKioskCss() {
        try {
            var ha = document.querySelector("home-assistant");
            if (!ha || !ha.shadowRoot) {
                return;
            }
            var main = ha.shadowRoot.querySelector("home-assistant-main");
            if (main && main.shadowRoot) {
                injectStyle(main.shadowRoot, "superdash-kiosk-main", MAIN_CSS);
            }
            var lovelace = main && main.shadowRoot
                ? main.shadowRoot.querySelector("ha-drawer ha-panel-lovelace")
                : null;
            var huiRoot = lovelace && lovelace.shadowRoot
                ? lovelace.shadowRoot.querySelector("hui-root")
                : null;
            if (huiRoot && huiRoot.shadowRoot) {
                injectStyle(huiRoot.shadowRoot, "superdash-kiosk-hui", HUI_CSS);
            }
        } catch (e) {
            /* ignore */
        }
    }

    function showOverlay(text, ttl) {
        var existing = document.getElementById("superdash-overlay-msg");
        if (existing) {
            existing.remove();
        }
        var node = document.createElement("div");
        node.id = "superdash-overlay-msg";
        node.textContent = text;
        node.style.cssText =
            "position:fixed;top:24px;left:50%;transform:translateX(-50%);" +
            "background:rgba(0,0,0,0.85);color:#fff;padding:16px 24px;" +
            "border-radius:8px;font:500 18px sans-serif;z-index:99999;";
        document.body.appendChild(node);
        setTimeout(function () {
            if (node.parentNode) {
                node.remove();
            }
        }, ttl);
    }

    // Re-run on every HA mutation. injectStyle is idempotent via the #id
    // check; cheap to call repeatedly. Panels rebuilt on view switch
    // get restyled without manual re-injection.
    injectKioskCss();
    var observer = new MutationObserver(injectKioskCss);
    observer.observe(document.documentElement, {
        childList: true,
        subtree: true,
    });
    window.addEventListener("location-changed", injectKioskCss);

    // SPA dashboard switching via history.pushState bypasses WebViewClient.
    // Trap it here. Full-page nav has its own trap in KioskWebView.kt.
    // window.__superdashPinnedDashboard is seeded by KioskWebView.buildPinScript.
    // Empty pin short-circuits via the isPinnedPath guard. View tabs one
    // segment past the pin are always allowed.
    function isPinnedPath(pathname) {
        var pin = window.__superdashPinnedDashboard;
        if (!pin) {
            return true;
        }
        var prefix = "/" + pin;
        if (pathname === prefix) {
            return true;
        }
        if (pathname.indexOf(prefix + "/") !== 0) {
            return false;
        }
        var tail = pathname.substring(prefix.length + 1);
        return tail.indexOf("/") === -1;
    }

    function pinnedHref() {
        return location.origin + "/" + window.__superdashPinnedDashboard;
    }

    // Last URL seen inside the pinned subtree. Snap-back targets this so the
    // user lands on their actual view, not the dashboard root.
    var lastPinnedHref = null;
    function rememberIfPinned() {
        if (window.__superdashPinnedDashboard && isPinnedPath(location.pathname)) {
            lastPinnedHref = location.href;
        }
    }

    function snapTarget() {
        return lastPinnedHref || pinnedHref();
    }

    rememberIfPinned();
    var origPush = history.pushState;
    var origReplace = history.replaceState;

    history.pushState = function (state, title, url) {
        if (window.__superdashPinnedDashboard && url) {
            try {
                var u = new URL(url, location.href);
                if (!isPinnedPath(u.pathname)) {
                    return origReplace.call(history, state, title, snapTarget());
                }
            } catch (e) {
                /* fall through */
            }
        }
        var result = origPush.apply(history, arguments);
        rememberIfPinned();
        return result;
    };

    history.replaceState = function (state, title, url) {
        if (window.__superdashPinnedDashboard && url) {
            try {
                var u = new URL(url, location.href);
                if (!isPinnedPath(u.pathname)) {
                    return origReplace.call(history, state, title, snapTarget());
                }
            } catch (e) {
                /* fall through */
            }
        }
        var result = origReplace.apply(history, arguments);
        rememberIfPinned();
        return result;
    };

    window.addEventListener("popstate", function () {
        if (window.__superdashPinnedDashboard && !isPinnedPath(location.pathname)) {
            origReplace.call(history, null, "", snapTarget());
        } else {
            rememberIfPinned();
        }
        injectKioskCss();
    });

    // JS bridge installer. The kiosk shell forwards Kotlin's
    // __superdash_init__ message into this iframe; capture the port from
    // it and expose it as window.__superdashBridge.
    window.addEventListener("message", function (e) {
        if (e.data === "__superdash_init__" && e.ports && e.ports[0]) {
            window.__superdashBridge = e.ports[0];
            window.__superdashBridge.onmessage = function (ev) {
                try {
                    var msg = JSON.parse(ev.data);
                    if (!msg || !msg.type) {
                        return;
                    }
                    if (msg.type === "overlay") {
                        showOverlay(msg.text || "", msg.ttlMs || 3000);
                    } else if (msg.type === "reload") {
                        location.reload();
                    } else if (msg.type === "reloadStart") {
                        location.assign(pinnedHref());
                    }
                } catch (err) {
                    /* non-JSON debug payloads — ignore */
                }
            };
            window.__superdashBridge.postMessage(
                JSON.stringify({ type: "ping", src: "js" }),
            );
        }
    });
})();
