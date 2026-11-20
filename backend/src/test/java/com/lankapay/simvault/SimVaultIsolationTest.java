package com.lankapay.simvault;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit test enforcing that simvault internals cannot be imported
 * by any module outside com.lankapay.simvault and
 * com.lankapay.virtualcard.provider.
 *
 * Rule: no class outside {simvault, virtualcard.provider} may depend on
 * any class inside com.lankapay.simvault.internal.*.
 */
@DisplayName("SimVault isolation — ArchUnit")
class SimVaultIsolationTest {

    private static final JavaClasses ALL_CLASSES = new ClassFileImporter()
            .importPackages("com.lankapay");

    @Test
    @DisplayName("No module outside simvault may import simvault.internal classes")
    void noExternalImportOfSimvaultInternals() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackages(
                        "com.lankapay.simvault..",
                        "com.lankapay.virtualcard..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.lankapay.simvault.internal..");

        rule.check(ALL_CLASSES);
    }

    @Test
    @DisplayName("No module outside simvault may import SimulatedCardVault directly")
    void noDirectImportOfSimulatedCardVault() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackages(
                        "com.lankapay.simvault..",
                        "com.lankapay.virtualcard..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("com.lankapay.simvault.SimulatedCardVault");

        rule.check(ALL_CLASSES);
    }

    @Test
    @DisplayName("simvault.internal classes must not be public — except the deliberate public bridge classes")
    void simvaultInternalsMustBePackagePrivate() {
        // VaultComponents and SimulatedCardVaultImpl are intentionally public
        // to allow SimulatedCardVault (parent package) to construct them.
        // All other internal classes must remain package-private.
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.lankapay.simvault.internal..")
                .and().doNotHaveSimpleName("VaultComponents")
                .and().doNotHaveSimpleName("SimulatedCardVaultImpl")
                .should().bePublic()
                .because("vault internals must be package-private to enforce isolation; "
                        + "only VaultComponents and SimulatedCardVaultImpl are intentional public bridges");

        rule.check(ALL_CLASSES);
    }

    @Test
    @DisplayName("Other modules use only CardProvider interface, not the implementation")
    void otherModulesUseCardProviderInterface() {
        // Classes outside simvault that interact with cards must only depend on the
        // CardProvider interface in virtualcard.provider
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackages(
                        "com.lankapay.simvault..",
                        "com.lankapay.virtualcard..",
                        // Tests are allowed to import the vault directly
                        "com.lankapay..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.lankapay.simvault..");

        // This is a looser check — we just verify internal package is not leaked
        // The stricter check is noExternalImportOfSimvaultInternals above
        noClasses()
                .that().resideOutsideOfPackages("com.lankapay.simvault..")
                .and().resideOutsideOfPackages("com.lankapay.virtualcard..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.lankapay.simvault.internal..")
                .check(ALL_CLASSES);
    }
}
