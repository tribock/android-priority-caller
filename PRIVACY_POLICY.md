# Privacy Policy for Priority Caller

**Last updated: 2026-08-13**

Priority Caller ("the app") is developed by Louis Kern.
This policy explains what data the app accesses and what it does with it.

## Data the app accesses

| Data | Why | Leaves the device? |
|---|---|---|
| Contact name and phone number(s) you explicitly pick via the "Add priority contact" screen | To recognize incoming calls from that contact | No |
| The phone number of an incoming call | To compare it against your saved priority contacts | No |
| Call state (ringing / answered / ended) | To know when to stop the priority ringtone | No |

The app does **not** collect, store, or transmit any data to the developer or to
any third party. There are no analytics SDKs, ad SDKs, or network requests of any
kind in the app — everything above is processed and stored only in the app's
local storage (Android `SharedPreferences`) on your device.

## Permissions used

- **Contacts** — to let you pick a priority contact from your address book via
  Android's standard contact picker.
- **Phone state** — to detect when a ringing call from a priority contact is
  answered or ends, so the alert can stop.
- **Call Screening role** — the system-provided mechanism the app uses to check
  each incoming call's number against your saved priority list. The app never
  blocks, rejects, or redirects any call.
- **Do Not Disturb access** — to let the priority ringtone sound even while Do
  Not Disturb is on, the same way alarms are never silenced.
- **Notifications** — to show the required ongoing notification while a priority
  call is ringing (Android requires this for any foreground service).
- **Full-screen intent** — to show the priority-call alert even if the screen is
  off or the device is locked.

## Data retention and deletion

Priority contacts you add are stored only on your device. You can remove any
contact from within the app at any time, or remove all data at once by
uninstalling the app.

## Children's privacy

The app is not directed at children and does not knowingly collect data from
anyone.

## Changes to this policy

If this policy changes, the updated version will be posted at this same URL with
a new "Last updated" date.

## Contact

Questions about this policy: louis.baumann93@gmail.com
