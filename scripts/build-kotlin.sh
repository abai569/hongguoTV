#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -z "${JAVA_HOME:-}" ]]; then
  for native_jdk in "$HOME"/.cache/hongguotv/toolchain/jdk-17*/Contents/Home; do
    if [[ -x "$native_jdk/bin/java" ]]; then export JAVA_HOME="$native_jdk"; break; fi
  done
fi
if [[ -z "${ANDROID_HOME:-}" && -d "$HOME/.cache/hongguotv/android-sdk" ]]; then
  export ANDROID_HOME="$HOME/.cache/hongguotv/android-sdk"
fi
"$repo_dir/kotlin-tv/gradlew" -p "$repo_dir/kotlin-tv" :core:test :app:lintTvRelease :app:lintPhoneRelease :app:assembleRelease --console=plain
mkdir -p "$repo_dir/outputs"
native_version="${HONGGUOTV_VERSION_NAME:-$(sed -n 's/.*versionName = .*?: "\([^"]*\)".*/\1/p' "$repo_dir/kotlin-tv/app/build.gradle.kts")}"
[[ "$native_version" =~ ^[0-9][0-9A-Za-z.+-]*$ ]] || { echo "Invalid APK version" >&2; exit 1; }
tv_apk="$repo_dir/outputs/hongguotv-kotlin-$native_version-android8.apk"
phone_apk="$repo_dir/outputs/hongguotv-$native_version-v8a.apk"
cp "$repo_dir/kotlin-tv/app/build/outputs/apk/tv/release/app-tv-release.apk" "$tv_apk"
cp "$repo_dir/kotlin-tv/app/build/outputs/apk/phone/release/app-phone-release.apk" "$phone_apk"
if command -v shasum >/dev/null; then shasum -a 256 "$tv_apk" "$phone_apk"; else sha256sum "$tv_apk" "$phone_apk"; fi
