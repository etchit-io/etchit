# Vendored `com.reown:android-core` (patched)

The AAR under `com/reown/android-core/1.6.12/` is a patched build of
Reown Kotlin 1.6.12's `core/android` module. The upstream release has a
thread-safety bug in `ChaChaPolyCodec` (the BouncyCastle
`ChaCha20Poly1305` cipher is not safe for concurrent reuse, and
concurrent WalletConnect relay payloads trip
`IllegalStateException: ChaCha20Poly1305 cannot be reused for encryption`).

The patch constructs a fresh cipher per call — the object is a tiny POJO
so per-call allocation is cheap.

## Source

The patch lives on a public fork:

- Repo: <https://github.com/etchit-io/reown-kotlin>
- Tag: `1.6.12-etchit.1`
- Branch: `etchit-patch`
- Patched file: `core/android/src/main/kotlin/com/reown/android/internal/common/crypto/codec/ChaChaPolyCodec.kt`

Unpatched coordinates (upstream Maven Central) remain at
`com.reown:android-core:1.6.12`. The `settings.gradle.kts` file-based
Maven repository makes Gradle resolve this directory first, so the
vendored AAR takes priority over Maven Central for that exact
groupId:artifactId:version.

## Rebuilding the AAR

```
git clone https://github.com/etchit-io/reown-kotlin.git
cd reown-kotlin
git checkout 1.6.12-etchit.1
./gradlew :core:android:publishToMavenLocal
cp ~/.m2/repository/com/reown/android-core/1.6.12/android-core-1.6.12.* \
   <etchit-android-v2>/local-maven/com/reown/android-core/1.6.12/
```

## License

Reown Kotlin is distributed under the WalletConnect Community License
(see <https://github.com/reown-com/reown-kotlin/blob/master/LICENSE>).
Redistribution of the AAR in this form is permitted by that license.
