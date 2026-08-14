#!/usr/bin/env python3
"""Inject the same changed-paths versus full comparison into both reports."""

from __future__ import annotations

import argparse
import csv
import html
from pathlib import Path


ALGORITHMS = (
    "cha",
    "rta",
    "zero-cfa",
    "optimized-0-1-cfa",
    "k-obj",
)
METRICS = (
    ("median_total_wall_seconds", "Median wall (s)"),
    ("median_call_graph_seconds", "Median CallGraph (s)"),
    ("median_peak_heap_used_mib", "Median Peak Heap Used (MiB)"),
    ("median_process_tree_peak_rss_kib", "Median RSS (KiB)"),
    ("cg_node_count", "CGNode"),
    ("cg_edge_count", "CGEdge"),
    ("real_external_method_node_count", "Real external method"),
    ("no_op_method_node_count", "No-op method"),
    ("factory_method_node_count", "Factory method"),
    ("dangerous_transfer_count", "Dangerous transfer"),
)


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--changed-summary", required=True, type=Path)
    parser.add_argument("--full-summary", required=True, type=Path)
    parser.add_argument("--changed-report", required=True, type=Path)
    parser.add_argument("--full-report", required=True, type=Path)
    return parser.parse_args()


def rows(path: Path, expected_scope: str) -> dict[str, dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as stream:
        values = list(csv.DictReader(stream, delimiter="\t"))
    result = {value["algorithm"]: value for value in values}
    if set(result) != set(ALGORITHMS):
        raise ValueError(f"incomplete algorithm summary: {path}")
    if any(value.get("dependency_analysis_scope") != expected_scope
           for value in values):
        raise ValueError(f"scope mismatch: {path}")
    if any(value.get("jdk_model")
           != ("none" if value.get("algorithm") == "cha" else "jdk8")
           for value in values):
        raise ValueError(f"JDK model mismatch: {path}")
    if any(value.get("result_refinement_algorithms") != "ssa-equivalence"
           for value in values):
        raise ValueError(f"result refinement mismatch: {path}")
    return result


def number(value: str) -> float:
    return float(value)


def formatted(value: float) -> str:
    if value.is_integer():
        return str(int(value))
    return f"{value:.3f}"


def section(
    changed: dict[str, dict[str, str]],
    full: dict[str, dict[str, str]],
) -> str:
    parts = [
        '<section id="cross-scope-comparison">',
        "<h2>changed-paths 与 full 对照</h2>",
        "<p>Absolute change = changed-paths − full；ratio = changed-paths / full。"
        "仅展示实测结果，不设置固定提速阈值。</p>",
        "<table><tr><th>Algorithm</th><th>Metric</th>",
        "<th>changed-paths</th><th>full</th><th>Absolute change</th>",
        "<th>Ratio</th></tr>",
    ]
    for algorithm in ALGORITHMS:
        for field, label in METRICS:
            changed_value = number(changed[algorithm][field])
            full_value = number(full[algorithm][field])
            ratio = "N/A" if full_value == 0 else f"{changed_value / full_value:.6f}"
            cells = (
                algorithm,
                label,
                formatted(changed_value),
                formatted(full_value),
                formatted(changed_value - full_value),
                ratio,
            )
            parts.append("<tr>" + "".join(
                f"<td>{html.escape(cell)}</td>" for cell in cells
            ) + "</tr>")
    parts.append("</table></section>")
    return "".join(parts)


def inject(path: Path, content: str) -> None:
    document = path.read_text(encoding="utf-8")
    marker = "</main>"
    if marker not in document:
        raise ValueError(f"report main element missing: {path}")
    updated = document.replace(marker, content + marker, 1)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(updated, encoding="utf-8")
    temporary.replace(path)


def main() -> int:
    args = arguments()
    comparison = section(
        rows(args.changed_summary, "changed-paths"),
        rows(args.full_summary, "full"),
    )
    inject(args.changed_report, comparison)
    inject(args.full_report, comparison)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
