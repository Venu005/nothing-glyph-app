# Backlit — Play Console answers

Copy these into Play Console → **App content** (and **Store settings**). They describe the app as of the release-prep
branch. Update them if a feature starts sending data anywhere.

## Privacy policy
- URL: `https://venu005.github.io/nothing-glyph-app/privacy-policy` (GitHub Pages, from `docs/privacy-policy.md`)

## App access
- **All functionality is available without special access.** No login.
- Note for reviewers: "Backlit drives the Glyph Matrix on the Nothing Phone (3) and Phone (4a) Pro. On other phones the
  app runs in preview mode: every screen works and shows the matrix on screen."

## Ads
- **No**, the app does not contain ads.

## Content rating (IARC questionnaire)
- Category: **All other app types** (utility / personalization).
- Violence, fear, sexuality, language, controlled substances, gambling: **No** to all.
- Users can interact or exchange content with other users: **No**.
- Shares the user's current physical location with other users: **No**.
- Allows users to purchase digital goods: **No**.
- Unrestricted internet access / web browsing: **No**.

## Target audience and content
- Target age group: **13–15, 16–17, 18 and over** (not designed for children; avoids the Families policy).
- Appeals to children: **No**.

## News app / Government app / Financial features / Health
- **No** to all.

## Data safety
Backlit processes everything on the phone and has no network access, so under Google's definitions it **collects
no data and shares no data**.

- Does your app collect or share any of the required user data types? **No.**
- (The form then skips the per-type questions.)
- Is all of the user data collected by your app encrypted in transit? Not applicable (no data leaves the device).
- Do you provide a way for users to request that their data is deleted? Not applicable. All data is local and is removed
  by uninstalling (stated in the privacy policy).

Why "no" is accurate, permission by permission:

| Permission | What it's used for | Leaves the phone? |
|---|---|---|
| `ACCESS_COARSE_LOCATION` | sunrise/sunset for the Day ring clock, rounded to ~1 km | No |
| `RECORD_AUDIO` | reading the phone's own playback output (Visualizer) for the Music toy | No |
| Notification listener | matching incoming/missed-call notifications to chosen contacts | No |
| `BLUETOOTH_CONNECT` | noticing chosen paired devices connecting | No |
| `SCHEDULE_EXACT_ALARM` | optional on-time sand timer alarm | No data |
| `RECEIVE_BOOT_COMPLETED`, `VIBRATE` | re-arming the timer after reboot; the done buzz | No data |
| Nothing `ENABLE` | drawing on the Glyph Matrix | No data |

## Sensitive permissions and APIs
- No restricted permissions are declared (no SMS/Call log, no all-files access, no background location, no
  `USE_EXACT_ALARM`, no accessibility service, no foreground service). No Permissions Declaration form is needed.
- In-app disclosures shown **before** each request:
  - Microphone: the Music page's card explains that only the phone's own playback is read and nothing is recorded.
  - Notification access: Alerts shows a "Notification access" sheet (what's read, what's saved, what's never sent)
    with CONTINUE TO SETTINGS / NOT NOW.
  - Nearby devices: Alerts shows a "Nearby devices" sheet before the permission prompt.
  - Location: Sun times shows "Read on your phone only, rounded to about 1 km, and never sent anywhere."

## Store settings
- App category: **Personalization**.
- Contact email: venusaiyalamanchili@gmail.com.
- Price: **Free**. Contains ads: **No**. In-app purchases: **No**.

## Testing track (new personal developer account)
New personal accounts must run a **closed test with at least 12 testers for 14 continuous days** before applying for
production access. Upload the AAB to a closed testing track first, add the testers' Google accounts (or a Google Group),
and keep at least 12 opted in for 14 days.
