Contributing to Hiyori

Development setup
- JDK 21+
- Use the Gradle Wrapper: `./gradlew`
- Run locally: `./gradlew run`

Code style
- Keep changes focused and minimal.
- Follow existing naming and project structure.

Commits
- Conventional style recommended: `feat:`, `fix:`, `chore:`, `docs:`, etc.
- Small, reviewable commits.

Branching
- `main`: stable branch.
- Use feature branches: `feat/<short-name>`.

Testing
- Prefer manual smoke test for UI changes: `./gradlew run -Dapp.testExitSeconds=3` (headless check).

Packaging
- App image: `./gradlew jpackageImage`
- Installer: `./gradlew jpackage`
- macOS icons: place at `packaging/icons/hiyori.icns`
- Windows icons: place at `packaging/icons/hiyori.ico`

CI overrides
- Pass via Gradle properties or ENV vars:
  - `app.version` / `APP_VERSION` (default `1.0.0`)
  - `app.vendor` / `APP_VENDOR` (default `Hiyori`)
  - `app.macBundleId` / `APP_MACBUNDLEID` (default `dev.hiyori.app`)
  - `app.installerType` / `APP_INSTALLERTYPE` (macOS default `pkg`)

Releasing
- Bump `app.version` via `-Papp.version=X.Y.Z` for packaging.

