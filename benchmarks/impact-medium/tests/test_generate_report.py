"""Contract tests for the CHA-only canonical benchmark report."""

from __future__ import annotations

import csv
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).parents[1] / "scripts" / "generate-report.py"
SPEC = importlib.util.spec_from_file_location("generate_report", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
REPORT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(REPORT)


class GenerateReportTest(unittest.TestCase):
    def test_canonical_suite_writes_cha_only_snapshots(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            runs = self._write_suite(root / "runs")
            output_html = root / "report.html"
            candidate_dir = root / "candidate"

            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--output-html",
                    str(output_html),
                    "--candidate-dir",
                    str(candidate_dir),
                    "--scope",
                    "changed-paths",
                    *(str(run) for run in runs),
                ],
                check=False,
                capture_output=True,
                text=True,
            )

            self.assertEqual(0, result.returncode, result.stderr)
            samples = self._read_tsv(candidate_dir / "samples.tsv")
            summaries = self._read_tsv(candidate_dir / "summary.tsv")
            topology = self._read_tsv(candidate_dir / "topology.tsv")
            self.assertEqual(5, len(samples))
            self.assertEqual({"cha"}, {row["algorithm"] for row in samples})
            self.assertEqual(["cha"], [row["algorithm"] for row in summaries])
            self.assertNotIn("k_obj_depth", samples[0])
            self.assertNotIn("result_refinement_algorithms", samples[0])
            self.assertNotIn("wall_vs_zero_cfa", summaries[0])
            self.assertEqual({"cha"}, {row["algorithm"] for row in topology})
            self.assertEqual(
                {"second-stage-on-java-miss"},
                {row["ssa_equivalence"] for row in topology},
            )
            self.assertEqual(
                {"first-stage-short-circuit"},
                {row["decompiled_java_equivalence"] for row in topology},
            )
            document = output_html.read_text(encoding="utf-8")
            self.assertIn("canonical algorithm 固定为 CHA", document)
            self.assertNotIn("Algorithm comparison", document)
            self.assertNotIn('name="report-tab"', document)

    def test_non_cha_sample_fails_contract(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            runs = self._write_suite(root / "runs")
            metrics = runs[1] / "logs" / "metrics.tsv"
            rows = self._read_tsv(metrics)
            rows[0]["algorithm"] = "k-obj"
            self._write_tsv(metrics, REPORT.SAMPLE_COLUMNS, rows)

            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--output-html",
                    str(root / "report.html"),
                    "--candidate-dir",
                    str(root / "candidate"),
                    "--scope",
                    "changed-paths",
                    *(str(run) for run in runs),
                ],
                check=False,
                capture_output=True,
                text=True,
            )

            self.assertEqual(1, result.returncode)
            self.assertIn("canonical benchmark 只接受 cha", result.stderr)

    def test_suite_shape_is_one_warmup_and_five_formal(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runs = self._write_suite(Path(temporary) / "runs")
            rows = [REPORT.read_metrics(run) for run in runs]
            by_kind = {
                kind: sum(row["run_kind"] == kind for row in rows)
                for kind in ("warmup", "formal")
            }
            self.assertEqual({"warmup": 1, "formal": 5}, by_kind)

    def _write_suite(self, root: Path) -> list[Path]:
        runs: list[Path] = []
        shapes = [("warmup", 0),
                  *(("formal", value) for value in range(1, 6))]
        for index, (run_kind, sample) in enumerate(shapes):
            run = root / f"run-{index}"
            run.joinpath("logs").mkdir(parents=True)
            row = self._sample_row(run_kind, sample)
            self._write_tsv(
                run / "logs" / "metrics.tsv", REPORT.SAMPLE_COLUMNS, [row]
            )
            if run_kind == "warmup":
                (run / "topology.json").write_text(
                    json.dumps(self._topology()), encoding="utf-8"
                )
            runs.append(run)
        return runs

    @staticmethod
    def _sample_row(run_kind: str, sample: int) -> dict[str, str]:
        row = {column: "" for column in REPORT.SAMPLE_COLUMNS}
        row.update({
            "label": f"cha-{run_kind}-{sample}",
            "run_kind": run_kind,
            "round": str(sample),
            "sample": str(sample),
            "dependency_analysis_scope": "changed-paths",
            "algorithm": "cha",
            "jdk_model": "none",
            "wala_reflection_options": REPORT.REFLECTION_DEFAULT,
            "total_wall_seconds": str(2 + sample / 10),
            "call_graph_seconds": str(1 + sample / 10),
            "peak_heap_used_mib": str(300 + sample),
            "peak_heap_committed_mib": "512",
            "heap_max_mib": "1024",
            "heap_sample_count": "10",
            "process_tree_peak_rss_kib": str(1000 + sample),
            "entrypoint_count": "2",
            "cg_node_count": "3",
            "cg_edge_count": "4",
            "real_external_artifact_count": "1",
            "no_op_external_artifact_count": "0",
            "real_external_method_node_count": "1",
            "no_op_method_node_count": "0",
            "factory_method_node_count": "0",
            "dangerous_transfer_count": "0",
            "status": "SUCCESS",
            "exit_code": "0",
            "analyzer_sha256": "sha",
            "git_commit": "commit",
            "git_dirty": "false",
            "os": "test-os",
            "architecture": "test-arch",
            "analyzer_java": "java-17",
            "jdk": "jdk-8",
            "maven": "maven",
        })
        return row

    @staticmethod
    def _topology() -> dict[str, object]:
        return {
            "schemaVersion": 13,
            "algorithm": "cha",
            "kObjDepth": None,
            "reflectionOptions": REPORT.REFLECTION_DEFAULT,
            "reflectionApplied": "not applied by cha",
            "jdkModel": "none",
            "ssaEquivalence": {"enabled": True, "fixed": True},
            "impactPathPruningExtensions": [
                "cha-local-receiver-inference",
            ],
            "requestedDependencyAnalysisScope": "changed-paths",
            "modules": [{
                "module": "fixture",
                "entrypointCount": 2,
                "cgNodeCount": 3,
                "cgEdgeCount": 4,
                "actualDependencyAnalysisScope": "changed-paths",
                "realExternalArtifactCount": 1,
                "noOpExternalArtifactCount": 0,
                "realExternalMethodNodeCount": 1,
                "noOpMethodNodeCount": 0,
                "factoryMethodNodeCount": 0,
                "dangerousTransferCount": 0,
                "ancestorRetainedExternalTypeCount": 1,
                "ancestorRetainedExternalMethodNodeCount": 1,
                "prunedExternalMethodTargetCount": 1,
                "jdkDeclaredDispatchPrunedTargetCount": 2,
                "changePointCollection": {
                    "ssaEquivalence": {
                        "enabled": True,
                        "fixed": True,
                        "evaluationOrder": 2,
                        "shortCircuitedBy":
                            "DECOMPILED_JAVA_TEXT_IDENTICAL",
                        "eligible": 0,
                        "executed": 0,
                        "skipped": 0,
                        "matchedSuppressed": 0,
                        "different": 0,
                        "unknown": 0,
                        "elapsedMillis": 0,
                        "comparisons": [],
                    },
                    "decompiledJavaEquivalence": {
                        "enabled": True,
                        "fixed": True,
                        "evaluationOrder": 1,
                        "shortCircuitWhen": "IDENTICAL",
                        "eligible": 0,
                        "identical": 0,
                        "different": 0,
                        "unknown": 0,
                        "elapsedMillis": 0,
                        "comparisons": [],
                    },
                },
                "dependencyPaths": [{"seed": "seed", "path": "a -> b"}],
                "topCallers": [],
                "topCallees": [],
            }],
        }

    @staticmethod
    def _write_tsv(
        path: Path, columns: tuple[str, ...], rows: list[dict[str, str]]
    ) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("w", encoding="utf-8", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=columns, delimiter="\t")
            writer.writeheader()
            writer.writerows(rows)

    @staticmethod
    def _read_tsv(path: Path) -> list[dict[str, str]]:
        with path.open(encoding="utf-8", newline="") as stream:
            return list(csv.DictReader(stream, delimiter="\t"))


if __name__ == "__main__":
    unittest.main()
