#!/usr/bin/env bash
# Pushing the tag is left to the caller, because it publishes to the Gradle Plugin Portal.
set -euo pipefail

cd "$(dirname "$0")"

fail() {
  echo "error: $*" >&2
  exit 1
}

[ $# -eq 1 ] || fail "usage: $0 <version>"
version="$1"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "'$version' is not a version like 1.2.3"

if [ -n "$(git status --porcelain)" ]; then
  fail "the working tree is not clean"
fi

tag="v$version"
if git rev-parse -q --verify "refs/tags/$tag" > /dev/null; then
  fail "the tag $tag already exists"
fi

# The release workflow takes the release notes from the section of the version.
unreleased="$(awk '$0 == "## Unreleased" { capture = 1; next } capture && /^## / { exit } capture && NF { print }' CHANGELOG.md)"
[ -n "$unreleased" ] || fail "CHANGELOG.md has no entries below '## Unreleased'"
sed -i "s/^## Unreleased$/## $version/" CHANGELOG.md

git commit -q -m "Release $version" CHANGELOG.md
git tag "$tag"

echo "Prepared $tag. Push it to publish the release:"
echo "  git push origin $(git rev-parse --abbrev-ref HEAD) $tag"
