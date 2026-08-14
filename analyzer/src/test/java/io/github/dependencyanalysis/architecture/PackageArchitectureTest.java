package io.github.dependencyanalysis.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

// Wiki: wiki/rules/package-boundaries.md - Executable package boundaries
/** Enforces Call Graph responsibility and dependency direction. */
class PackageArchitectureTest {

    /** Imported production classes. */
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.github.dependencyanalysis");

    @Test
    void callGraphDoesNotDependOnBusinessOrReportPackages() {
        final ArchRule rule = noClasses()
                .that().resideInAPackage("..callgraph..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..impact..", "..report..");
        rule.check(CLASSES);
    }

    @Test
    void strategiesRemainMutuallyIsolated() {
        noClasses().that().resideInAPackage("..strategy.cha..")
                .should().dependOnClassesThat()
                .resideInAPackage("..strategy.kobj..")
                .check(CLASSES);
        noClasses().that().resideInAPackage("..strategy.kobj..")
                .should().dependOnClassesThat()
                .resideInAPackage("..strategy.cha..")
                .check(CLASSES);
    }

    @Test
    void commonProtocolDoesNotDependOnStrategyOrEngine() {
        noClasses().that().resideInAPackage("..callgraph.protocol..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..callgraph.strategy..",
                        "..callgraph.engine..")
                .check(CLASSES);
    }

    @Test
    void algorithmAdaptersStayUnderTheirStrategy() {
        classes().that().haveSimpleNameStartingWith("KObj")
                .should().resideInAPackage("..strategy.kobj..")
                .check(CLASSES);
    }

    @Test
    void callGraphRootContainsNoProductionClass() {
        assertThat(CLASSES.stream().filter(value -> value.getPackageName()
                        .equals("io.github.dependencyanalysis.callgraph"))
                .filter(value -> !value.getSimpleName()
                        .equals("package-info")))
                .isEmpty();
    }

    @Test
    void reportDoesNotDependOnLiveStrategyImplementations() {
        noClasses().that().resideInAPackage("..report..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..strategy.cha..",
                        "..strategy.kobj..")
                .check(CLASSES);
    }
}
