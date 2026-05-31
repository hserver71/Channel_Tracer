# Channel Tracer (Android)

Xtream UI player_api client with stream tracing via curl-like checks.

## Release (push + build + GitHub APK upload)

```bash
cd /var/www/html/hello-world-apk
export GITHUB_TOKEN=ghp_...
./release.sh              # uses versionName from app/build.gradle
./release.sh v1.1 "Fix stream timeout"
```

Or copy `.env.example` to `.env` and set `GITHUB_TOKEN` there. If your git remote already includes the token (`https://TOKEN@github.com/...`), the script picks it up automatically — no export needed.

The script will:
1. Commit and push changes to `main` (if any)
2. Run `./gradlew assembleDebug`
3. Upload `channel_tracer.apk` to the GitHub Release for the tag (creates the release if missing)

## Features

- **Host / Username / Password** inputs + Reload
- Fetches **live categories** via `player_api.php?action=get_live_categories`
- Tap a category to load **channels** via `get_live_streams&category_id=`
- Tap a channel to fill the **Stream URL** input (`.ts` extension)
- **Start Selected** runs curl-like tests on checked channels or the stream URL field

## Stream URL format

```
{host}/live/{username}/{password}/{stream_id}.ts
```

## Build

```bash
cd /var/www/html/hello-world-apk
export ANDROID_HOME=/opt/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk` or `/var/www/html/hello-world.apk`
