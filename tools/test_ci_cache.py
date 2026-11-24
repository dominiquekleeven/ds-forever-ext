"""Exercise download-cache trust boundaries with tiny fixtures, not Android builds."""
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
COMPONENTS = ("proton-11.0-90624-arm64ec.wcp", "dxvk-2.4.1-wow-aarch64-test.wcp",
              "turnip-wow-scheduler-test.zip", "turnip-V32-RP6sched.zip")


class DownloadCacheTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="ci-cache-",
                                                dir=os.environ.get("RUNNER_TEMP", "/tmp/opencode"))
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "tools").mkdir()
        (self.root / "bin").mkdir()
        self.remote = self.root / "remote"
        self.remote.mkdir()
        self.env = dict(os.environ, ANDROID_HOME="/unused-fixture-sdk",
                        PATH=f"{self.root / 'bin'}:{os.environ['PATH']}",
                        FIXTURE_REMOTE=str(self.remote), FIXTURE_LOG=str(self.root / "downloads"))
        # The real fetch scripts still select URLs, copy cache hits and check hashes.
        self.write("bin/curl", '#!/bin/bash\nset -eu\n'
                   'while [ "$1" != "-o" ]; do shift; done\n'
                   'out="$2"; url="$3"\n'
                   'echo "$url" >> "$FIXTURE_LOG"\n'
                   'cp "$FIXTURE_REMOTE/${url##*/}" "$out"\n', executable=True)

    def write(self, name, text, executable=False):
        p = self.root / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text)
        if executable:
            p.chmod(0o700)
        return p

    def run_script(self, path, *args, success=True):
        result = subprocess.run(["bash", str(path), *map(str, args)], cwd=self.root,
                                env=self.env, capture_output=True, text=True)
        self.assertEqual(result.returncode == 0, success, result.stdout + result.stderr)
        return result

    def test_components_miss_hit_and_corrupt_hit(self):
        shutil.copyfile(ROOT / "tools/fetch_components.sh", self.root / "tools/fetch_components.sh")
        hashes = []
        for name in COMPONENTS:
            content = f"original fixture {name}".encode()
            (self.remote / name).write_bytes(content)
            hashes.append(f"{hashlib.sha256(content).hexdigest()}  {name}\n")
        self.write("tools/components.sha256", "".join(hashes))
        # Run the actual bootstrap download/cache section, before any native work.
        bootstrap = (ROOT / "tools/ci_bootstrap.sh").read_text().split(
            "# Match upstream's deterministic runtime recipe", 1)[0]
        script = self.write("tools/ci_bootstrap.sh", bootstrap)
        self.run_script(script)
        log = self.root / "downloads"
        self.assertEqual(len(log.read_text().splitlines()), 4)
        cache = self.root / ".bootstrap/component-downloads"
        assets = self.root / "app/src/main/assets/bundled_components"
        for name in COMPONENTS:
            self.assertEqual((cache / name).read_bytes(), (self.remote / name).read_bytes())
        # Model a trimmed output, then a fresh checkout with only raw cached downloads.
        (assets / COMPONENTS[0]).write_bytes(b"trimmed output must not enter cache")
        self.assertEqual((cache / COMPONENTS[0]).read_bytes(), (self.remote / COMPONENTS[0]).read_bytes())
        shutil.rmtree(assets)
        self.run_script(script)
        self.assertEqual(len(log.read_text().splitlines()), 4, "cache hit downloaded inputs")
        shutil.rmtree(assets)
        (cache / COMPONENTS[0]).write_bytes(b"corrupt cache")
        self.run_script(script, success=False)
        self.assertEqual(len(log.read_text().splitlines()), 4)

    def test_bridge_archive_miss_hit_and_corrupt_hit(self):
        name = "llvm-mingw-20261104-ucrt-ubuntu-22.04-x86_64"
        compiler = self.write(f"remote/{name}/bin/aarch64-w64-mingw32-clang", "#!/bin/sh\nexit 0\n", True)
        archive = self.remote / f"{name}.tar.xz"
        with tarfile.open(archive, "w:xz") as tar:
            tar.add(compiler.parent.parent, arcname=name)
        fixture_hash = hashlib.sha256(archive.read_bytes()).hexdigest()
        source = (ROOT / "tools/wow-session-bridge/build_dsf.sh").read_text().replace(
            "919380403de87dca3c581c2053230cd298af5e891286628ba0613b7fed370926", fixture_hash)
        script = self.write("tools/wow-session-bridge/build_dsf.sh", source)
        self.write("tools/wow-session-bridge/build.sh", 'printf fixture-bridge > "$2"\n')
        self.write("tools/wow-session-bridge/wow-session-bridge.sha256",
                   hashlib.sha256(b"fixture-bridge").hexdigest() + "  wow-session-bridge.exe\n")
        (self.root / "app/src/main/assets").mkdir(parents=True)
        toolchain = self.root / ".bootstrap/bridge-toolchain"
        self.run_script(script, toolchain)
        self.assertEqual(len((self.root / "downloads").read_text().splitlines()), 1)
        # Actions caches only the archive, never the extracted compiler directory.
        shutil.rmtree(toolchain / name)
        self.run_script(script, toolchain)
        self.assertEqual(len((self.root / "downloads").read_text().splitlines()), 1)
        shutil.rmtree(toolchain / name)
        (toolchain / archive.name).write_bytes(b"corrupt cache")
        self.run_script(script, toolchain, success=False)
        self.assertFalse((toolchain / name).exists(), "extracted an unverified archive")

    def test_each_recipe_or_pin_change_invalidates_download_key(self):
        workflow = (ROOT / ".github/workflows/android-release.yml").read_text()
        inputs = re.findall(r"'([^']+)'", re.search(r"hashFiles\(([^)]+)\)", workflow)[1])
        # Model hashFiles' content-sensitive aggregation with small copies of its exact inputs.
        for name in inputs:
            self.write(name, (ROOT / name).read_text())

        def digest():
            return hashlib.sha256(b"".join(hashlib.sha256((self.root / name).read_bytes()).digest()
                                          for name in sorted(inputs))).hexdigest()

        baseline = digest()
        for name in inputs:
            path = self.root / name
            original = path.read_bytes()
            path.write_bytes(original + b"\nchanged fixture input\n")
            self.assertNotEqual(digest(), baseline, name)
            path.write_bytes(original)
        self.assertEqual(digest(), baseline)


if __name__ == "__main__":
    unittest.main()
