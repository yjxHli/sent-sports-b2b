package com.sent.sports;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 分层架构测试（P1-19）：用 ArchUnit 强约束模块化单体边界，防止耦合倒灌。
 * 1) controller 不得直接依赖 mapper；
 * 2) domain 不得带任何 Spring 注解（保持纯 POJO，可单测）；
 * 3) common 不得依赖任何业务模块；
 * 4) 跨模块调用只走对方 service 接口（controller/service/event 不得依赖其他模块的 mapper/domain/controller）。
 */
class ArchitectureTest {

    private static final String BASE = "com.sent.sports";
    private static final String[] MODULES = {
            "auth", "system", "customer", "member", "product", "stock", "cart",
            "order", "payment", "refund", "customize", "finance", "chat", "notify", "report", "job"
    };

    private static JavaClasses classes;

    @BeforeAll
    static void init() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(BASE);
    }

    /** 规则1：controller 不依赖 mapper。 */
    @Test
    void controllerMustNotDependOnMapper() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..mapper..");
        rule.check(classes);
    }

    /** 规则2：domain 不得带 Spring 注解。 */
    @Test
    void domainMustNotHaveSpringAnnotations() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().beAnnotatedWith(Component.class)
                .orShould().beAnnotatedWith(Service.class)
                .orShould().beAnnotatedWith(Repository.class)
                .orShould().beAnnotatedWith(Controller.class)
                .orShould().beAnnotatedWith(RestController.class)
                .orShould().beAnnotatedWith(Configuration.class);
        rule.check(classes);
    }

    /** 规则3：common 不依赖任何业务模块。 */
    @Test
    void commonMustNotDependOnBusinessModules() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + ".common..")
                .should().dependOnClassesThat().resideInAnyPackage(businessPackages());
        rule.check(classes);
    }

    /** 规则4：跨模块只走 service（controller/service/event 不得依赖其他模块的 mapper/domain/controller）。 */
    @Test
    void crossModuleOnlyViaService() {
        for (String module : MODULES) {
            String self = BASE + "." + module;
            ArchRule rule = noClasses()
                    .that().resideInAPackage(self + "..")
                    .and().resideInAnyPackage("..controller..", "..service..", "..event..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(otherModuleForbiddenPackages(module));
            rule.check(classes);
        }
    }

    private static String[] businessPackages() {
        String[] pkgs = new String[MODULES.length];
        for (int i = 0; i < MODULES.length; i++) {
            pkgs[i] = BASE + "." + MODULES[i] + "..";
        }
        return pkgs;
    }

    private static String[] otherModuleForbiddenPackages(String selfModule) {
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String m : MODULES) {
            if (m.equals(selfModule)) {
                continue;
            }
            String base = BASE + "." + m;
            list.add(base + "..mapper..");
            list.add(base + "..domain..");
            list.add(base + "..controller..");
        }
        return list.toArray(new String[0]);
    }
}
