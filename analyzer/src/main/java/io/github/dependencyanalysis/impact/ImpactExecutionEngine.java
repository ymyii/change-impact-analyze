package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.workspace.WorkspaceResult;

// Wiki: wiki/c4/components/dependency-analyzer-cli-impact-tracing.md - 接口
/** Command-level Impact analysis engine. */
public interface ImpactExecutionEngine {

    /**
     * Runs the complete Impact analysis before Report publication.
     *
     * @param workspace prepared baseline and target workspaces
     * @return complete frozen-capable analysis result
     * @throws Exception when command-level preparation fails
     */
    AnalysisRunResult run(WorkspaceResult workspace) throws Exception;
}
