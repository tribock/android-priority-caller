# Play Store publishing plan

## 1. Prerequisites

- A Google Play Console developer account ($25 one-time fee): https://play.google.com/console/signup
- A privacy policy hosted at a public URL (see `PRIVACY_POLICY.md` in this repo —
  publish it via GitHub Pages, or any static host, and fill in the `[TODO]` fields first)
- A release signing keystore (see below)

## 2. Create a release keystore (do this yourself — do not share or commit it)

```
keytool -genkeypair -v -keystore priority-caller-release.jks \
  -alias priority-caller -keyalg RSA -keysize 2048 -validity 10000
```

Store the resulting `.jks` file and its passwords somewhere safe outside the repo
(e.g. a password manager). **If you lose this keystore you can never publish an
update to the same app listing again.** Never commit it — add it to `.gitignore`.

Wire it into `app/build.gradle.kts` as a release `signingConfig` (Android Studio's
**Build → Generate Signed App Bundle** wizard can do this for you interactively
instead of hand-editing Gradle).

## 3. Build the release bundle

Play Store requires the Android App Bundle format, not a raw APK:

```
./gradlew bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`

## 4. Store listing content

**App name** (≤30 chars): `Priority Caller`

**Short description** (≤80 chars):
> Ring loudly through Do Not Disturb when your priority contacts call.

**Full description** (≤4000 chars):

> Priority Caller makes sure you never miss a call from the people who matter
> most — even with Do Not Disturb on.
>
> Pick one or more priority contacts. When any of them calls, Priority Caller
> rings your phone at full volume through an alarm-style alert that bypasses Do
> Not Disturb, silent mode, and battery-saver call restrictions — the same way
> your alarm clock is never silenced.
>
> How it works:
> • Uses Android's built-in Call Screening role to check each incoming call
>   against your priority list — no calls are ever blocked, rejected, or logged
>   anywhere else.
> • Uses Do Not Disturb access only to let the priority alert sound through DND.
> • Runs a short-lived foreground service, shown as a notification, only while a
>   priority call is ringing.
>
> Priority Caller collects no data, shows no ads, and makes no network requests.
> Everything is stored locally on your device. See the full privacy policy at
> [your hosted privacy policy URL].

**Category**: Tools (or Communication)

**Contact email**: [TODO]

**Privacy policy URL**: [TODO — hosted URL for `PRIVACY_POLICY.md`]

## 5. Data safety form (Play Console → App content → Data safety)

| Data type | Collected | Shared | Purpose | Optional |
|---|---|---|---|---|
| Contacts (name, phone number) | Yes | No | App functionality | Yes |

Answer "No" to data being encrypted in transit (nothing is transmitted) and "Yes"
to users being able to request deletion (remove the contact in-app, or uninstall).

## 6. Sensitive permissions declaration (Play Console → App content → Permissions declaration)

For the "Phone" / "Call Log" permission group, select the use case
**"Caller ID or call-blocking/spam-detection"** and describe:

> The app's sole purpose is call screening: it registers for Android's
> CALL_SCREENING role and checks each incoming call's number against a
> user-maintained list of priority contacts to trigger a loud ringtone alert.
> No calls are blocked or rejected; nothing is transmitted off-device.

## 7. Assets checklist (not included in this repo — produce these yourself)

- [ ] App icon, 512×512 PNG (hi-res export of the existing adaptive icon)
- [ ] Feature graphic, 1024×500 PNG/JPG
- [ ] At least 2 phone screenshots (from the redesigned `activity_main.xml` UI)
- [ ] Content rating questionnaire completed in Play Console (expect "Everyone")
- [ ] Target audience & content settings completed (not for children)

## 8. Submit

Play Console → your app → **Production** → create a new release, upload the
`.aab`, fill in the release notes, and submit for review. First-time reviews for
apps requesting restricted permissions can take several days and may come back
with follow-up questions — respond with the same justification as step 6.
