## DS Forever Ext — AYN Thor test build

Community fork of [DS Forever](https://github.com/Jreese702/DS-Forever). Not an official upstream release.

- Choose the bundled GPU driver or import a compatible Android Turnip driver ZIP.
- Use Questie instead of the Polestar add-on. Questie supplies quest markers and objectives, not Polestar's step-by-step guide.
- Separate app: **DS Forever Ext**, package `app.dsforever.ext`. Leave the original app installed.
- Fork-only signing key and update channel. No upstream report-worker endpoint.

### Test safely

1. Download the APK and check `SHA256SUMS`.
2. Install it beside DS Forever. Do not uninstall the original app.
3. Select a **copy** of your WoW game folder. A shared folder also shares AddOns and WTF settings.
4. Test the bundled driver first. Then import your compatible driver ZIP from the driver menu.
5. Report the Thor model, driver name, reproduction steps, and symptoms. Never attach login files or credentials.

Built and signature-checked by GitHub Actions. Not yet tested on a physical Thor. A custom GPU driver can crash the game; reset to the bundled default if needed.

Upstream runtime components and credits are retained. Upstream documents unresolved component source/notice gaps in `RELEASE_NOTICES.md`; this test build does not assert those gaps are resolved. Supply your own authorised game installation and account.
