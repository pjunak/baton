"""Publish CI's signed APK without replacing a released commit or downgrading latest."""
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE_VERSION = "0.3.7"  # Lets the old semver updater discover this transition once.
CODE_OFFSET = 100_000   # Keep this and ci.yml's run-number sequence stable.


def build_identity(sha, run_number):
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Expected a full commit SHA")
    code = CODE_OFFSET + int(run_number)
    if int(run_number) < 1 or code > 2_100_000_000:
        raise ValueError("Build number is outside Android's range")
    return dict(sha=sha, code=code, version=f"{BASE_VERSION}-commit.{sha[:7]}",
                tag=f"v{BASE_VERSION}-commit.{sha}", file=f"baton-{code}-{sha}.apk")


def released_code(release):
    values = []
    for asset in release.get("assets", []):
        match = re.fullmatch(r"baton-([0-9]+)-[0-9a-f]{40}\.apk", asset["name"])
        if match:
            values.append(int(match[1]))
    return max(values, default=0)  # Historical v0.3.x builds used codes below 100000.


def publish(api, repo, identity, package):
    sha, code, tag = identity["sha"], identity["code"], identity["tag"]
    root = f"/repos/{repo}"
    comparison = api("GET", root + f"/compare/{sha}...main")
    if comparison["status"] not in ("ahead", "identical"):
        raise ValueError("Commit is no longer on main")
    ref = api("GET", root + f"/git/ref/tags/{tag}", missing=True)
    if ref and (ref["object"]["type"] != "commit" or ref["object"]["sha"] != sha):
        raise ValueError("Existing tag points at another object")
    data = package.read_bytes()
    if not 0 < len(data) <= 250 * 1024 * 1024 or not data.startswith(b"PK"):
        raise ValueError("Invalid APK")
    digest = "sha256:" + hashlib.sha256(data).hexdigest()
    release = api("GET", root + f"/releases/tags/{tag}", missing=True)
    latest = api("GET", root + "/releases/latest", missing=True)
    promote = latest is None or code > released_code(latest)
    if not release:
        release = api("POST", root + "/releases", data=dict(
            tag_name=tag, target_commitish=sha, name=f"Baton {BASE_VERSION} · {sha[:7]}",
            draft=True, body=f"Tested commit `{sha}`. Android build `{code}`.\n\n"
            f"SHA-256: `{digest.removeprefix('sha256:')}`.\n\n"
            "Install through Baton Settings → Updates when ready. Your approval is required."
        ))
    assets = release["assets"]
    if assets:
        if len(assets) != 1 or assets[0]["name"] != identity["file"] or assets[0].get("digest") != digest:
            raise ValueError("Released bytes differ; refusing to replace an existing package")
    elif not release["draft"]:
        raise ValueError("Published release has no package; refusing to change it")
    else:
        asset = api("POST", root + f"/releases/{release['id']}/assets?name={identity['file']}",
                    data=data, upload=True)
        if asset.get("digest") != digest or asset.get("name") != identity["file"] or asset.get("state") != "uploaded":
            raise ValueError("GitHub did not confirm the uploaded package")
    if release["draft"]:
        api("PATCH", root + f"/releases/{release['id']}",
            data={"draft": False, "make_latest": "true" if promote else "false"})
    return tag


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def github_api(token):
    opener = urllib.request.build_opener(NoRedirect())
    def api(method, path, data=None, missing=False, upload=False):
        origin = "https://uploads.github.com" if upload else "https://api.github.com"
        headers = {"Authorization": "Bearer " + token, "Accept": "application/vnd.github+json",
                   "X-GitHub-Api-Version": "2026-03-10", "User-Agent": "baton-tested-release"}
        body = data if upload else json.dumps(data).encode() if data is not None else None
        if body is not None:
            headers["Content-Type"] = "application/vnd.android.package-archive" if upload else "application/json"
        request = urllib.request.Request(origin + path, data=body, headers=headers, method=method)
        try:
            with opener.open(request, timeout=120) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if missing and error.code == 404:
                return None
            raise RuntimeError(f"GitHub {method} {path}: HTTP {error.code}") from None
    return api


def main():
    identity = build_identity(os.environ["GITHUB_SHA"], os.environ["GITHUB_RUN_NUMBER"])
    command = sys.argv[1]
    if command == "identity":
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            output.write(f"name={identity['version']}\ncode={identity['code']}\n")
    elif command == "prepare":
        source = Path("app/build/outputs/apk/release")
        metadata = json.loads((source / "output-metadata.json").read_text())
        elements = metadata["elements"]
        if len(elements) != 1 or elements[0]["versionCode"] != identity["code"] or elements[0]["versionName"] != identity["version"]:
            raise ValueError("APK metadata does not match this build")
        target = Path("release-output")
        target.mkdir(exist_ok=True)
        (target / identity["file"]).write_bytes((source / "app-release.apk").read_bytes())
    elif command == "publish":
        if os.environ["GITHUB_REF"] != "refs/heads/main" or os.environ["GITHUB_EVENT_NAME"] != "push":
            raise ValueError("Only successful main-push builds may publish")
        repo = os.environ["GITHUB_REPOSITORY"]
        if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo):
            raise ValueError("Invalid repository")
        package = Path("release-output", identity["file"])
        tag = publish(github_api(os.environ["GITHUB_TOKEN"]), repo, identity, package)
        print(f"PUBLISHED_TESTED_APK: {tag}")
    else:
        raise ValueError("Unknown command")


if __name__ == "__main__":
    main()
