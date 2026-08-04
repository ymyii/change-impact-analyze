package io.github.dependencyanalysis.impact;

/** Final disposition of one module-bound ChangePoint. */
public enum ChangePointDisposition {

    /** At least one final Impact Path was reported. */
    IMPACT_REPORTED,

    /** Candidate paths were removed by proven SSA equivalence. */
    FILTERED_EQUIVALENT,

    /** The change kind is outside the active impact model. */
    CHANGE_KIND_NOT_ANALYZED,

    /** A target-side member could not be resolved. */
    TARGET_NOT_FOUND,

    /** No reachable old symbolic reference was found. */
    DECLARED_REFERENCE_NOT_FOUND,

    /** A seed exists but no current PROJECT method reaches it. */
    NO_PROJECT_PATH,

    /** A class reference could not be attributed to a method. */
    UNATTRIBUTABLE_CLASS_REFERENCE,

    /** Structural metadata exists without reachability evidence. */
    UNREACHABLE_STRUCTURAL_REFERENCE
}
