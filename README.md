# SmartTube Local

Personal SmartTube fork for anonymous YouTube access with local-only profiles instead of Google accounts.

## Features

- local profiles, subscriptions, and playlists
- local-profile password protection
- anonymous-intended YouTube network access
- Google account/authentication UI removed from the intended product path
- Google Takeout playlist import
- subscription import from NewPipe-compatible JSON
- blue SmartTube launcher icon to distinguish it from mainline SmartTube

## Current Build

The current test build is **SmartTube Local 32.52**.

Local profiles, passwords, subscriptions, playlists, Google Takeout playlist import, and subscription import have all been tested successfully.

Local avatars are not currently implemented.

**New profiles:** creating a profile for the first time may briefly produce a `token missing` error. Wait for the error to disappear, then retry.

## Download

Download the current universal APK from [GitHub Releases](https://github.com/BobbyLLM/SmartTube/releases/download/v32.52-local/SmartTube_Local_32.52_universal.apk).

## Importing from Google Takeout

Google Takeout can be used to restore both playlists and subscriptions, but the two use different import paths.

### Playlists

On your PC:

1. Sign in to your Google account and open [Google Takeout](https://takeout.google.com/).
2. Click **Deselect all**.
3. Enable **YouTube and YouTube Music**.
4. Click **All YouTube data included**.
5. Deselect everything, then select **playlists only**.
6. Make sure **videos** is NOT selected, otherwise Google may include uploaded video files and make the export much larger.
7. Click **OK**, then **Next step**.
8. Choose **Send download link via email**, **Export once**, and **.zip**.
9. Create the export and download the ZIP when Google has prepared it.

Then:

1. Copy the original Takeout ZIP to your Android TV/device.
2. In SmartTube Local, open **Settings → Backup & Restore → Import Google Takeout playlists**.
3. Select the original ZIP. Do not extract it first.
4. Wait for the import to complete.

Large libraries can take several minutes because SmartTube resolves video metadata during import. An import containing roughly 1,700 unique videos took about five minutes in testing and appeared to be doing nothing during much of that time.

**Fresh profiles:** if playlist import does not work on a newly created local profile, first create a small local playlist manually, then retry the Takeout import. This may be required to initialise local playlist storage.

### Subscriptions

SmartTube Local imports subscriptions from a **NewPipe-compatible JSON export**.

For Google Takeout subscriptions, the tested workflow is:

1. Export your YouTube data from [Google Takeout](https://takeout.google.com/).
2. Import the Takeout export into **PipePipe** or **NewPipe**.
3. Export your subscriptions from PipePipe/NewPipe as a NewPipe-compatible `.json` file.
4. Copy that JSON file to:

   `Android/media/smarttube`

5. In SmartTube Local, open **Settings → Backup & Restore → Import subscriptions group (GrayJay/PocketTube/NewPipe)**.
6. Select the exported JSON.
7. Wait for the import to complete.

On some Android TV devices, SmartTube's current file picker may not expose files stored in ordinary locations such as `Download`. `Android/media/smarttube` is known to work.

Subscription import can also appear idle for several minutes while SmartTube resolves channel metadata. A real import of 116 subscriptions completed successfully, including channel names and icons.

Imported subscriptions belong to the active SmartTube Local profile. No Google account sign-in is required.

## Repository Notes

This repository is maintained for *private* use, but you are welcome to use it.

Pull requests are not accepted.  
No support or compatibility guarantee is provided.

## Based on SmartTube

https://github.com/yuliskov/SmartTube