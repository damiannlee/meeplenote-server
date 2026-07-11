package com.meeplenote.architecture

import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices

/**
 * ADR-001(모듈러 모놀리스)의 "모듈 간 접근은 공개 인터페이스로만"과
 * ADR-009(패키지 규약 + ArchUnit)를 빌드 게이트로 강제하는 테스트.
 *
 * 규약:
 *   com.meeplenote.<module>.api      → 다른 모듈이 접근 가능한 공개 계약 (인터페이스, DTO)
 *   com.meeplenote.<module>.internal → 모듈 내부 구현. 외부 접근 시 빌드 실패.
 *
 * 이 테스트가 깨지면 "코드가 틀린 것"이 아니라 "절단선을 넘은 것"이다.
 * 규칙을 우회하지 말고, 필요한 기능을 해당 모듈의 api 패키지로 승격할 것.
 */
@AnalyzeClasses(packages = ["com.meeplenote"])
class ModuleBoundaryTest {

    // 모듈별 internal 접근 금지 규칙 (개별 선언 — 실패 메시지에서 어느 모듈 경계가 뚫렸는지 즉시 식별)
    @ArchTest
    val authInternalIsPrivate = internalRule("auth")
    @ArchTest
    val gameInternalIsPrivate = internalRule("game")
    @ArchTest
    val collectionInternalIsPrivate = internalRule("collection")
    @ArchTest
    val playInternalIsPrivate = internalRule("play")
    @ArchTest
    val statsInternalIsPrivate = internalRule("stats")
    @ArchTest
    val dataimportInternalIsPrivate = internalRule("dataimport")

    // 모듈 간 순환 의존 금지 — 순환이 생기면 미래의 절단선이 사라진다
    @ArchTest
    val noCyclesBetweenModules: ArchRule =
        slices().matching("com.meeplenote.(*)..")
            .should().beFreeOfCycles()
            .allowEmptyShould(true)

    private fun internalRule(module: String): ArchRule =
        noClasses()
            .that().resideOutsideOfPackage("com.meeplenote.$module..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.meeplenote.$module.internal..")
            .because("$module 모듈의 internal은 $module 안에서만 접근 가능 (ADR-001/009)")
            .allowEmptyShould(true)
}
