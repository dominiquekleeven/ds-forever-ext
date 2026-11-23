## DS Forever Ext — AYN Thor test build

Community fork of [DS Forever](https://github.com/Jreese702/DS-Forever). Not an official upstream release.

- Choose the bundled GPU driver, Android's system driver, or an imported compatible ARM64 Adrenotools driver ZIP. Turnip is not required in its name: Qualcomm Adreno and other compatible packages are accepted too.
- Existing driver imports and saved selections from Ext.1 remain usable. Ext.2 is a same-key update; do not uninstall Ext.1.
- Use Questie instead of the Polestar add-on. Questie supplies quest markers and objectives, not Polestar's step-by-step guide.
- Questie 12.12.0 downloads from its official release during setup. Questie settings open on the game screen, not as a mirrored bottom-screen panel.
- Separate app: **DS Forever Ext**, package `app.dsforever.ext`. Leave the original app installed.
- Fork-only signing key and update channel. No upstream report-worker endpoint.

### Test safely

1. Download the APK and check `SHA256SUMS`.
2. Install it beside DS Forever. Do not uninstall the original app.
3. Select a **copy** of your WoW game folder. A shared folder also shares AddOns and WTF settings.
4. Test the bundled driver first. Then use **GPU driver → Import driver ZIP** at the bottom-right of the launcher, or choose **Use System driver**. Do not extract the ZIP yourself. Choose **Reset to Bundled default** if it fails.
5. Report the Thor model, driver name, reproduction steps, and symptoms. Never attach login files or credentials.

Driver ZIPs need Adrenotools `meta.json` and an ARM64 shared library. Windows/Linux installers, arbitrary Mesa source archives and drivers for incompatible GPUs are not supported. An accepted ZIP is not proof of hardware compatibility. System selection does not replace or modify Android's driver.

Built and signature-checked by GitHub Actions. Not yet tested on a physical Thor. An imported or system GPU driver can crash the game; reset to the bundled default if needed.

The named `Polestar` and `Polestar_Nav` folders, plus recorded installer-managed Polestar folders, move into `Interface/AddOns-disabled-dsforever` without deletion. SavedVariables and other user add-ons remain. To restore Polestar, close WoW and move the reviewed folders back into `Interface/AddOns` without overwriting existing folders. Do not use both apps against the same game folder.

Upstream runtime components and credits are retained. Upstream documents unresolved component source/notice gaps in `RELEASE_NOTICES.md`; this test build does not assert those gaps are resolved. Supply your own authorised game installation and account.
