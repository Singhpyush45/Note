#!/usr/bin/env python3
"""Publish build/test results as GitHub check-run annotations.

Why: raw job logs live on storage some tools can't reach, but annotations are part of the
GitHub API. This script turns JUnit XML, Lint XML and the Gradle log into a few annotations:
a summary notice plus error annotations with failing tests, lint errors and the build failure.

Usage: report.py <label> <gradle-log> <junit-xml-glob> [<junit-xml-glob> ...]
"""
import glob
import re
import sys
import xml.etree.ElementTree as ET

MAX_CHARS = 30_000  # per annotation; keeps well under GitHub's limit
MAX_ERRORS = 8      # GitHub keeps at most 10 error annotations per step


def esc_data(text: str) -> str:
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def esc_prop(text: str) -> str:
    return esc_data(text).replace(":", "%3A").replace(",", "%2C")


def emit(level: str, title: str, message: str) -> None:
    message = message if len(message) <= MAX_CHARS else message[:MAX_CHARS] + "\n…(truncated)"
    print(f"::{level} title={esc_prop(title)}::{esc_data(message)}", flush=True)


def junit(globs):
    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    failures = []
    files = sorted({f for g in globs for f in glob.glob(g, recursive=True)})
    for path in files:
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as exc:
            failures.append(f"Unreadable test report {path}: {exc}")
            continue
        suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
        for suite in suites:
            for case in suite.findall("testcase"):
                totals["tests"] += 1
                if case.find("skipped") is not None:
                    totals["skipped"] += 1
                for kind in ("failure", "error"):
                    node = case.find(kind)
                    if node is not None:
                        totals["failures" if kind == "failure" else "errors"] += 1
                        body = (node.get("message") or "") + "\n" + (node.text or "")
                        lines = [l for l in body.strip().splitlines()
                                 if l.strip() and not re.match(r"\s+at (org\.junit|org\.robolectric|java\.base|jdk\.|androidx\.test|org\.gradle|kotlinx\.coroutines|androidx\.compose\.ui\.test)", l)][:14]
                        failures.append(f"✗ {case.get('classname')}.{case.get('name')}\n" + "\n".join(lines))
    return files, totals, failures


def lint_issues(severities):
    issues = []
    for path in glob.glob("**/build/reports/lint-results-*.xml", recursive=True):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for issue in root.findall("issue"):
            if issue.get("severity") in severities:
                loc = issue.find("location")
                where = f"{loc.get('file')}:{loc.get('line')}" if loc is not None else "?"
                issues.append(f"[{issue.get('id')}] {where}\n  {issue.get('message')}")
    return issues


def gradle_failure(log_path):
    try:
        text = open(log_path, encoding="utf-8", errors="replace").read()
    except OSError:
        return None, False
    failed = "BUILD FAILED" in text or "FAILURE:" in text
    if not failed:
        return None, False
    # Compiler errors ("e: file:///…") and the "What went wrong" blocks are the useful parts.
    compile_errors = [l for l in text.splitlines() if l.startswith("e: ")][:60]
    blocks = re.findall(r"\* What went wrong:\n(.*?)(?=\n\* Try:|\Z)", text, re.S)
    tail = "\n".join(text.splitlines()[-80:])
    parts = []
    if compile_errors:
        parts.append("Compiler errors:\n" + "\n".join(compile_errors))
    if blocks:
        parts.append("What went wrong:\n" + "\n---\n".join(b.strip() for b in blocks[:6]))
    parts.append("Log tail:\n" + tail)
    return "\n\n".join(parts), True


def main():
    label, log_path, globs = sys.argv[1], sys.argv[2], sys.argv[3:]
    files, totals, failures = junit(globs)
    lint = lint_issues(("Error", "Fatal")) if label.startswith("build") else []
    lint_warnings = lint_issues(("Warning",)) if label.startswith("build") else []
    failure_text, build_failed = gradle_failure(log_path)

    status = "FAILED" if (build_failed or failures or totals["errors"]) else "OK"
    emit("notice", f"{label} summary",
         f"status={status}\nreports={len(files)} tests={totals['tests']} failures={totals['failures']} "
         f"errors={totals['errors']} skipped={totals['skipped']} lintErrors={len(lint)}")

    try:
        warnings = sorted({l for l in open(log_path, encoding="utf-8", errors="replace").read().splitlines()
                           if l.startswith("w: ")})
    except OSError:
        warnings = []
    if warnings:
        emit("warning", f"{label}: compiler warnings ({len(warnings)})", "\n".join(warnings[:80]))
    try:
        log_lines = open(log_path, encoding="utf-8", errors="replace").read().splitlines()
    except OSError:
        log_lines = []
    deprecations = sorted({l.strip() for l in log_lines if "deprecated" in l.lower() and "Gradle" in l
                           and not l.startswith("w: ")})
    if lint_warnings:
        emit("warning", f"{label}: lint warnings ({len(lint_warnings)})", "\n\n".join(lint_warnings[:40]))
    if deprecations:
        emit("warning", f"{label}: Gradle deprecations ({len(deprecations)})", "\n".join(deprecations[:40]))

    budget = MAX_ERRORS
    if lint and budget:
        emit("error", f"{label}: lint errors ({len(lint)})", "\n\n".join(lint))
        budget -= 1
    if failure_text and budget:
        emit("error", f"{label}: Gradle failure", failure_text)
        budget -= 1
    chunk = ""
    for item in failures:
        if len(chunk) + len(item) > MAX_CHARS and chunk and budget > 1:
            emit("error", f"{label}: failures", chunk)
            budget -= 1
            chunk = ""
        chunk += item + "\n\n"
    if chunk and budget:
        emit("error", f"{label}: failures", chunk)


if __name__ == "__main__":
    main()
