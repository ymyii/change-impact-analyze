#!/usr/bin/env python3
"""Generate canonical CallGraph benchmark HTML and tracked TSV candidates."""

from __future__ import annotations

import argparse
import csv
import html
import json
import statistics
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any, Iterable


ALGORITHMS = ("cha",)
REFLECTION_DEFAULT = "ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD"
SAMPLE_COLUMNS = (
    "label", "run_kind", "round", "sample", "dependency_analysis_scope",
    "algorithm", "jdk_model", "result_refinement_algorithms",
    "wala_reflection_options", "total_wall_seconds",
    "call_graph_seconds", "peak_heap_used_mib",
    "peak_heap_committed_mib", "heap_max_mib", "heap_sample_count",
    "process_tree_peak_rss_kib", "entrypoint_count", "cg_node_count",
    "cg_edge_count", "real_external_artifact_count",
    "no_op_external_artifact_count", "real_external_method_node_count",
    "no_op_method_node_count", "factory_method_node_count",
    "dangerous_transfer_count", "status", "exit_code", "analyzer_sha256",
    "git_commit", "git_dirty", "os", "architecture", "analyzer_java",
    "jdk", "maven",
)
SUMMARY_COLUMNS = (
    "dependency_analysis_scope", "algorithm", "jdk_model",
    "result_refinement_algorithms",
    "wala_reflection_options", "samples",
    "min_total_wall_seconds", "median_total_wall_seconds",
    "max_total_wall_seconds", "min_call_graph_seconds",
    "median_call_graph_seconds", "max_call_graph_seconds",
    "min_peak_heap_used_mib", "median_peak_heap_used_mib",
    "max_peak_heap_used_mib", "min_peak_heap_committed_mib",
    "median_peak_heap_committed_mib", "max_peak_heap_committed_mib",
    "min_heap_max_mib", "median_heap_max_mib", "max_heap_max_mib",
    "min_process_tree_peak_rss_kib",
    "median_process_tree_peak_rss_kib",
    "max_process_tree_peak_rss_kib", "entrypoint_count",
    "cg_node_count", "cg_edge_count", "real_external_artifact_count",
    "no_op_external_artifact_count", "real_external_method_node_count",
    "no_op_method_node_count", "factory_method_node_count",
    "dangerous_transfer_count", "successful_samples",
)
TOPOLOGY_COLUMNS = (
    "algorithm", "jdk_model", "result_refinement_algorithms",
    "wala_reflection_options",
    "module", "direction",
    "record_type", "rank", "cg_node_id", "cg_node_identity", "context",
    "wala_synthetic", "sentinel_role", "method_identity", "owner", "name",
    "descriptor", "origin",
    "related_cg_node_count", "distinct_related_method_count", "raw_edge_count",
    "child_rank", "child_method_identity", "child_owner", "child_name",
    "child_descriptor", "child_origin", "child_related_cg_node_count",
    "child_raw_edge_count", "omitted_related_cg_node_count",
    "related_cg_node_examples",
    "path_root_kind", "path_root_cg_node_identity", "shortest_path", "cycle",
    "source_status", "source_sha256", "ir_status", "ir_sha256",
    "requested_dependency_scope", "actual_dependency_scope",
    "dependency_scope_fallback_reason", "real_external_artifact_count",
    "no_op_external_artifact_count", "real_external_method_node_count",
    "no_op_method_node_count", "factory_method_node_count",
    "dangerous_transfer_count", "ancestor_retained_external_type_count",
    "ancestor_retained_external_method_node_count",
    "pruned_external_method_target_count", "changed_dependency_seed",
    "dependency_path",
)
NUMERIC_SAMPLE_FIELDS = (
    "total_wall_seconds", "call_graph_seconds", "peak_heap_used_mib",
    "peak_heap_committed_mib", "heap_max_mib",
    "process_tree_peak_rss_kib", "entrypoint_count", "cg_node_count",
    "cg_edge_count", "real_external_artifact_count",
    "no_op_external_artifact_count", "real_external_method_node_count",
    "no_op_method_node_count", "factory_method_node_count",
    "dangerous_transfer_count",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-html", required=True, type=Path)
    parser.add_argument("--candidate-dir", required=True, type=Path)
    parser.add_argument("--scope", required=True, choices=("changed-paths", "full"))
    parser.add_argument("run_directories", nargs="+", type=Path)
    return parser.parse_args()


def read_metrics(run_directory: Path) -> dict[str, str]:
    path = run_directory / "logs" / "metrics.tsv"
    with path.open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        if tuple(reader.fieldnames or ()) != SAMPLE_COLUMNS:
            raise ValueError(f"unexpected metrics header: {path}")
        rows = list(reader)
    if len(rows) != 1:
        raise ValueError(f"expected exactly one metrics row: {path}")
    row = rows[0]
    row["run_directory"] = str(run_directory)
    return row


def load_topology(run_directory: Path) -> dict[str, Any]:
    path = run_directory / "topology.json"
    with path.open(encoding="utf-8") as stream:
        value = json.load(stream)
    if value.get("schemaVersion") != 10:
        raise ValueError(f"unsupported topology schema: {path}")
    return value


def number(row: dict[str, str], field: str) -> float:
    value = row.get(field, "")
    if value == "":
        raise ValueError(f"missing {field}: {row.get('run_directory', '')}")
    return float(value)


def topology_totals(topology: dict[str, Any]) -> tuple[int, int, int]:
    modules = topology.get("modules", [])
    return (
        sum(int(module["entrypointCount"]) for module in modules),
        sum(int(module["cgNodeCount"]) for module in modules),
        sum(int(module["cgEdgeCount"]) for module in modules),
    )


def boundary_totals(topology: dict[str, Any]) -> tuple[int, int, int, int, int, int]:
    modules = topology.get("modules", [])
    fields = (
        "realExternalArtifactCount", "noOpExternalArtifactCount",
        "realExternalMethodNodeCount", "noOpMethodNodeCount",
        "factoryMethodNodeCount", "dangerousTransferCount",
    )
    return tuple(sum(int(module.get(field, 0)) for module in modules)
                 for field in fields)


def validate(
    rows: list[dict[str, str]],
    topologies: dict[str, dict[str, Any]],
    scope: str,
) -> list[str]:
    errors: list[str] = []
    by_kind: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        by_kind[row.get("run_kind", "")].append(row)
    if len(rows) != 7:
        errors.append(f"应有 7 个独立 CLI 进程，实际 {len(rows)} 个")
    if len(by_kind["warmup"]) != 1:
        errors.append(f"应有 1 个 warm-up，实际 {len(by_kind['warmup'])} 个")
    if len(by_kind["formal"]) != 5:
        errors.append(f"应有 5 个正式样本，实际 {len(by_kind['formal'])} 个")
    if len(by_kind["control"]) != 1:
        errors.append(f"应有 1 个 semantic control，实际 {len(by_kind['control'])} 个")
    environment_fields = (
        "dependency_analysis_scope", "wala_reflection_options",
        "analyzer_sha256", "git_commit",
        "git_dirty", "os", "architecture", "analyzer_java", "jdk", "maven",
    )
    for field in environment_fields:
        values = {row.get(field, "") for row in rows}
        if len(values) != 1:
            errors.append(f"环境字段 {field} 不一致: {sorted(values)}")
    for row in rows:
        if row.get("dependency_analysis_scope") != scope:
            errors.append(
                f"{row.get('label')} scope 不匹配: "
                f"{row.get('dependency_analysis_scope')} != {scope}"
            )
        if row.get("status") != "SUCCESS" or row.get("exit_code") != "0":
            errors.append(f"运行失败: {row.get('label')} status={row.get('status')}")
        if row.get("wala_reflection_options") != REFLECTION_DEFAULT:
            errors.append(
                f"{row.get('label')} 未使用默认 ReflectionOptions: "
                f"{row.get('wala_reflection_options')}"
            )
        if row.get("algorithm") != "cha":
            errors.append(f"canonical benchmark 只接受 cha: {row.get('algorithm')}")
        for field in NUMERIC_SAMPLE_FIELDS:
            try:
                number(row, field)
            except ValueError as failure:
                errors.append(str(failure))
    for algorithm in ALGORITHMS:
        warmups = [row for row in by_kind["warmup"]
                   if row.get("algorithm") == algorithm]
        formal = [row for row in by_kind["formal"]
                  if row.get("algorithm") == algorithm]
        controls = [row for row in by_kind["control"]
                    if row.get("algorithm") == algorithm]
        if len(warmups) != 1:
            errors.append(f"{algorithm} 应有 1 个 warm-up，实际 {len(warmups)} 个")
            continue
        if len(formal) != 5:
            errors.append(f"{algorithm} 应有 5 个正式样本，实际 {len(formal)} 个")
        expected_controls = 1
        if len(controls) != expected_controls:
            errors.append(
                f"{algorithm} 应有 {expected_controls} 个 semantic control，"
                f"实际 {len(controls)} 个"
            )
        default_rows = warmups + formal
        expected_model = "none"
        if any(row.get("jdk_model") != expected_model for row in default_rows):
            errors.append(
                f"{algorithm} warm-up/formal 未使用默认 {expected_model} model"
            )
        if any(row.get("jdk_model") != "none" for row in controls):
            errors.append(f"{algorithm} control 未使用 none model")
        if any(row.get("result_refinement_algorithms") != "ssa-equivalence"
               for row in default_rows):
            errors.append(f"{algorithm} warm-up/formal 未选择 ssa-equivalence")
        expected_control_refinement = (
            "cha-local-receiver-inference" if algorithm == "cha"
            else "ssa-equivalence"
        )
        if any(row.get("result_refinement_algorithms")
               != expected_control_refinement for row in controls):
            errors.append(
                f"{algorithm} control refinement 应为 "
                f"{expected_control_refinement}"
            )
        topology = topologies.get(algorithm)
        if topology is None:
            errors.append(f"{algorithm} 缺少 topology JSON")
            continue
        if topology.get("algorithm") != algorithm:
            errors.append(f"{algorithm} topology algorithm 不匹配")
        if topology.get("kObjDepth") is not None:
            errors.append(f"{algorithm} topology kObjDepth 不匹配")
        if topology.get("reflectionOptions") != REFLECTION_DEFAULT:
            errors.append(f"{algorithm} topology ReflectionOptions 不匹配")
        expected_reflection_applied = "not applied by cha"
        if topology.get("reflectionApplied") != expected_reflection_applied:
            errors.append(f"{algorithm} topology reflectionApplied 不匹配")
        if topology.get("jdkModel") != expected_model:
            errors.append(f"{algorithm} topology JDK model 不匹配")
        if topology.get("resultRefinementAlgorithms") != ["ssa-equivalence"]:
            errors.append(f"{algorithm} topology result refinement 不匹配")
        if topology.get("requestedDependencyAnalysisScope") != scope:
            errors.append(f"{algorithm} topology requested scope 不匹配")
        for module in topology.get("modules", []):
            if module.get("actualDependencyAnalysisScope") != scope:
                errors.append(
                    f"{algorithm} {module.get('module')} actual scope="
                    f"{module.get('actualDependencyAnalysisScope')}"
                )
        expected = topology_totals(topology)
        warmup_counts = tuple(int(number(warmups[0], field)) for field in (
            "entrypoint_count", "cg_node_count", "cg_edge_count"))
        if expected != warmup_counts:
            errors.append(
                f"{algorithm} warm-up topology JSON/report 不一致: "
                f"{expected} != {warmup_counts}"
            )
        expected_boundary = boundary_totals(topology)
        boundary_fields = (
            "real_external_artifact_count", "no_op_external_artifact_count",
            "real_external_method_node_count", "no_op_method_node_count",
            "factory_method_node_count", "dangerous_transfer_count",
        )
        warmup_boundary = tuple(int(number(warmups[0], field))
                                for field in boundary_fields)
        if expected_boundary != warmup_boundary:
            errors.append(
                f"{algorithm} warm-up boundary JSON/report 不一致: "
                f"{expected_boundary} != {warmup_boundary}"
            )
        for row in formal:
            actual = tuple(int(number(row, field)) for field in (
                "entrypoint_count", "cg_node_count", "cg_edge_count"))
            if actual != expected:
                row["status"] = "TOPOLOGY_DRIFT"
                errors.append(
                    f"{row.get('label')} TOPOLOGY_DRIFT: {actual} != {expected}"
                )
            actual_boundary = tuple(int(number(row, field))
                                    for field in boundary_fields)
            if actual_boundary != expected_boundary:
                row["status"] = "TOPOLOGY_DRIFT"
                errors.append(
                    f"{row.get('label')} BOUNDARY_DRIFT: "
                    f"{actual_boundary} != {expected_boundary}"
                )
    return errors


def stats(rows: list[dict[str, str]], field: str) -> tuple[float, float, float]:
    values = [number(row, field) for row in rows]
    return min(values), statistics.median(values), max(values)


def summaries(formal: list[dict[str, str]], scope: str) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    for algorithm in ALGORITHMS:
        rows = [row for row in formal if row["algorithm"] == algorithm]
        wall = stats(rows, "total_wall_seconds")
        call_graph = stats(rows, "call_graph_seconds")
        heap = stats(rows, "peak_heap_used_mib")
        committed = stats(rows, "peak_heap_committed_mib")
        heap_max = stats(rows, "heap_max_mib")
        rss = stats(rows, "process_tree_peak_rss_kib")
        values: dict[str, Any] = {
            "dependency_analysis_scope": scope,
            "algorithm": algorithm,
            "jdk_model": rows[0]["jdk_model"],
            "result_refinement_algorithms": rows[0][
                "result_refinement_algorithms"],
            "wala_reflection_options": rows[0]["wala_reflection_options"],
            "samples": len(rows),
            "entrypoint_count": int(number(rows[0], "entrypoint_count")),
            "cg_node_count": int(number(rows[0], "cg_node_count")),
            "cg_edge_count": int(number(rows[0], "cg_edge_count")),
            "real_external_artifact_count": int(number(
                rows[0], "real_external_artifact_count")),
            "no_op_external_artifact_count": int(number(
                rows[0], "no_op_external_artifact_count")),
            "real_external_method_node_count": int(number(
                rows[0], "real_external_method_node_count")),
            "no_op_method_node_count": int(number(
                rows[0], "no_op_method_node_count")),
            "factory_method_node_count": int(number(
                rows[0], "factory_method_node_count")),
            "dangerous_transfer_count": int(number(
                rows[0], "dangerous_transfer_count")),
            "successful_samples": sum(row["status"] == "SUCCESS" for row in rows),
        }
        for prefix, triple in (
            ("total_wall_seconds", wall),
            ("call_graph_seconds", call_graph),
            ("peak_heap_used_mib", heap),
            ("peak_heap_committed_mib", committed),
            ("heap_max_mib", heap_max),
            ("process_tree_peak_rss_kib", rss),
        ):
            values[f"min_{prefix}"] = f"{triple[0]:.3f}"
            values[f"median_{prefix}"] = f"{triple[1]:.3f}"
            values[f"max_{prefix}"] = f"{triple[2]:.3f}"
        result.append({column: str(values[column]) for column in SUMMARY_COLUMNS})
    return result


def path_text(path: dict[str, Any]) -> str:
    def step_text(step: dict[str, Any]) -> str:
        role = step.get("sentinelRole", "NONE")
        prefix = f"[{role}] " if role != "NONE" else ""
        return prefix + step["identity"]

    return " -> ".join(step_text(step) for step in path.get("steps", []))


def topology_rows(topologies: dict[str, dict[str, Any]]) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for algorithm in ALGORITHMS:
        topology = topologies[algorithm]
        for module in topology.get("modules", []):
            scope_values = {
                "algorithm": algorithm,
                "jdk_model": topology["jdkModel"],
                "result_refinement_algorithms": ",".join(
                    topology["resultRefinementAlgorithms"]),
                "wala_reflection_options": topology["reflectionOptions"],
                "module": module["module"],
                "direction": "DEPENDENCY",
                "requested_dependency_scope": topology[
                    "requestedDependencyAnalysisScope"],
                "actual_dependency_scope": module[
                    "actualDependencyAnalysisScope"],
                "dependency_scope_fallback_reason": module.get(
                    "dependencyScopeFallbackReason", ""),
                "real_external_artifact_count": str(module.get(
                    "realExternalArtifactCount", 0)),
                "no_op_external_artifact_count": str(module.get(
                    "noOpExternalArtifactCount", 0)),
                "real_external_method_node_count": str(module.get(
                    "realExternalMethodNodeCount", 0)),
                "no_op_method_node_count": str(module.get(
                    "noOpMethodNodeCount", 0)),
                "factory_method_node_count": str(module.get(
                    "factoryMethodNodeCount", 0)),
                "dangerous_transfer_count": str(module.get(
                    "dangerousTransferCount", 0)),
                "ancestor_retained_external_type_count": str(module.get(
                    "ancestorRetainedExternalTypeCount", 0)),
                "ancestor_retained_external_method_node_count": str(module.get(
                    "ancestorRetainedExternalMethodNodeCount", 0)),
                "pruned_external_method_target_count": str(module.get(
                    "prunedExternalMethodTargetCount", 0)),
            }
            module_row = dict(scope_values)
            module_row["record_type"] = "DEPENDENCY_SCOPE"
            rows.append(module_row)
            for dependency_path in module.get("dependencyPaths", []):
                path_row = dict(scope_values)
                path_row.update({
                    "record_type": "DEPENDENCY_PATH",
                    "changed_dependency_seed": dependency_path["seed"],
                    "dependency_path": dependency_path["path"],
                })
                rows.append(path_row)
            for field, direction, child_field in (
                ("topCallers", "CALLER", "topCallees"),
                ("topCallees", "CALLEE", "topCallers"),
            ):
                for value in module.get(field, []):
                    node = value["node"]
                    method = node["method"]
                    source = value["source"]
                    ir = value["ir"]
                    common = {
                        "algorithm": algorithm,
                        "jdk_model": topology["jdkModel"],
                        "result_refinement_algorithms": ",".join(
                            topology["resultRefinementAlgorithms"]),
                        "wala_reflection_options": topology["reflectionOptions"],
                        "module": module["module"],
                        "direction": direction,
                        "rank": str(value["rank"]),
                        "cg_node_id": str(node["graphNodeId"]),
                        "cg_node_identity": node["identity"],
                        "context": node["context"],
                        "wala_synthetic": str(
                            bool(node.get("walaSynthetic"))).lower(),
                        "sentinel_role": node.get("sentinelRole", "NONE"),
                        "method_identity": method["identity"],
                        "owner": method["owner"],
                        "name": method["name"],
                        "descriptor": method["descriptor"],
                        "origin": method["origin"],
                        "related_cg_node_count": str(value["relatedCgNodeCount"]),
                        "distinct_related_method_count": str(
                            value["distinctRelatedMethodCount"]),
                        "raw_edge_count": str(value["rawEdgeCount"]),
                        "cycle": str(bool(value.get("cycle"))).lower(),
                        "source_status": source["status"],
                        "source_sha256": source["sha256"],
                        "ir_status": ir["status"],
                        "ir_sha256": ir["sha256"],
                    }
                    ranked_row = dict(common)
                    ranked_row["record_type"] = "RANKED_CGNODE"
                    rows.append(ranked_row)
                    for child in value.get(child_field, []):
                        child_method = child["method"]
                        child_row = dict(common)
                        child_row.update({
                            "record_type": "RELATED_IMETHOD",
                            "child_rank": str(child["rank"]),
                            "child_method_identity": child_method["identity"],
                            "child_owner": child_method["owner"],
                            "child_name": child_method["name"],
                            "child_descriptor": child_method["descriptor"],
                            "child_origin": child_method["origin"],
                            "child_related_cg_node_count": str(
                                child["relatedCgNodeCount"]),
                            "child_raw_edge_count": str(child["rawEdgeCount"]),
                            "omitted_related_cg_node_count": str(
                                child.get("omittedRelatedCgNodeCount", 0)),
                            "related_cg_node_examples": " || ".join(
                                related["identity"]
                                for related in child.get(
                                    "relatedCgNodeExamples", [])
                            ),
                        })
                        rows.append(child_row)
                    for path in value.get("reachabilityPaths", []):
                        path_row = dict(common)
                        path_row.update({
                            "record_type": "REACHABILITY_PATH",
                            "path_root_kind": path["rootKind"],
                            "path_root_cg_node_identity":
                                path["root"]["identity"],
                            "shortest_path": path_text(path),
                        })
                        rows.append(path_row)
    return rows


def write_tsv(path: Path, columns: Iterable[str], rows: Iterable[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(columns), delimiter="\t",
                                lineterminator="\n", quoting=csv.QUOTE_MINIMAL,
                                extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def e(value: Any) -> str:
    return html.escape(str(value), quote=True)


def method_label(method: dict[str, Any]) -> str:
    return f"{method['owner']}#{method['name']}{method['descriptor']}@{method['origin']}"


def node_label(node: dict[str, Any]) -> str:
    return node["identity"]


def sentinel_badge(node: dict[str, Any]) -> str:
    role = node.get("sentinelRole", "NONE")
    if role == "NONE":
        return ""
    return f' <span class="badge sentinel">{e(role)}</span>'


def render_paths(paths: list[dict[str, Any]]) -> str:
    parts = ["<ol>"]
    for path in paths:
        root = path["root"]
        steps = []
        for step in path.get("steps", []):
            cycle_badge = (
                ' <span class="badge cycle">CYCLE</span>'
                if step.get("cycle") else ""
            )
            steps.append(
                f'<code>{e(step["identity"])}</code>'
                f'{sentinel_badge(step)}{cycle_badge}'
            )
        parts.append(
            f'<li><span class="badge root-kind">{e(path["rootKind"])}</span> '
            f'<code>{e(root["identity"])}</code>{sentinel_badge(root)}<br>'
            + " → ".join(steps) + "</li>"
        )
    parts.append("</ol>")
    return "".join(parts)


def render_related_methods(
    values: list[dict[str, Any]],
    relation_label: str,
) -> str:
    if not values:
        return "<p>无 related IMethod。</p>"
    parts = [
        f"<h5>Top 10 {e(relation_label)} IMethod（按 related CGNode 数）</h5>",
        "<table><tr><th>Rank</th><th>IMethod</th><th>Related CGNode</th>",
        "<th>Raw CGEdge</th><th>Context 明细</th></tr>",
    ]
    for value in values:
        related_nodes = value.get("relatedCgNodeExamples", [])
        total_nodes = int(value.get("relatedCgNodeCount", len(related_nodes)))
        omitted_nodes = int(value.get(
            "omittedRelatedCgNodeCount", max(0, total_nodes - len(related_nodes))))
        contexts = "".join(
            f"<li><code>{e(node_label(node))}</code></li>"
            for node in related_nodes
        )
        parts.append(
            "<tr>"
            f'<td>{e(value["rank"])}</td>'
            f'<td><code>{e(method_label(value["method"]))}</code></td>'
            f'<td>{e(value["relatedCgNodeCount"])}</td>'
            f'<td>{e(value["rawEdgeCount"])}</td>'
            f'<td><details><summary>{len(related_nodes)} / {total_nodes} '
            f'CGNode Context examples；omitted {omitted_nodes}</summary>'
            f"<ol>{contexts}</ol></details></td></tr>"
        )
    parts.append("</table>")
    return "".join(parts)


def render_ranked(
    values: list[dict[str, Any]],
    relation_label: str,
    child_field: str,
) -> str:
    parts: list[str] = []
    for value in values:
        node = value["node"]
        method = node["method"]
        source = value["source"]
        ir = value["ir"]
        badges = []
        if value.get("cycle"):
            badges.append('<span class="badge cycle">CYCLE</span>')
        if node.get("walaSynthetic"):
            badges.append('<span class="badge">WALA SYNTHETIC</span>')
        sentinel = sentinel_badge(node)
        if sentinel:
            badges.append(sentinel.strip())
        badges.append(f'<span class="badge">SOURCE {e(source["status"])}</span>')
        badges.append(f'<span class="badge">IR {e(ir["status"])}</span>')
        parts.append(
            f'<article class="cgnode"><h4>#{value["rank"]} '
            f'<code>{e(node_label(node))}</code> {" ".join(badges)}</h4>'
            f'<p>IMethod: <code>{e(method_label(method))}</code><br>'
            f'Context: <code>{e(node["context"])}</code><br>'
            f'WALA synthetic/summary: <strong>'
            f'{e(str(bool(node.get("walaSynthetic"))).lower())}</strong><br>'
            f'{e(relation_label)} CGNode: <strong>{value["relatedCgNodeCount"]}</strong>；'
            f'distinct {e(relation_label)} IMethod: '
            f'<strong>{value["distinctRelatedMethodCount"]}</strong>；'
            f'raw CGEdge: {value["rawEdgeCount"]}；'
            f'classpath source: <code>{e(source["classpathSource"] or "UNAVAILABLE")}</code></p>'
        )
        parts.append(render_related_methods(
            value.get(child_field, []), relation_label.lower()))
        parts.append("<h5>Declared entrypoint shortest CGNode chains</h5>")
        paths = value.get("reachabilityPaths", [])
        declared_paths = [
            path for path in paths
            if path.get("rootKind") == "DECLARED_ENTRYPOINT"
        ]
        sentinel_paths = [
            path for path in paths
            if path.get("rootKind") in {"FAKE_ROOT", "FAKE_WORLD_CLINIT"}
        ]
        if declared_paths:
            parts.append(render_paths(declared_paths))
        else:
            parts.append(
                '<p class="warning">当前 ranked CGNode 无 declared entrypoint path，'
                '请查看 WALA sentinel shortest CGNode chains。</p>'
            )
        parts.append("<h5>WALA sentinel shortest CGNode chains</h5>")
        if sentinel_paths:
            parts.append(render_paths(sentinel_paths))
        else:
            parts.append("<p>无 WALA sentinel path。</p>")
        reason = source.get("reason", "")
        parts.append(
            f'<details><summary>Source — {e(source["status"])} — SHA-256 '
            f'<code>{e(source["sha256"] or "UNAVAILABLE")}</code></summary>'
            f'<p>{e(reason)}</p><pre><code>{e(source.get("text", "") or "UNAVAILABLE")}'
            '</code></pre></details>'
        )
        ir_reason = ir.get("reason", "")
        parts.append(
            f'<details><summary>WALA IR — {e(ir["status"])} — SHA-256 '
            f'<code>{e(ir["sha256"] or "UNAVAILABLE")}</code></summary>'
            f'<p>{e(ir_reason)}</p><pre><code>{e(ir.get("text", "") or "UNAVAILABLE")}'
            '</code></pre></details></article>'
        )
    return "".join(parts) if parts else "<p>无 Top CGNode。</p>"


def render_html(
    rows: list[dict[str, str]],
    topologies: dict[str, dict[str, Any]],
    summary_rows: list[dict[str, str]],
    errors: list[str],
    scope: str,
) -> str:
    formal = [row for row in rows if row.get("run_kind") == "formal"]
    status = "SUCCESS" if not errors else "FAILED"
    css = """
body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;margin:0;background:#f5f7fb;color:#172033;line-height:1.5}
main{max-width:1500px;margin:auto;padding:32px}h1,h2,h3,h4{line-height:1.25}section{background:#fff;border:1px solid #dfe5ef;border-radius:12px;padding:22px;margin:20px 0;box-shadow:0 4px 18px #1c2b4a0d}
table{border-collapse:collapse;width:100%;display:block;overflow-x:auto}th,td{border:1px solid #dfe5ef;padding:8px 10px;text-align:right;white-space:nowrap}th:first-child,td:first-child{text-align:left}th{background:#eef3fa}code,pre{font-family:ui-monospace,SFMono-Regular,Menlo,monospace}pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#101827;color:#e7edf8;padding:14px;border-radius:8px;max-height:640px;overflow:auto}
.cgnode{border-top:1px solid #dfe5ef;padding:12px 0}.badge{display:inline-block;background:#e8eef9;border-radius:999px;padding:2px 8px;font-size:.75rem}.cycle{background:#ffe0ad;color:#744400}.ok{color:#14733c}.bad,.warning{color:#a12626}.muted{color:#5b667a}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:12px}.metric{background:#f7f9fd;padding:12px;border-radius:8px}details{margin:10px 0}summary{cursor:pointer;font-weight:600}
"""
    parts = [
        "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">",
        '<meta name="viewport" content="width=device-width,initial-scale=1">',
        f"<title>CallGraph Benchmark — {e(scope)}</title><style>{css}</style></head><body><main>",
        f"<h1>CallGraph Benchmark 可观测性报告 — <code>{e(scope)}</code></h1>",
        f'<p>Suite status: <strong class="{"ok" if not errors else "bad"}">{status}</strong>。'
        "正式样本使用全新 Java Virtual Machine（JVM），warm-up 仅用于 topology 与缓存预热；"
        "canonical algorithm 固定为 CHA；另有一个 "
        "<code>cha-local-receiver-inference</code> control。</p>",
    ]
    if errors:
        parts.append("<section><h2>Failure diagnostics</h2><ul>")
        parts.extend(f"<li>{e(error)}</li>" for error in errors)
        parts.append("</ul><p>tracked TSV 未更新；失败候选与 raw run 保留在 tmp-files/。</p></section>")
    summary_by_algorithm = {row["algorithm"]: row for row in summary_rows}
    for algorithm in ALGORITHMS:
        algorithm_rows = sorted(
            [row for row in formal if row.get("algorithm") == algorithm],
            key=lambda row: int(row.get("sample", "0") or 0),
        )
        parts.append(
            f'<section id="{e(algorithm)}" '
            f'class="algorithm-panel panel-{e(algorithm)}">'
            f'<h2>Algorithm: <code>{e(algorithm)}</code></h2>'
        )
        parts.append("<h3>五个正式样本</h3><table><tr>"
                     "<th>Sample</th><th>Total wall (s)</th><th>CallGraph (s)</th>"
                     "<th>Peak Heap Used (MiB)</th><th>Heap Committed (MiB)</th>"
                     "<th>Heap Max (MiB)</th><th>Peak RSS (KiB)</th>"
                     "<th>Entrypoint</th><th>CGNode</th><th>CGEdge</th>"
                     "<th>Real external method</th><th>No-op method</th>"
                     "<th>Factory method</th><th>Dangerous transfer</th>"
                     "<th>Status</th></tr>")
        for row in algorithm_rows:
            parts.append("<tr>" + "".join(f"<td>{e(row.get(field, ''))}</td>" for field in (
                "sample", "total_wall_seconds", "call_graph_seconds",
                "peak_heap_used_mib", "peak_heap_committed_mib", "heap_max_mib",
                "process_tree_peak_rss_kib", "entrypoint_count", "cg_node_count",
                "cg_edge_count", "real_external_method_node_count",
                "no_op_method_node_count", "factory_method_node_count",
                "dangerous_transfer_count", "status",
            )) + "</tr>")
        parts.append("</table>")
        summary = summary_by_algorithm.get(algorithm)
        if summary:
            parts.append("<h3>Min / median / max</h3><table><tr><th>Metric</th><th>Min</th><th>Median</th><th>Max</th></tr>")
            for field, label in (
                ("total_wall_seconds", "Total wall (s)"),
                ("call_graph_seconds", "CallGraph (s)"),
                ("peak_heap_used_mib", "Peak Heap Used (MiB)"),
                ("peak_heap_committed_mib", "Peak Heap Committed (MiB)"),
                ("heap_max_mib", "Heap Max (MiB)"),
                ("process_tree_peak_rss_kib", "Process-tree peak RSS (KiB)"),
            ):
                parts.append(f"<tr><td>{e(label)}</td><td>{e(summary['min_' + field])}</td>"
                             f"<td>{e(summary['median_' + field])}</td>"
                             f"<td>{e(summary['max_' + field])}</td></tr>")
            parts.append("</table>")
        topology = topologies.get(algorithm, {})
        for module in topology.get("modules", []):
            parts.append(f'<h3>Module topology: <code>{e(module["module"])}</code></h3>')
            parts.append('<div class="grid">'
                         f'<div class="metric">Entrypoint<br><strong>{module["entrypointCount"]}</strong></div>'
                         f'<div class="metric">CGNode<br><strong>{module["cgNodeCount"]}</strong></div>'
                         f'<div class="metric">CGEdge<br><strong>{module["cgEdgeCount"]}</strong></div>'
                         f'<div class="metric">Actual scope<br><strong>{e(module["actualDependencyAnalysisScope"])}</strong></div>'
                         f'<div class="metric">Real/no-op artifact<br><strong>{module.get("realExternalArtifactCount", 0)} / {module.get("noOpExternalArtifactCount", 0)}</strong></div>'
                         f'<div class="metric">Real/no-op/factory method<br><strong>{module.get("realExternalMethodNodeCount", 0)} / {module.get("noOpMethodNodeCount", 0)} / {module.get("factoryMethodNodeCount", 0)}</strong></div>'
                         f'<div class="metric">Ancestor-retained type/method<br><strong>{module.get("ancestorRetainedExternalTypeCount", 0)} / {module.get("ancestorRetainedExternalMethodNodeCount", 0)}</strong></div>'
                         f'<div class="metric">Pruned external target<br><strong>{module.get("prunedExternalMethodTargetCount", 0)}</strong></div>'
                         f'<div class="metric">Dangerous transfer<br><strong>{module.get("dangerousTransferCount", 0)}</strong></div></div>')
            fallback = module.get("dependencyScopeFallbackReason", "")
            if fallback:
                parts.append(f'<p class="warning">Fallback: <code>{e(fallback)}</code></p>')
            paths = module.get("dependencyPaths", [])
            if paths:
                parts.append("<h4>Changed dependency paths</h4><ol>")
                parts.extend(
                    f'<li><code>{e(path["seed"])}</code>: '
                    f'<code>{e(path["path"])}</code></li>'
                    for path in paths
                )
                parts.append("</ol>")
            parts.append("<h3>Top 10 caller CGNode（按 related callee CGNode）</h3>")
            parts.append(render_ranked(
                module.get("topCallers", []), "Callee", "topCallees"))
            parts.append("<h3>Top 10 callee CGNode（按 related caller CGNode）</h3>")
            parts.append(render_ranked(
                module.get("topCallees", []), "Caller", "topCallers"))
        parts.append("</section>")
    parts.append("</main></body></html>")
    return "".join(parts)


def main() -> int:
    args = parse_args()
    rows: list[dict[str, str]] = []
    topologies: dict[str, dict[str, Any]] = {}
    errors: list[str] = []
    for run_directory in args.run_directories:
        try:
            row = read_metrics(run_directory)
            rows.append(row)
            if row.get("run_kind") == "warmup":
                topology = load_topology(run_directory)
                algorithm = row.get("algorithm", "")
                if algorithm in topologies:
                    errors.append(f"重复 warm-up topology: {algorithm}")
                topologies[algorithm] = topology
        except (OSError, ValueError, json.JSONDecodeError) as failure:
            errors.append(str(failure))
    errors.extend(validate(rows, topologies, args.scope))
    formal = [row for row in rows if row.get("run_kind") == "formal"]
    summary_rows: list[dict[str, str]] = []
    if len(formal) == 5 and all(
            sum(row.get("algorithm") == algorithm for row in formal) == 5
            for algorithm in ALGORITHMS):
        try:
            summary_rows = summaries(formal, args.scope)
        except (ValueError, statistics.StatisticsError) as failure:
            errors.append(str(failure))
    report = render_html(rows, topologies, summary_rows, errors, args.scope)
    args.output_html.parent.mkdir(parents=True, exist_ok=True)
    temporary_html = args.output_html.with_suffix(args.output_html.suffix + ".tmp")
    temporary_html.write_text(report, encoding="utf-8")
    temporary_html.replace(args.output_html)
    args.candidate_dir.mkdir(parents=True, exist_ok=True)
    write_tsv(args.candidate_dir / "samples.tsv", SAMPLE_COLUMNS, formal)
    if summary_rows:
        write_tsv(args.candidate_dir / "summary.tsv", SUMMARY_COLUMNS,
                  summary_rows)
    if all(algorithm in topologies for algorithm in ALGORITHMS):
        write_tsv(args.candidate_dir / "topology.tsv", TOPOLOGY_COLUMNS,
                  topology_rows(topologies))
    if errors:
        (args.candidate_dir / "failure.txt").write_text(
            "\n".join(errors) + "\n", encoding="utf-8")
        for error in errors:
            print(f"report failure: {error}", file=sys.stderr)
        return 1
    print(f"HTML report: {args.output_html}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
