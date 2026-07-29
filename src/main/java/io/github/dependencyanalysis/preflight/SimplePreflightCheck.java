package io.github.dependencyanalysis.preflight;

import java.util.List;
import java.util.Objects;

/** Immutable check backed by a callback. */
public final class SimplePreflightCheck
        implements PreflightCheck {

    /** Callback contract. */
    @FunctionalInterface
    public interface Action {
        /**
         * Executes an atomic probe.
         *
         * @param context prepared context
         * @return outcome
         * @throws Exception on probe failure
         */
        PreflightOutcome run(
                PreflightContext context)
                throws Exception;
    }

    /** Check identifier. */
    private final String checkId;

    /** Command name. */
    private final String command;

    /** Scope. */
    private final PreflightScope scope;

    /** Scope identifier. */
    private final String scopeId;

    /** Requirement. */
    private final PreflightRequirement requirement;

    /** Dependencies. */
    private final List<String> dependsOn;

    /** Probe. */
    private final Action action;

    /**
     * Creates a check.
     *
     * @param id check id
     * @param commandName command name
     * @param checkScope scope
     * @param checkScopeId scope id
     * @param checkRequirement requirement
     * @param dependencies dependencies
     * @param checkAction probe callback
     */
    public SimplePreflightCheck(
            final String id,
            final String commandName,
            final PreflightScope checkScope,
            final String checkScopeId,
            final PreflightRequirement checkRequirement,
            final List<String> dependencies,
            final Action checkAction) {
        checkId = Objects.requireNonNull(id, "id");
        command = Objects.requireNonNull(
                commandName, "commandName");
        scope = Objects.requireNonNull(
                checkScope, "scope");
        scopeId = Objects.requireNonNull(
                checkScopeId, "scopeId");
        requirement = Objects.requireNonNull(
                checkRequirement, "requirement");
        dependsOn = List.copyOf(dependencies);
        action = Objects.requireNonNull(
                checkAction, "action");
    }

    @Override
    public String getCheckId() {
        return checkId;
    }

    @Override
    public String getCommand() {
        return command;
    }

    @Override
    public PreflightScope getScope() {
        return scope;
    }

    @Override
    public String getScopeId() {
        return scopeId;
    }

    @Override
    public PreflightRequirement getRequirement() {
        return requirement;
    }

    @Override
    public List<String> getDependsOn() {
        return dependsOn;
    }

    @Override
    public PreflightOutcome execute(
            final PreflightContext context)
            throws Exception {
        return action.run(context);
    }
}
