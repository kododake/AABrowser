#!/usr/bin/env python3
import argparse
import os
import sys
from pathlib import Path

# Standard GPLv3 header
STANDARD_HEADER = """/*
 * Copyright (C) 2025 AABrowser Contributors (https://github.com/kododake/AABrowser)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://gnu.org>.
 */
"""

IDENTIFIER = "Copyright (C) 2025 AABrowser Contributors"
MAX_LINES_LIMIT = 300

def process_file(file_path: Path, header: str, dry_run: bool) -> dict:
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    lines = content.splitlines()
    orig_count = len(lines)

    if IDENTIFIER in content[:500]:
        return {
            "status": "SKIPPED",
            "path": file_path,
            "orig_count": orig_count,
            "new_count": orig_count,
            "reason": "Header already present"
        }

    new_content = header + "\n" + content if not content.startswith("\n") else header + content
    new_lines = new_content.splitlines()
    new_count = len(new_lines)

    if not dry_run:
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(new_content)

    return {
        "status": "ADDED",
        "path": file_path,
        "orig_count": orig_count,
        "new_count": new_count,
        "reason": "Header added"
    }

def main():
    parser = argparse.ArgumentParser(description="Add GPLv3 license header to Kotlin files.")
    parser.add_argument(
        "--target-dir",
        type=str,
        default="app/src/main/java/com/kododake/aabrowser",
        help="Target directory to scan (default: app/src/main/java/com/kododake/aabrowser)"
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Simulate execution without modifying any files"
    )
    args = parser.parse_args()

    project_root = Path(__file__).resolve().parent.parent
    target_path = project_root / args.target_dir
    if not target_path.exists():
        target_path = Path(args.target_dir).resolve()
        if not target_path.exists():
            print(f"[ERROR] Target directory not found: {args.target_dir}", file=sys.stderr)
            sys.exit(1)

    header = STANDARD_HEADER
    kt_files = sorted(list(target_path.rglob("*.kt")))

    print(f"=== License Header Injector ===")
    print(f"Target directory: {target_path}")
    print(f"Files found: {len(kt_files)}")
    print(f"Mode: {'DRY-RUN (No files modified)' if args.dry_run else 'APPLY'}")
    print("=" * 31)

    added = []
    skipped = []
    over_limit = []

    for kt_file in kt_files:
        res = process_file(kt_file, header, args.dry_run)
        rel_path = kt_file.relative_to(project_root) if kt_file.is_relative_to(project_root) else kt_file

        if res["status"] == "ADDED":
            added.append(res)
            msg = f"[ADDED]   {rel_path} ({res['orig_count']} -> {res['new_count']} lines)"
        else:
            skipped.append(res)
            msg = f"[SKIPPED] {rel_path} ({res['reason']})"

        if res["new_count"] > MAX_LINES_LIMIT:
            over_limit.append((rel_path, res["new_count"]))
            msg += f" [WARNING: exceeds {MAX_LINES_LIMIT} lines!]"

        print(msg)

    print("\n" + "=" * 31)
    print("Summary:")
    print(f"  Total scanned: {len(kt_files)}")
    print(f"  Headers added: {len(added)}")
    print(f"  Skipped:       {len(skipped)}")

    if over_limit:
        print(f"\n[WARNING] {len(over_limit)} file(s) exceed the {MAX_LINES_LIMIT}-line limit after adding header:")
        for path, count in over_limit:
            print(f"  - {path}: {count} lines (+{count - MAX_LINES_LIMIT} over limit)")
        print(f"\nPlease refactor or adjust these files to maintain the {MAX_LINES_LIMIT}-line constraint if required.")

if __name__ == "__main__":
    main()
