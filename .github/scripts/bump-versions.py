#!/usr/bin/env python3

from concurrent.futures import Future, ThreadPoolExecutor, as_completed
import itertools
from pathlib import Path
import re
import subprocess
import sys

VERSION_STR = "VersionCode ="
VERSION_REGEX_KMK = re.compile(f"(?<=Kmk){VERSION_STR} (\\d+)")
VERSION_REGEX_UPSTREAM = re.compile(f"(?<!Kmk){VERSION_STR} (\\d+)")
BUMPED_FILES: list[Path] = []

BOT_EMAIL = "github-actions[bot]@users.noreply.github.com"
BOT_NAME = "github-actions[bot]"

def has_match(query: str, file: Path) -> tuple[Path, bool]:
    return (file, query in file.read_text())

def find_files_with_match(query: str, include_multisrc: bool = True) -> list[Path]:
    files = Path("src").glob("*/*/build.gradle")
    if include_multisrc:
        files = itertools.chain(files, Path("lib-multisrc").glob("*/build.gradle.kts"))

    # Prevent bumping files twice.
    files = filter(lambda file: file not in BUMPED_FILES, files)

    # Use multiple threads to find matches.
    with ThreadPoolExecutor() as executor:
        futures = [executor.submit(has_match, query, file) for file in files]
        results = map(Future.result, as_completed(futures))
        return [path for path, result in results if result]

def replace_version(match: re.Match) -> str:
    version = int(match.group(1))
    print(f"{version} -> {version + 1}")
    return f"{VERSION_STR} {version + 1}"

def bump_version(file: Path):
    BUMPED_FILES.append(file)
    with file.open("r+") as f:
        print(f"\n{file}: ", end="")
        regex = VERSION_REGEX_KMK if len(sys.argv) > 1 and sys.argv[1].lower() == 'true' else VERSION_REGEX_UPSTREAM
        text = regex.sub(replace_version, f.read())
        # Move the cursor to the start again, to prevent writing at the end
        f.seek(0)
        f.write(text)

def bump_lib_multisrc(theme: str):
    for file in find_files_with_match(f"themePkg = '{theme}'", include_multisrc=False):
        bump_version(file)

def commit_changes():
    paths = [str(path.resolve()) for path in BUMPED_FILES]
    subprocess.check_call(["git", "config", "--global", "user.email", BOT_EMAIL])
    subprocess.check_call(["git", "config", "--global", "user.name", BOT_NAME])
    subprocess.check_call(["git", "add"] + paths)
    commit_message = "[skip ci] chore: Mass-bump on extensions"
    if len(sys.argv) > 2:
        commit_message += f"\n\nCaused by: {sys.argv[2]}"
    subprocess.check_call(["git", "commit", "-m", commit_message])
    # 'git push' will be doing outside of this script so we can decide per workflow if we want to push or not.
    # subprocess.check_call(["git", "push"])

if __name__ == "__main__":
    if len(sys.argv) > 3:
        # Regex to match the lib name in the path, like "unpacker" or "dood-extractor".
        lib_regex = re.compile(r"lib/([a-z0-9-]+)/")
        # Find matches and remove None results.
        matches = filter(None, map(lib_regex.search, sys.argv[3:]))
        for match in matches:
            project_path = ":lib:" + match.group(1)
            for file in find_files_with_match(project_path):
                if file.parent.parent.name == "lib-multisrc":
                    bump_lib_multisrc(file.parent.name)
                else:
                    bump_version(file)

        if len(BUMPED_FILES) > 0:
            commit_changes()
