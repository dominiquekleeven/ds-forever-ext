"""Gate the fork APK before publishing; no upstream signing identity is reused."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

from build_release import verified_components, verify_packaged_components, verify_addon_manifest


def main():
    root = Path(__file__).resolve().parent.parent
    components = verified_components(root)
    verify_addon_manifest(root / "companion")
    config = (root / "app/build.gradle.kts").read_text()
    version = re.search(r'versionName = "([^"]+)"', config)[1]
    code = re.search(r'versionCode = (\d+)', config)[1]
    candidates = list((root / "app/build/outputs/apk/modern/release").glob("*.apk"))
    if len(candidates) != 1:
        raise ValueError("Expected exactly one release APK")
    tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/36.1.0"
    apk = candidates[0]
    badging = subprocess.check_output([str(tools / "aapt2"), "dump", "badging", str(apk)], text=True)
    expected = f"package: name='app.dsforever.ext' versionCode='{code}' versionName='{version}'"
    if expected not in badging:
        raise ValueError("Wrong fork package or version")
    signing = subprocess.check_output([str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk)], text=True)
    if "2703b1fd09c7dc3fa45e9e8235771bb6bf78a10d0644d976c3cc8302573c1b3e" not in signing:
        raise ValueError("Unexpected signing certificate: this is not the Ext release key")
    with zipfile.ZipFile(apk) as archive:
        verify_packaged_components(archive, components)
        for library in ("libcasc.so", "libinteriors.so", "libdsfwhisper.so", "libdsf-moonlight.so"):
            if "lib/arm64-v8a/" + library not in archive.namelist():
                raise ValueError("Missing runtime library: " + library)
        if "assets/wow-session-bridge.exe" not in archive.namelist():
            raise ValueError("Missing from-source browser-login helper")
    out = root / "dist"
    out.mkdir(exist_ok=True)
    target = out / f"DS-Forever-{version}.apk"
    shutil.copy2(apk, target)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    (out / "SHA256SUMS").write_text(f"{digest}  {target.name}\n")
    (out / "SIGNATURE.txt").write_text(signing)
    (out / "BUILD.json").write_text(json.dumps({
        "repository": "dominiquekleeven/ds-forever-ext",
        "commit": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
        "package": "app.dsforever.ext", "version": version, "versionCode": int(code),
        "sha256": digest, "bundled_components": components,
        "device_tested": False,
    }, indent=2) + "\n")
    print("Verified", target.name, digest)


if __name__ == "__main__":
    main()
