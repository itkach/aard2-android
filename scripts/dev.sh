# aard2-android developer front door.
#
#   source scripts/dev.sh   # functions become available in your shell, or
#   ./d <command> [args]     # run one command in a subshell (see ./d for list)
#
# Each function's `## ...` comment is its one-line help, shown by `./d`.

# Derived from this file's own location (scripts/dev.sh -> repo root), not the
# caller's cwd, so `./d <cmd>` works from anywhere. Assigned unconditionally (no
# ${ROOT:-...}) so an unrelated ROOT in the caller's environment can't redirect
# it. ${BASH_SOURCE[0]:-$0} covers bash (BASH_SOURCE) and zsh (falls back to $0,
# the sourced file's path); this resolves correctly via ./d and via a bare
# `source scripts/dev.sh` alike.
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")/.." && pwd)

record-deps() {  ## snapshot slobj/slobber revisions into source-deps.json
    "$ROOT/scripts/record-deps"
}

mk-manifest() {  ## regenerate AndroidManifest.xml from the templates
    python3 "$ROOT/scripts/mk-android-manifest"
}

mk-webp() {  ## convert screenshots in images/ to lossless webp
    "$ROOT/scripts/mk-webp"
}

mk-phosphor() {  ## subset the Phosphor icon fonts to the glyphs IconMaker uses
    "$ROOT/scripts/mk-phosphor-subset" "$@"
}

mk-release() {  ## bump version, regen manifest, record deps, commit + tag [version]
    # Runs in a subshell: cd stays contained (dev.sh can be sourced), and each
    # step is gated so a failure never leaves a release commit/tag whose manifest
    # doesn't match its template. On failure the (uncommitted) template edit, if
    # already made, remains in the working tree for inspection - re-running then
    # reads the already-bumped value and bumps again, so revert it first if you
    # don't want versionCode to skip.
    (
        cd "$ROOT" || exit 1
        tmpl="AndroidManifest.template.xml"
        [ -f "$tmpl" ] || { echo "mk-release: $tmpl not found" >&2; exit 1; }
        cur_code=$(grep -oE 'android:versionCode="[0-9]+"' "$tmpl" | grep -oE '[0-9]+')
        cur_name=$(grep -oE 'android:versionName="[^"]+"' "$tmpl" | sed -E 's/.*"([^"]+)".*/\1/')
        # Bail unless each parsed to a single clean value: an empty, non-numeric or
        # duplicated versionCode would otherwise make new_code bogus (or throw an
        # arithmetic error), and the Python no-op replace would exit 0 -> bad release.
        if ! [[ $cur_code =~ ^[0-9]+$ ]] || [ -z "$cur_name" ] || [[ $cur_name == *$'\n'* ]]; then
            echo "mk-release: cannot parse a single versionCode/versionName from $tmpl" >&2
            exit 1
        fi
        # versionCode must always increase; versionName defaults to 0.<code>, or
        # the argument if one is given (e.g. `mk-release 1.0`).
        new_code=$(( cur_code + 1 ))
        new_name="${1:-0.$new_code}"
        # Pre-flight: bail before touching anything if the tag already exists,
        # rather than failing only after the release commit is made.
        if git rev-parse -q --verify "refs/tags/$new_name" >/dev/null; then
            echo "mk-release: tag $new_name already exists" >&2
            exit 1
        fi
        echo "version: $cur_name ($cur_code) -> $new_name ($new_code)"

        # Edit the template with Python (targeted - no risk of mangling XML). The
        # post-condition makes a no-op replace fail loudly instead of writing the
        # file back unchanged.
        python3 - "$tmpl" "$cur_code" "$new_code" "$cur_name" "$new_name" <<'PY' || exit 1
import sys
path, cc, nc, cn, nn = sys.argv[1:]
s = old = open(path).read()
s = s.replace(f'android:versionCode="{cc}"', f'android:versionCode="{nc}"')
s = s.replace(f'android:versionName="{cn}"', f'android:versionName="{nn}"')
if s == old or s.count(f'android:versionCode="{nc}"') != 1 \
             or s.count(f'android:versionName="{nn}"') != 1:
    sys.exit(f"mk-release: version bump did not apply cleanly to {path}")
open(path, "w").write(s)
PY

        mk-manifest || exit 1
        record-deps || exit 1
        git add "$tmpl" AndroidManifest.xml source-deps.json || exit 1
        git commit -m "$new_name" || exit 1
        git tag -a "$new_name" -m "Aard 2 $new_name" || exit 1
        echo "committed and tagged $new_name (not pushed - push with: git push --follow-tags)"
    )
}

enable-hooks() {  ## point git at the tracked hooks in githooks/ (once per machine)
    git -C "$ROOT" config core.hooksPath githooks
    chmod +x "$ROOT"/githooks/* 2>/dev/null
    echo "git hooks enabled (core.hooksPath=githooks)"
}

install-completion() {  ## add ./d shell completion to your shell rc [--print]
    local shell rc marker="aard2 d completion"
    shell=$(basename "${SHELL:-}")
    local block
    case "$shell" in
        zsh)  rc="$HOME/.zshrc"
              block=$(cat <<'EOF'
# >>> aard2 d completion >>>
_d_completion() {
    local root devsh
    root=$(git rev-parse --show-toplevel 2>/dev/null) || return
    for devsh in "$root/scripts/dev.sh" "$root/dev.sh"; do
        [ -f "$devsh" ] && { compadd ${(f)"$(grep -oE '^[a-z0-9_-]+\(\)' "$devsh" | sed 's/()//')"}; return; }
    done
}
compdef _d_completion d
# <<< aard2 d completion <<<
EOF
) ;;
        bash) rc="$HOME/.bashrc"
              block=$(cat <<'EOF'
# >>> aard2 d completion >>>
_d_completion() {
    local root devsh cmds
    root=$(git rev-parse --show-toplevel 2>/dev/null) || return
    for devsh in "$root/scripts/dev.sh" "$root/dev.sh"; do
        [ -f "$devsh" ] || continue
        cmds=$(grep -oE '^[a-z0-9_-]+\(\)' "$devsh" | sed 's/()//')
        COMPREPLY=( $(compgen -W "$cmds" -- "${COMP_WORDS[COMP_CWORD]}") )
        return
    done
}
complete -F _d_completion d ./d
# <<< aard2 d completion <<<
EOF
) ;;
        *) echo "install-completion: unsupported shell '$shell' (zsh/bash only)"; return 1 ;;
    esac

    if [ "${1:-}" = "--print" ]; then
        printf '%s\n' "$block"
        return
    fi

    # Idempotent + path-independent: strip any prior block, then append fresh.
    if [ -f "$rc" ] && grep -q ">>> $marker >>>" "$rc"; then
        sed "/>>> $marker >>>/,/<<< $marker <<</d" "$rc" > "$rc.tmp" && mv "$rc.tmp" "$rc"
    fi
    printf '\n%s\n' "$block" >> "$rc"
    echo "added ./d completion to $rc - run: exec $shell"
}

setup() {  ## one-time machine setup: enable-hooks + install-completion
    enable-hooks
    install-completion
}
