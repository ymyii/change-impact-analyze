package io.github.dependencyanalysis.preflight;

import java.util.List;

/** Atomic preflight check in a dependency DAG. */
public interface PreflightCheck {

    /** @return stable check identifier */
    String getCheckId();

    /** @return root command name */
    String getCommand();

    /** @return check scope */
    PreflightScope getScope();

    /** @return stable scope identifier */
    String getScopeId();

    /** @return requirement */
    PreflightRequirement getRequirement();

    /** @return prerequisite check identifiers */
    List<String> getDependsOn();

    /**
     * Executes the check.
     *
     * @param context prepared context
     * @return outcome
     * @throws Exception on probe failure
     */
    PreflightOutcome execute(
            PreflightContext context)
            throws Exception;
}
