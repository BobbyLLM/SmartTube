# SmartTube Local

Personal SmartTube fork for anonymous YouTube access with local-only profiles instead of Google accounts.

## Implemented

- local profiles, subscriptions, and playlists
- anonymous-intended YouTube network access
- blue SmartTube launcher icon (to make it distinct from mainline install)
- Google account/authentication UI removed from the intended product path
- Import of Youtube playlists via Google Takeout ZIP 

## Current Build

The current test build is SmartTube 32.52. Google Takeout playlist import is now working, alongside local playlists and local profiles. All other tested functionality appears to behave as expected relative to mainline SmartTube.

## Current Status

- Local-profile password protection (DONE)
- Playlist import (DONE)
- Local avatars (NOT DONE / very minor issue).

Note: creating profile for first time may result in "token missing" error. Simply wait for error to disappear and retry. 

## Download

Download the current universal APK from [GitHub Releases](https://github.com/BobbyLLM/SmartTube/releases/download/v32.52-local/SmartTube_Local_32.52_universal.apk).

## Playlist Import

EDIT: Regarding playlist import, the suggested pathway is:

- on your PC, sign into your Google account
- go to [Google Takeout](https://takeout.google.com/)
- click **Deselect all**
- scroll down and enable **YouTube and YouTube Music**
- click **All YouTube data included**
- deselect everything inside that section, then select **playlists only**
- make sure **videos** is NOT selected, otherwise Google may include your uploaded video files and make the export much larger
- click **OK**
- scroll to the bottom and click **Next step**
- choose **Send download link via email**
- choose **Export once**
- choose **.zip**
- create the export and wait for Google to prepare it
- download the ZIP Google provides
- copy the ZIP to your Android TV/device
- in SmartTube Local, go to **Settings → Backup & Restore → Import Google Takeout playlists**
- select the original Google Takeout ZIP; do not extract it first
- wait for the import to complete

**Note:** large libraries can take several minutes to populate because SmartTube resolves video metadata before writing the playlists. As a rough example, an import containing around 1,700 unique videos took about five minutes on my setup. During that time it may look like nothing is happening, so give it a few minutes before assuming the import has failed.

## Repository Notes

This repository is maintained for *private* use but you are WELCOME to use it :)

Pull requests are not accepted.  
No support or compatibility guarantee is provided.

## Based on SmartTube

https://github.com/yuliskov/SmartTube