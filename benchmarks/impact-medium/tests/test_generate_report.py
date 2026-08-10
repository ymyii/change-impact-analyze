#!/usr/bin/env python3
"""Black-box tests for the canonical benchmark report generator."""

from __future__ import annotations

import csv
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ALGORITHMS = (
    "rta",
    "zero-cfa",
    "optimized-0-1-cfa",
    "1-object-1-call-site",
)
REFLECTION_OPTIONS = "ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD"
SAMPLE_COLUMNS = (
    "label",
    "run_kind",
    "round",
    "sample",
    "algorithm",
    "wala_reflection_options",
    "total_wall_seconds",
    "call_graph_seconds",
    "peak_heap_used_mib",
    "peak_heap_committed_mib",
    "heap_max_mib",
    "heap_sample_count",
    "process_tree_peak_rss_kib",
    "entrypoint_count",
    "cg_node_count",
    "cg_edge_count",
    "status",
    "exit_code",
    "analyzer_sha256",
    "git_commit",
    "git_dirty",
    "os",
    "architecture",
    "analyzer_java",
    "jdk",
    "maven",
)
GRAPH_COUNTS = {
    "rta": (12, 120, 240),
    "zero-cfa": (12, 24, 42),
    "optimized-0-1-cfa": (12, 30, 48),
    "1-object-1-call-site": (12, 36, 60),
}


class GenerateReportTest(unittest.TestCase):
    """Exercises report generation from a complete synthetic suite."""

    @classmethod
    def setUpClass(cls) -> None:
        scripts = Path(__file__).resolve().parents[1] / "scripts"
        cls.generator = scripts / "generate-report.py"
        cls.comparator = scripts / "compare-summaries.sh"
        cls.publisher = scripts / "publish-results.sh"

    def test_complete_suite_generates_html_and_three_tsv_snapshots(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run_directories = self._write_suite(root / "runs")
            output_html = root / "benchmark-report.html"
            candidate_dir = root / "candidate"

            result = self._generate(output_html, candidate_dir, run_directories)

            self.assertEqual(0, result.returncode, result.stderr)
            self.assertTrue(output_html.is_file())
            self.assertEqual(
                {"samples.tsv", "summary.tsv", "topology.tsv"},
                {path.name for path in candidate_dir.iterdir()},
            )
            with (candidate_dir / "samples.tsv").open(
                encoding="utf-8", newline=""
            ) as stream:
                samples = list(csv.DictReader(stream, delimiter="\t"))
            self.assertEqual(20, len(samples))
            self.assertEqual({"formal"}, {row["run_kind"] for row in samples})

            with (candidate_dir / "summary.tsv").open(
                encoding="utf-8", newline=""
            ) as stream:
                summaries = list(csv.DictReader(stream, delimiter="\t"))
            self.assertEqual(list(ALGORITHMS), [row["algorithm"] for row in summaries])
            self.assertEqual({"5"}, {row["successful_samples"] for row in summaries})

            with (candidate_dir / "topology.tsv").open(
                encoding="utf-8", newline=""
            ) as stream:
                reader = csv.DictReader(stream, delimiter="\t")
                topology_columns = tuple(reader.fieldnames or ())
                topology = list(reader)
            self.assertEqual(24, len(topology))
            self.assertIn("sentinel_role", topology_columns)
            self.assertIn("path_root_kind", topology_columns)
            self.assertIn("path_root_cg_node_identity", topology_columns)
            self.assertNotIn("entrypoint_cg_node_identity", topology_columns)
            self.assertNotIn("path_status", topology_columns)
            self.assertEqual(
                {"CALLER", "CALLEE"}, {row["direction"] for row in topology}
            )
            self.assertEqual(
                {"RANKED_CGNODE", "RELATED_IMETHOD", "REACHABILITY_PATH"},
                {row["record_type"] for row in topology},
            )
            self.assertEqual(
                {"", "deadbeef"}, {row["source_sha256"] for row in topology}
            )
            self.assertTrue(all(row["ir_sha256"] == "feedface" for row in topology))
            path_rows = [
                row for row in topology
                if row["record_type"] == "REACHABILITY_PATH"
            ]
            self.assertTrue(all(" -> " in row["shortest_path"] for row in path_rows))
            self.assertTrue(any(
                "[FAKE_ROOT]" in row["shortest_path"]
                and "[FAKE_WORLD_CLINIT]" in row["shortest_path"]
                for row in path_rows
            ))
            self.assertEqual(8, len(path_rows))
            self.assertEqual(
                {"DECLARED_ENTRYPOINT", "FAKE_ROOT"},
                {row["path_root_kind"] for row in path_rows},
            )
            self.assertTrue(all(row["path_root_cg_node_identity"] for row in path_rows))
            self.assertEqual(
                {"NONE", "FAKE_WORLD_CLINIT"},
                {row["sentinel_role"] for row in topology},
            )
            child_rows = [
                row for row in topology if row["record_type"] == "RELATED_IMETHOD"
            ]
            self.assertEqual({"12"}, {row["child_related_cg_node_count"]
                                     for row in child_rows})
            self.assertEqual(
                {"2"}, {row["omitted_related_cg_node_count"]
                        for row in child_rows}
            )

            report = output_html.read_text(encoding="utf-8")
            for algorithm in ALGORITHMS:
                self.assertIn(
                    f'<section id="{algorithm}" '
                    f'class="tab-panel panel-{algorithm}">',
                    report,
                )
                self.assertIn(f'id="tab-{algorithm}"', report)
            self.assertEqual(5, report.count('name="report-tab"'))
            self.assertIn('id="tab-comparison"', report)
            self.assertIn("Top 10 caller CGNode", report)
            self.assertIn("Top 10 callee CGNode", report)
            self.assertIn("Top 10 callee IMethod", report)
            self.assertIn("Top 10 caller IMethod", report)
            self.assertIn("Source — DECOMPILED", report)
            self.assertIn("Source — UNAVAILABLE", report)
            self.assertIn(
                "WALA synthetic/summary Method has no bytecode source", report
            )
            self.assertIn("WALA IR — AVAILABLE", report)
            self.assertIn("WALA SYNTHETIC", report)
            self.assertIn("10 / 12 CGNode Context examples；omitted 2", report)
            self.assertEqual(
                8, report.count("Declared entrypoint shortest CGNode chains")
            )
            self.assertEqual(
                8,
                report.count("<h5>WALA sentinel shortest CGNode chains</h5>"),
            )
            self.assertEqual(
                4,
                report.count("请查看 WALA sentinel shortest CGNode chains"),
            )
            self.assertNotIn("UNREACHABLE_FROM_DECLARED_ENTRYPOINTS", report)
            self.assertIn(
                '<span class="badge sentinel">FAKE_ROOT</span>', report
            )
            self.assertIn(
                '<span class="badge sentinel">FAKE_WORLD_CLINIT</span>', report
            )
            caller_start = report.index("<h3>Top 10 caller CGNode")
            callee_start = report.index("<h3>Top 10 callee CGNode", caller_start)
            caller_section = report[caller_start:callee_start]
            self.assertIn(
                "Declared entrypoint shortest CGNode chains", caller_section
            )
            self.assertIn(
                "WALA sentinel shortest CGNode chains", caller_section
            )
            self.assertEqual(20, report.count("<td>SUCCESS</td>"))
            self.assertIn("Algorithm comparison", report)
            self.assertIn("Wall / ZeroCFA", report)
            self.assertIn("&lt;danger&gt;&amp;&quot;", report)
            self.assertIn("&lt;ir&gt;&amp;&quot;", report)
            self.assertNotIn('<danger>&"', report)
            self.assertNotIn('<ir>&"', report)
            self.assertNotIn("<script", report.lower())
            self.assertNotIn("points-to set", report.lower())

    def test_topology_drift_fails_without_touching_tracked_snapshots(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run_directories = self._write_suite(
                root / "runs", drift_algorithm="rta"
            )
            output_html = root / "benchmark-report.html"
            candidate_dir = root / "candidate"
            tracked_dir = root / "tracked-results"
            tracked_dir.mkdir()
            tracked_snapshots = {}
            for name in ("samples.tsv", "summary.tsv", "topology.tsv"):
                snapshot = tracked_dir / name
                snapshot.write_text(f"previous-{name}\n", encoding="utf-8")
                tracked_snapshots[name] = snapshot.read_bytes()

            result = self._generate(output_html, candidate_dir, run_directories)

            self.assertNotEqual(0, result.returncode)
            self.assertIn("TOPOLOGY_DRIFT", result.stderr)
            self.assertTrue(output_html.is_file())
            self.assertIn("TOPOLOGY_DRIFT", output_html.read_text(encoding="utf-8"))
            for name, expected in tracked_snapshots.items():
                self.assertEqual(expected, (tracked_dir / name).read_bytes())

    def test_schema_v2_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run_directories = self._write_suite(root / "runs")
            topology_path = run_directories[0] / "topology.json"
            topology = json.loads(topology_path.read_text(encoding="utf-8"))
            topology["schemaVersion"] = 2
            topology_path.write_text(json.dumps(topology), encoding="utf-8")

            result = self._generate(
                root / "benchmark-report.html", root / "candidate", run_directories
            )

            self.assertNotEqual(0, result.returncode)
            self.assertIn("unsupported topology schema", result.stderr)

    def test_history_comparison_reports_absolute_change_and_ratio(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run_directories = self._write_suite(root / "runs")
            candidate_dir = root / "candidate"
            generated = self._generate(
                root / "benchmark-report.html", candidate_dir, run_directories
            )
            self.assertEqual(0, generated.returncode, generated.stderr)
            summary = candidate_dir / "summary.tsv"

            result = subprocess.run(
                [str(self.comparator), str(summary), str(summary)],
                check=False,
                capture_output=True,
                encoding="utf-8",
            )

            self.assertEqual(0, result.returncode, result.stderr)
            rows = list(csv.DictReader(result.stdout.splitlines(), delimiter="\t"))
            self.assertEqual(24, len(rows))
            self.assertEqual({"0.000000"}, {row["absolute_change"] for row in rows})
            self.assertEqual({"1.000000"}, {row["ratio"] for row in rows})

            incompatible = root / "incompatible-summary.tsv"
            incompatible.write_text(
                summary.read_text(encoding="utf-8").replace(
                    REFLECTION_OPTIONS, "NONE", 1
                ),
                encoding="utf-8",
            )
            rejected = subprocess.run(
                [str(self.comparator), str(summary), str(incompatible)],
                check=False,
                capture_output=True,
                encoding="utf-8",
            )
            self.assertNotEqual(0, rejected.returncode)
            self.assertIn("ReflectionOptions differ", rejected.stderr)

    def test_atomic_publisher_validates_all_candidates_before_replacement(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            candidate_dir = root / "candidate"
            tracked_dir = root / "tracked"
            candidate_dir.mkdir()
            tracked_dir.mkdir()
            names = ("samples.tsv", "summary.tsv", "topology.tsv")
            for name in names:
                (tracked_dir / name).write_text(f"old-{name}\n", encoding="utf-8")
            for name in names[:2]:
                (candidate_dir / name).write_text(f"new-{name}\n", encoding="utf-8")

            rejected = subprocess.run(
                [str(self.publisher), str(candidate_dir), str(tracked_dir)],
                check=False,
                capture_output=True,
                encoding="utf-8",
            )

            self.assertNotEqual(0, rejected.returncode)
            for name in names:
                self.assertEqual(
                    f"old-{name}\n", (tracked_dir / name).read_text(encoding="utf-8")
                )

            (candidate_dir / names[2]).write_text(
                f"new-{names[2]}\n", encoding="utf-8"
            )
            published = subprocess.run(
                [str(self.publisher), str(candidate_dir), str(tracked_dir)],
                check=False,
                capture_output=True,
                encoding="utf-8",
            )
            self.assertEqual(0, published.returncode, published.stderr)
            for name in names:
                self.assertEqual(
                    f"new-{name}\n", (tracked_dir / name).read_text(encoding="utf-8")
                )

    def _generate(
        self,
        output_html: Path,
        candidate_dir: Path,
        run_directories: list[Path],
    ) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                sys.executable,
                str(self.generator),
                "--output-html",
                str(output_html),
                "--candidate-dir",
                str(candidate_dir),
                *(str(path) for path in run_directories),
            ],
            check=False,
            capture_output=True,
            encoding="utf-8",
        )

    def _write_suite(
        self,
        root: Path,
        drift_algorithm: str | None = None,
    ) -> list[Path]:
        run_directories: list[Path] = []
        for algorithm in ALGORITHMS:
            warmup = root / f"warmup-{algorithm}"
            self._write_metrics(warmup, algorithm, "warmup", 0, 0)
            self._write_topology(warmup, algorithm)
            run_directories.append(warmup)
        for sample in range(1, 6):
            rotation = (sample - 1) % len(ALGORITHMS)
            order = ALGORITHMS[rotation:] + ALGORITHMS[:rotation]
            for algorithm in order:
                run = root / f"formal-{sample}-{algorithm}"
                drift = algorithm == drift_algorithm and sample == 3
                self._write_metrics(run, algorithm, "formal", sample, sample, drift)
                run_directories.append(run)
        return run_directories

    def _write_metrics(
        self,
        run_directory: Path,
        algorithm: str,
        run_kind: str,
        round_number: int,
        sample: int,
        drift: bool = False,
    ) -> None:
        logs = run_directory / "logs"
        logs.mkdir(parents=True, exist_ok=True)
        entrypoints, nodes, edges = GRAPH_COUNTS[algorithm]
        if drift:
            nodes += 1
        algorithm_offset = ALGORITHMS.index(algorithm)
        row = {
            "label": run_directory.name,
            "run_kind": run_kind,
            "round": str(round_number),
            "sample": str(sample),
            "algorithm": algorithm,
            "wala_reflection_options": REFLECTION_OPTIONS,
            "total_wall_seconds": f"{4 + algorithm_offset + sample / 10:.3f}",
            "call_graph_seconds": f"{1 + algorithm_offset + sample / 100:.3f}",
            "peak_heap_used_mib": f"{128 + algorithm_offset * 16 + sample:.3f}",
            "peak_heap_committed_mib": f"{256 + algorithm_offset * 16 + sample:.3f}",
            "heap_max_mib": "4096.000",
            "heap_sample_count": "20",
            "process_tree_peak_rss_kib": str(300000 + algorithm_offset * 10000),
            "entrypoint_count": str(entrypoints),
            "cg_node_count": str(nodes),
            "cg_edge_count": str(edges),
            "status": "SUCCESS",
            "exit_code": "0",
            "analyzer_sha256": "analyzer-sha256",
            "git_commit": "0123456789abcdef",
            "git_dirty": "false",
            "os": "TestOS 1",
            "architecture": "test-arch",
            "analyzer_java": "openjdk version 17-test",
            "jdk": "openjdk version 1.8-test",
            "maven": "Apache Maven 3-test",
        }
        with (logs / "metrics.tsv").open("w", encoding="utf-8", newline="") as stream:
            writer = csv.DictWriter(
                stream,
                fieldnames=SAMPLE_COLUMNS,
                delimiter="\t",
                lineterminator="\n",
            )
            writer.writeheader()
            writer.writerow(row)

    def _write_topology(self, run_directory: Path, algorithm: str) -> None:
        entrypoints, nodes, edges = GRAPH_COUNTS[algorithm]
        entrypoint = self._method(
            "example/Entrypoint", "main", "([Ljava/lang/String;)V", "PROJECT"
        )
        entrypoint_node = self._node(1, entrypoint, "Everywhere")
        caller_node = self._node(
            2, self._method("example/Caller", "call", "()V", "PROJECT"),
            "CallerContext",
        )
        callee_node = self._node(
            3, self._method("example/Callee", "run", "()V", "SYNTHETIC"),
            "ReceiverContext",
        )
        caller_node["sentinelRole"] = "FAKE_WORLD_CLINIT"
        caller_node["identity"] = str(caller_node["identity"]).replace(
            "sentinelRole=NONE", "sentinelRole=FAKE_WORLD_CLINIT"
        )
        fake_root = self._node(
            0,
            self._method(
                "com/ibm/wala/FakeRoot", "fakeRootMethod", "()V", "SYNTHETIC"
            ),
            "Everywhere",
        )
        fake_root["sentinelRole"] = "FAKE_ROOT"
        fake_root["identity"] = str(fake_root["identity"]).replace(
            "sentinelRole=NONE", "sentinelRole=FAKE_ROOT"
        )
        caller = self._ranked_node(
            1, caller_node, fake_root, "FAKE_ROOT", callee_node, "topCallees", True,
        )
        callee = self._ranked_node(
            1, callee_node, entrypoint_node, "DECLARED_ENTRYPOINT", caller_node,
            "topCallers", False,
        )
        topology = {
            "schemaVersion": 3,
            "algorithm": algorithm,
            "reflectionOptions": REFLECTION_OPTIONS,
            "jdk": "1.8-test",
            "modules": [
                {
                    "module": "fixture:app:1.0",
                    "entrypointCount": entrypoints,
                    "cgNodeCount": nodes,
                    "cgEdgeCount": edges,
                    "topCallers": [caller],
                    "topCallees": [callee],
                }
            ],
        }
        (run_directory / "topology.json").write_text(
            json.dumps(topology), encoding="utf-8"
        )

    @staticmethod
    def _method(
        owner: str,
        name: str,
        descriptor: str,
        origin: str,
    ) -> dict[str, str]:
        return {
            "owner": owner,
            "name": name,
            "descriptor": descriptor,
            "origin": origin,
            "identity": f"{owner}#{name}{descriptor}@{origin}",
        }

    @staticmethod
    def _node(
        node_id: int,
        method: dict[str, str],
        context: str,
    ) -> dict[str, object]:
        return {
            "graphNodeId": node_id,
            "context": context,
            "walaSynthetic": method["origin"] == "SYNTHETIC",
            "sentinelRole": "NONE",
            "identity": (
                f"{method['identity']}|context={context}|nodeId={node_id}"
                f"|walaSynthetic={str(method['origin'] == 'SYNTHETIC').lower()}"
                "|sentinelRole=NONE"
            ),
            "method": method,
        }

    @staticmethod
    def _ranked_node(
        rank: int,
        node: dict[str, object],
        root: dict[str, object],
        root_kind: str,
        related: dict[str, object],
        child_field: str,
        source_available: bool,
    ) -> dict[str, object]:
        related_examples = [
            {
                **related,
                "graphNodeId": int(related["graphNodeId"]) + offset,
                "identity": f"{related['identity']}|example={offset}",
            }
            for offset in range(10)
        ]
        return {
            "rank": rank,
            "relatedDirection": "CALLEE",
            "node": node,
            "relatedCgNodeCount": 1,
            "distinctRelatedMethodCount": 7,
            "rawEdgeCount": 9,
            "cycle": True,
            child_field: [
                {
                    "rank": 1,
                    "method": related["method"],
                    "relatedCgNodeCount": 12,
                    "rawEdgeCount": 12,
                    "omittedRelatedCgNodeCount": 2,
                    "relatedCgNodeExamples": related_examples,
                }
            ],
            "source": {
                "status": "DECOMPILED" if source_available else "UNAVAILABLE",
                "sha256": "deadbeef" if source_available else "",
                "classpathSource": "fixture.jar" if source_available else "",
                "reason": "" if source_available
                else "WALA synthetic/summary Method has no bytecode source",
                "text": '<danger>&"' if source_available else "",
            },
            "ir": {
                "status": "AVAILABLE",
                "sha256": "feedface",
                "reason": "",
                "text": '<ir>&"',
            },
            "reachabilityPaths": [
                {
                    "rootKind": root_kind,
                    "root": root,
                    "steps": [
                        {**root, "cycle": False},
                        {**node, "cycle": True},
                    ],
                }
            ],
        }


if __name__ == "__main__":
    unittest.main()
