# Publishing to JetBrains Marketplace

## One-time setup
1. Create a vendor/account at https://plugins.jetbrains.com and generate a permanent token (Profile > Tokens).
2. Generate a signing key pair (https://plugins.jetbrains.com/docs/intellij/plugin-signing.html):
   `openssl genpkey -aes-256-cbc -algorithm RSA -out private_encrypted.pem -pkeyopt rsa_keygen_bits:4096`
   then `openssl rsa -in private_encrypted.pem -out private.pem`
   then `openssl req -key private.pem -new -x509 -days 365 -out chain.crt`.
3. Keep the keys outside the repo and export them as environment variables:
   - `CERTIFICATE_CHAIN` (contents of chain.crt)
   - `PRIVATE_KEY` (contents of private.pem)
   - `PRIVATE_KEY_PASSWORD`
   - `PUBLISH_TOKEN`

## Each release
1. Bump `version` in `build.gradle.kts`; update `docs/RELEASE_NOTES.md` and the `<change-notes>` in `plugin.xml`.
2. `.\gradlew.bat test verifyPlugin` (tests and verifies against IntelliJ IDEA 2026.2.3, the minimum supported release).
3. `.\gradlew.bat signPlugin` creates `build/distributions/intellij-jsp-<version>-signed.zip`.
4. First release: upload the zip manually at https://plugins.jetbrains.com/plugin/add (needs moderation approval).
   Later releases: `.\gradlew.bat publishPlugin`.

## Marketplace checklist
- Plugin ID `com.rajendarreddyj.intellij.jsp`, `pluginIcon.svg` present, MIT `LICENSE` present.
- Marked incompatible with IntelliJ IDEA Ultimate (it bundles its own JSP support).
- Optional: add `url=` to `<vendor>`, screenshots and a source/issue-tracker link on the listing page.
