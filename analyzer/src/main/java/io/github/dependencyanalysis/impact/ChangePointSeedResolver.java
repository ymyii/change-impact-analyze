package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.ChangePointKind;

/** Strategy resolving one family of ChangePoints into typed seeds. */
interface ChangePointSeedResolver {

    /**
     * @param kind ChangePoint kind
     * @return true when this is the unique resolver for the kind
     */
    boolean supports(ChangePointKind kind);

    /**
     * @param request read-only resolution input
     * @return typed resolution
     */
    ChangePointSeedResolution resolve(ChangePointSeedRequest request);
}
