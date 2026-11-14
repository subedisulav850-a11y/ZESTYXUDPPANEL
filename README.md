# Zesty Proxy

**Package:** `com.ff.zesty.proxy`  
**Version:** 2.0  
**Credits:** [Zesty](https://t.me/zestyji) · Telegram: @zestyji

---

## Build

### Local (debug)
```bash
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/
```

### Local (release, signed)
1. Generate a keystore:
   ```bash
   keytool -genkey -v -keystore zesty-release.jks \
     -alias zesty -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Create `keystore.properties` at project root (**never commit this**):
   ```
   storeFile=zesty-release.jks
   storePassword=YOUR_STORE_PASS
   keyAlias=zesty
   keyPassword=YOUR_KEY_PASS
   ```
3. Run:
   ```bash
   ./gradlew assembleRelease
   ```

---

## GitHub Actions CI

Workflow: `.github/workflows/build.yml`

| Secret | Purpose |
|--------|---------|
| `KEYSTORE_BASE64` | `base64 -w0 zesty-release.jks` — if absent, release builds with debug signing |
| `KEY_STORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias (`zesty`) |
| `KEY_PASSWORD` | Key password |

Every push to `main`/`master` triggers a build. APKs are uploaded as workflow artifacts (30-day retention).

---

## Server config

Edit `app/src/main/java/com/ff/zesty/proxy/auth/AuthConfig.java`:

| Field | Description |
|-------|-------------|
| `BASE_URL` | VPS address (debug auto-uses `127.0.0.1:8777`) |
| `CERT_PIN` | SPKI pin of your self-signed TLS cert (`sha256/BASE64==`) |
| `SERVER_ED25519_PUB_HEX` | Server signing key hex |

---

*Built by [Zesty](https://t.me/zestyji)*
