# Us Location Share

A small, private Android app that lets **two people who both consent** see each
other's live location and recent movement trail on a Google Map. Both phones
install the same app, enter the same secret "pair code," and that's it.

This is the opposite of covert tracking: each person installs it knowingly on
their own phone, a permanent **"Sharing your location"** notification stays in
the status bar the whole time it's on, and either person can hit **Stop
sharing** at any moment.

- **Backend:** Firebase (Realtime Database + Anonymous Auth) — free tier is far
  more than enough for two people.
- **History:** every location point is kept indefinitely. The map draws the most
  recent 500 points per person as a trail.
- **Map:** Google Maps SDK for Android.

---

## What you'll need

1. A **Google account** (for Firebase and the Google Maps API).
2. **Android Studio** installed on a computer (free): https://developer.android.com/studio
3. Both **Android phones** with USB cables, or a way to transfer an APK file.

There are three one-time setup pieces: **Firebase**, a **Google Maps API key**,
and **building the app**. Budget ~30 minutes the first time.

---

## Step 1 — Create the Firebase backend

1. Go to https://console.firebase.google.com and click **Add project**. Give it
   any name (e.g. `us-location-share`). You can disable Google Analytics.
2. In the project, click the **Android** icon to add an Android app.
   - **Android package name:** `com.example.locationsharing` (must match exactly).
   - Register the app, then **download `google-services.json`**.
3. Put that downloaded file at **`app/google-services.json`** in this project
   (the same folder as `app/google-services.json.template`). The build will not
   succeed until this real file is in place.
4. In the Firebase console, open **Build > Authentication > Get started**, and
   on the **Sign-in method** tab enable **Anonymous**.
5. Open **Build > Realtime Database > Create Database**. Pick a location, and
   start in **locked mode**.
6. Go to the database's **Rules** tab and paste in the contents of
   [`firebase-database-rules.json`](firebase-database-rules.json) (just the
   `rules` object), then **Publish**. These rules require sign-in and stop
   either partner from overwriting the other's location.

## Step 2 — Get a Google Maps API key

1. Go to https://console.cloud.google.com and select the **same project** that
   Firebase created (it appears in the project picker).
2. Open **APIs & Services > Library**, search for **Maps SDK for Android**, and
   click **Enable**.
3. Open **APIs & Services > Credentials > Create credentials > API key**. Copy
   the key.
4. (Recommended) Click the key to restrict it: **Application restriction →
   Android apps**, and **API restriction → Maps SDK for Android**. You can add
   the Android restriction after Step 3 when you know your signing certificate's
   SHA-1 (Android Studio shows it under **Gradle > app > Tasks > android >
   signingReport**).
5. Open **`app/src/main/res/values/strings.xml`** and replace
   `PASTE_YOUR_GOOGLE_MAPS_API_KEY_HERE` with your key.

## Step 3 — Build and install on both phones

1. Open **this `location-sharing-app` folder** in Android Studio (**File > Open**).
   Let it finish the Gradle sync — Android Studio downloads the correct Gradle
   version automatically from `gradle/wrapper/gradle-wrapper.properties`.
2. Plug in a phone with **USB debugging** turned on (Settings > About phone >
   tap *Build number* 7 times to unlock Developer options, then enable *USB
   debugging*). Press **Run** (the green ▶) to install and launch.
3. To put it on the **second phone**, either repeat the Run step with that phone
   plugged in, or build a shareable file with **Build > Build Bundle(s) /
   APK(s) > Build APK(s)**, find the `app-debug.apk` it produces, and send it to
   the other phone (the recipient enables "install from unknown sources" when
   prompted).

> The debug APK is signed with Android Studio's auto-generated debug key, which
> is fine for the two of you. For a long-lived install, create your own release
> signing key (**Build > Generate Signed Bundle / APK**) so future updates
> install over the top.

## Step 4 — Pair the two phones

1. On **both** phones, open the app and enter:
   - **Your name** (so each marker is labelled).
   - The **same pair code** on both phones — pick anything you both agree on and
     keep it private, like a shared password. Anyone who enters that code into a
     copy of this app pointed at your Firebase project could see both locations.
2. Tap **Start sharing** and grant location permission. For the map to keep
   updating when the app is closed or the screen is off, choose **Allow all the
   time** when Android asks about background location (you may have to pick it in
   the system Settings screen Android opens).

Each phone now shows both people as coloured markers, with a matching coloured
trail of recent movement. Use **Recenter** to frame both of you, **Hide/Show
trails** to toggle the history lines, and **Stop sharing** to turn it all off.

---

## How the data is stored

```
pairs/{pairCode}/members/{userId}/displayName   -> name
pairs/{pairCode}/members/{userId}/live          -> latest position (overwritten)
pairs/{pairCode}/history/{userId}/{autoId}      -> every position, kept forever
```

Because history is kept indefinitely, it will slowly grow. To wipe it, open the
Realtime Database in the Firebase console and delete the `history` node (or the
whole `pairs` node to reset everything). You can also lower the retained trail
length by changing `TRAIL_POINT_LIMIT` in `LocationRepository.kt`.

## Privacy and consent notes

- This app is meant for **mutual, consensual** sharing between two people who
  each install it on their own phone. Don't install it on someone else's phone
  or use it to track anyone without their knowledge and agreement — that's
  harmful and, in most places, illegal.
- Keep your **pair code** and your **Firebase project** private. Both are what
  stand between your location history and anyone else.
- To stop sharing permanently, tap **Stop sharing**, then uninstall the app and
  delete the data from the Firebase console.

## Project layout

| File | Purpose |
|------|---------|
| `PairingActivity.kt` | First screen: enter name + code, request permissions, start sharing. |
| `MapActivity.kt` | The map: live markers and movement trails for both people. |
| `LocationSharingService.kt` | Foreground service that publishes this phone's location. |
| `LocationRepository.kt` | All Firebase reads and writes. |
| `LocationPoint.kt` | The stored shape of a single position. |
| `PairSettings.kt` | Remembers the pair code and display name between launches. |
| `firebase-database-rules.json` | Security rules to paste into Firebase. |
