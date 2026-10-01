# BOOX Firefox Scroll

A tiny Android accessibility helper for BOOX devices with physical page-turn buttons.
It intercepts Volume Up/Down or Page Up/Down **only while Firefox is foregrounded** and injects a vertical swipe gesture.

## Why this approach

BOOX firmware can map the side buttons differently for each app. Firefox does not always turn BOOX button input into webpage scrolling consistently, especially across firmware/browser changes. Setting Firefox's BOOX button behavior to **Volume** gives this helper a predictable hardware key event, and the helper converts it to a normal touchscreen swipe.

## BOOX setup

1. Install the APK.
2. Open **BOOX Firefox Scroll**.
3. Tap **Enable accessibility service** and enable **BOOX Firefox Button Scroll**.
4. Back on the BOOX launcher, long-press **Firefox**.
5. Open **Optimize** → **Others**.
6. Under **Customize Buttons**, choose **Volume**.
7. Open Firefox and press the physical buttons.

Default behavior:
- Top / Volume Up / Page Up → scroll upward.
- Bottom / Volume Down / Page Down → scroll downward.
- Long presses do not rapidly fly through the page.
- Outside Firefox, the buttons are not intercepted.

Use **Reverse top/bottom button direction** if BOOX reports the buttons opposite to your preferred orientation.
Use the slider to change the vertical distance per press (45–90%).

## Supported Firefox packages

- Firefox Release: `org.mozilla.firefox`
- Firefox Beta: `org.mozilla.firefox_beta`
- Firefox Nightly / Fenix: `org.mozilla.fenix`
- Firefox Focus: `org.mozilla.focus`
- Firefox Klar: `org.mozilla.klar`

## Permissions / privacy

The service uses Android Accessibility because Android only allows an accessibility service to globally filter supported hardware key events and inject touchscreen gestures without root. The project does **not** request the `INTERNET` permission and does not store webpage text, URLs, or history.

## Build with GitHub Actions

Open **Actions → Build APK → Run workflow**. When the build finishes, download the **BooxFirefoxScroll-debug** artifact and unzip it. The APK inside is `app-debug.apk`.

## Troubleshooting

- If the buttons still change volume, confirm Firefox's BOOX **Customize Buttons** setting is **Volume** and confirm the accessibility service is enabled.
- If nothing is detected, try BOOX **Page Turn** mode; the service also listens for Android Page Up/Page Down key events.
- The app's home screen shows the **last recognized Firefox button**.
- If another accessibility/remapping app is intercepting the buttons, temporarily disable its key remapping and test again.
