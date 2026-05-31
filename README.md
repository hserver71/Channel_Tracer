# Channel Tracer (Android)

Xtream UI player_api client with stream tracing via curl-like checks.

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
