# Glyph Light Lab

Can an app control the **Nothing Phone (3a) Lite**'s single Glyph Light?

The (3a) Lite isn't on the list of phones supported by Nothing's [Glyph Developer Kit](https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit), so this small test app tries every route we know of and logs what happened:

1. **Nothing's Glyph toolkit**: registers as each supported Nothing phone in turn and lights (or breathes) the first light.
2. **Android's notification light**: posts a notification asking for a blinking red light, the way classic LED phones worked.
3. **The phone itself**: model codes, whether Nothing's Glyph service is installed, Glyph debug mode, and any LEDs the system lists.

If one of these lights the LED, a real customisation app can be built on top of it: per-app blink patterns, a pattern editor, charging pulses and more.

## Try it

1. Download `glyph-light-lab.apk` from the [**Test build** release](../../releases/tag/test-build) and install it.
2. For the Glyph toolkit tests, Nothing requires debug mode. With USB debugging on and the phone connected to a computer:
   ```
   adb shell settings put global nt_glyph_interface_debug_enable 1
   ```
   It switches itself off after 48 hours.
3. Open the app. Tap **Light** and **Breathe** on each row and watch the back of the phone. Then try **Blink with a notification** and lock the screen within 5 seconds.
4. Tap **Copy log** and share it, along with which buttons lit the light.

## Build

GitHub Actions builds the APK on every push and publishes it as the **Test build** release. Locally: open the project in Android Studio, or run `gradle :app:assembleDebug` with Gradle 8.13 and JDK 17.

Uses Nothing's Glyph Developer Kit (`app/libs/glyph-matrix-sdk-2.0.aar`) from Nothing's public repository.
