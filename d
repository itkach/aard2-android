#!/usr/bin/env python3
"""aard2-android developer front door.

    ./d <command> [args]      run one maintenance task
    ./d                       list the commands

The git-heavy tasks (recording dependency revisions, cutting a release, enabling
the hooks) are implemented here directly; the asset generators stay in scripts/
and are invoked as subprocesses.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path

# This file lives at the repo root, so its directory is the root. Resolved (not
# the caller's cwd) so `./d <cmd>` works from anywhere.
ROOT = Path(__file__).resolve().parent
SCRIPTS = ROOT / "scripts"

# The source dependencies, leaf first (slobj, then slobber which depends on it).
# Built from sibling checkouts via includeFlat, so their revisions aren't
# captured by the build; record-deps snapshots them into source-deps.json.
DEPS = ["slobj", "slobber"]


def git(
    args: list[str],
    *,
    cwd: Path,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    """Run git in `cwd`, capturing text output."""
    return subprocess.run(
        ["git", *args],
        cwd=cwd,
        check=check,
        capture_output=True,
        text=True,
    )


def is_git_checkout(path: Path) -> bool:
    # is_dir() first: git() runs with cwd=path, and subprocess raises
    # FileNotFoundError before git even starts if the directory is missing - which
    # is exactly the fresh-clone / packager case record-deps must no-op on.
    if not path.is_dir():
        return False
    # Require path to be a repo's own top level, not merely somewhere inside one -
    # otherwise a plain folder nested in an outer repo (e.g. a home dir that is a
    # dotfiles checkout) would pass, and we'd record that outer repo's HEAD as the
    # dependency's. --show-toplevel is the folder itself for a worktree or
    # submodule too, so those still qualify.
    r = git(["rev-parse", "--show-toplevel"], cwd=path, check=False)
    return r.returncode == 0 and Path(r.stdout.strip()).resolve() == path.resolve()


def dep_dir(name: str) -> Path:
    return ROOT.parent / name


def record_deps(
    require_clean: bool = False,
    require_pushed: bool = False,
    require_present: bool = False,
) -> int:
    """Snapshot each dependency's revision into source-deps.json.

    No-ops (leaving any existing file untouched) unless every dependency is a git
    checkout - e.g. a fresh clone, CI, or a packager's machine - so it never
    blocks there. A release is the exception: `require_present` makes a missing
    checkout a hard error, so a release can't be tagged with a stale, un-updated
    source-deps.json. The JSON is deterministic (fixed order, no timestamp) so
    unchanged deps produce a byte-identical file, and is written atomically so a
    git failure partway can't leave a truncated file.
    """
    out = ROOT / "source-deps.json"

    # Detect each dependency via git itself, so a worktree or submodule (whose
    # .git is a file, not a directory) is recognized rather than silently skipped.
    for name in DEPS:
        if not is_git_checkout(dep_dir(name)):
            if require_present:
                sys.exit(
                    f"record-deps: ../{name} is not a git checkout; its revision can't "
                    "be recorded for a release"
                )
            print(
                f"record-deps: ../{name} is not a git checkout, leaving {out} untouched",
                file=sys.stderr,
            )
            return 0

    # Refresh cached stat info first, so a tracked file whose mtime changed but
    # whose content didn't (a checkout round-trip, a no-op save) isn't misreported.
    for name in DEPS:
        git(["update-index", "-q", "--refresh"], cwd=dep_dir(name), check=False)

    if require_clean:
        dirty: list[str] = []
        for name in DEPS:
            # --porcelain covers staged, unstaged and untracked-but-not-ignored
            # files - the last matters because Gradle compiles a new, un-added
            # source too.
            result = git(["status", "--porcelain"], cwd=dep_dir(name), check=False)
            if result.returncode != 0:
                sys.exit(
                    f"record-deps: could not check {name} for changes "
                    f"(is ../{name} a healthy repo?)"
                )
            if result.stdout.strip():
                dirty.append(name)
        if dirty:
            sys.exit(
                "record-deps: uncommitted or untracked changes in dependency: "
                + " ".join(dirty)
                + "\n  commit or stash them first, or bypass the pre-commit hook with: "
                "git commit --no-verify"
            )

    if require_pushed:
        unpushed: list[str] = []
        for name in DEPS:
            head = git(["rev-parse", "HEAD"], cwd=dep_dir(name)).stdout.strip()
            # Pushed == reachable from some remote-tracking ref (reflects the
            # last fetch/push from this checkout).
            contains = git(
                ["branch", "-r", "--contains", head], cwd=dep_dir(name), check=False
            ).stdout.strip()
            if not contains:
                unpushed.append(name)
        if unpushed:
            sys.exit(
                "record-deps: HEAD is not on any remote (unpushed) in dependency: "
                + " ".join(unpushed)
                + "\n  push it so the recorded revision is fetchable by others"
            )

    deps: list[dict[str, str]] = []
    for name in DEPS:
        d = dep_dir(name)
        deps.append(
            {
                "name": name,
                "repo": git(
                    ["remote", "get-url", "origin"], cwd=d, check=False
                ).stdout.strip(),
                "sha": git(["rev-parse", "HEAD"], cwd=d).stdout.strip(),
                "describe": git(
                    ["describe", "--tags", "--always", "--dirty"], cwd=d, check=False
                ).stdout.strip(),
            }
        )

    tmp = out.with_suffix(out.suffix + ".tmp")
    tmp.write_text(json.dumps(deps, indent=2) + "\n")
    tmp.replace(out)
    return 0


def mk_webp() -> int:
    """Convert the screenshots in images/ to lossless WebP for README.md.

    Delegates to scripts/mk-webp.
    """
    return subprocess.run([str(SCRIPTS / "mk-webp")]).returncode


def mk_phosphor(phosphor_dir: str | None) -> int:
    """Subset the Phosphor icon fonts to the glyphs IconMaker uses.

    Delegates to scripts/mk-phosphor-subset, which holds the icon codepoint map.
    A relative PHOSPHOR_DIR is resolved by that script against the current working
    directory, so it's passed through unchanged.
    """
    cmd = [str(SCRIPTS / "mk-phosphor-subset")]
    if phosphor_dir is not None:
        cmd.append(phosphor_dir)
    return subprocess.run(cmd).returncode


def mk_release(version: str | None) -> int:
    """Bump the version in the manifest, record deps, then commit and tag.

    The checks all run before the manifest is touched. If the commit or tag fails
    after that, the (uncommitted) version bump remains in the working tree for
    inspection - re-running then reads the already-bumped value and bumps again,
    so revert it first if you don't want versionCode to skip.
    """
    # A release commit must contain only the version bump and source-deps.json -
    # so refuse if aard2-android itself has staged or unstaged changes to tracked
    # files: they'd otherwise be swept into the release commit and tag, or (if
    # unstaged) built into the APK but left out of the tag. Untracked files are
    # fine - nothing here stages them.
    if git(["status", "--porcelain", "--untracked-files=no"], cwd=ROOT).stdout.strip():
        sys.exit("mk-release: aard2-android has uncommitted changes; commit or stash first")

    manifest = ROOT / "AndroidManifest.xml"
    text = manifest.read_text()
    codes = re.findall(r'android:versionCode="(\d+)"', text)
    names = re.findall(r'android:versionName="([^"]+)"', text)
    # Bail unless each parsed to a single clean value: a missing or duplicated
    # versionCode/versionName would otherwise make the bump bogus.
    if len(codes) != 1 or len(names) != 1:
        sys.exit(f"mk-release: cannot parse a single versionCode/versionName from {manifest.name}")
    cur_code, cur_name = codes[0], names[0]

    # versionCode must always increase; versionName defaults to 0.<code>, or the
    # argument if one is given (e.g. `./d mk-release 1.0`).
    new_code = str(int(cur_code) + 1)
    new_name = version if version is not None else f"0.{new_code}"

    # Pre-flight: bail before touching anything if the tag already exists, rather
    # than failing only after the release commit is made.
    if (
        git(["rev-parse", "-q", "--verify", f"refs/tags/{new_name}"], cwd=ROOT, check=False).returncode
        == 0
    ):
        sys.exit(f"mk-release: tag {new_name} already exists")

    # Pre-flight dependency checks (and record source-deps.json now) so we bail
    # here rather than aborting at git commit with a half-applied bump. A release
    # additionally requires the deps be pushed, so the revisions it records are
    # fetchable by whoever builds the release. record_deps returns 0 or exits.
    record_deps(require_clean=True, require_pushed=True, require_present=True)
    print(f"version: {cur_name} ({cur_code}) -> {new_name} ({new_code})")

    # Targeted string replace - no risk of mangling XML. Verify the bump applied
    # to exactly one occurrence of each, rather than silently writing back
    # unchanged.
    bumped = text.replace(
        f'android:versionCode="{cur_code}"', f'android:versionCode="{new_code}"'
    ).replace(f'android:versionName="{cur_name}"', f'android:versionName="{new_name}"')
    if (
        bumped == text
        or bumped.count(f'android:versionCode="{new_code}"') != 1
        or bumped.count(f'android:versionName="{new_name}"') != 1
    ):
        sys.exit(f"mk-release: version bump did not apply cleanly to {manifest.name}")
    manifest.write_text(bumped)

    git(["add", manifest.name, "source-deps.json"], cwd=ROOT)
    git(["commit", "-m", new_name], cwd=ROOT)
    git(["tag", new_name], cwd=ROOT)
    # A lightweight tag isn't carried by `git push --follow-tags` (annotated only),
    # so the tag needs its own push alongside the usual branch push.
    print(
        f"committed and tagged {new_name} "
        f"(push the branch as usual, then the tag: git push origin {new_name})"
    )
    return 0


def enable_hooks() -> int:
    """Point git at the tracked hooks in githooks/ (once per machine)."""
    git(["config", "core.hooksPath", "githooks"], cwd=ROOT)
    for hook in (ROOT / "githooks").iterdir():
        hook.chmod(hook.stat().st_mode | 0o111)
    print("git hooks enabled (core.hooksPath=githooks)")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="./d",
        description="aard2-android developer front door.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    sub = parser.add_subparsers(metavar="<command>")

    p = sub.add_parser(
        "record-deps", help="snapshot slobj/slobber revisions into source-deps.json"
    )
    p.add_argument(
        "--require-clean",
        action="store_true",
        help="fail if any dependency has uncommitted or untracked changes",
    )
    p.add_argument(
        "--require-pushed",
        action="store_true",
        help="fail if any dependency's HEAD isn't on a remote",
    )
    p.set_defaults(run=record_deps)

    sub.add_parser(
        "mk-webp", help="convert screenshots in images/ to lossless webp"
    ).set_defaults(run=mk_webp)

    p = sub.add_parser(
        "mk-phosphor", help="subset the Phosphor icon fonts to the glyphs IconMaker uses"
    )
    p.add_argument(
        "phosphor_dir",
        nargs="?",
        metavar="PHOSPHOR_DIR",
        help="Phosphor download dir (default ~/Downloads/phosphor-icons)",
    )
    p.set_defaults(run=mk_phosphor)

    p = sub.add_parser(
        "mk-release", help="bump version, record deps, commit + tag"
    )
    p.add_argument(
        "version", nargs="?", help="version name (default 0.<new versionCode>)"
    )
    p.set_defaults(run=mk_release)

    sub.add_parser(
        "enable-hooks", help="point git at the tracked hooks in githooks/ (once per machine)"
    ).set_defaults(run=enable_hooks)

    return parser


def main(argv: list[str]) -> int:
    # The pre-commit hook invokes `./d record-deps` with GIT_INDEX_FILE exported
    # (the commit's index); inherited, it would make the per-dependency git calls
    # operate on aard2-android's index instead of each sibling's own, reporting
    # every clean sibling as dirty. Drop it so each repo uses its default index.
    # This is a child process, so the hook's own staging still sees GIT_INDEX_FILE.
    os.environ.pop("GIT_INDEX_FILE", None)

    parser = build_parser()
    # Each subcommand registers its handler via set_defaults(run=...), and its
    # option dests match the handler's parameter names, so the remaining namespace
    # is passed straight through as keyword arguments. A new option whose dest
    # doesn't match a parameter fails loudly with a TypeError the first time it's
    # run.
    args = vars(parser.parse_args(argv))
    run = args.pop("run", None)
    if run is None:  # no subcommand given
        parser.print_help()
        return 0
    try:
        return run(**args)
    except subprocess.CalledProcessError as e:
        # git() captures output, so on a checked failure git's own message lands
        # in the exception rather than on the terminal - surface it instead of a
        # bare Python traceback. The quiet check=False probes don't raise, so they
        # stay quiet.
        cmd = " ".join(e.cmd) if isinstance(e.cmd, list) else str(e.cmd)
        print(f"d: command failed: {cmd}", file=sys.stderr)
        for stream in (e.stderr, e.stdout):
            if stream:
                print(stream, file=sys.stderr, end="")
        return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
