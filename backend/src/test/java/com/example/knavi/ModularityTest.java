package com.example.knavi;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** モジュール境界（循環依存・内部パッケージへの参照）を検証する。ADR-0001 */
class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(KnowledgeNavigatorApplication.class);

    @Test
    void verifiesModularStructure() {
        modules.verify();
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(modules).writeDocumentation();
    }
}
