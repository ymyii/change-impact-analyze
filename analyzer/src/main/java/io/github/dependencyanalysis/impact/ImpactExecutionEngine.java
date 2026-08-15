package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.workspace.WorkspaceResult;

// Wiki: wiki/architecture/dependency-analysis-pipelines.md
// - Impact execution boundary
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
