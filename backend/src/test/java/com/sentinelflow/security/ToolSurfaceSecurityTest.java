package com.sentinelflow.security;

import com.sentinelflow.ai.tool.InvestigationAiTools;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 8: the AI tool surface is read-only by contract. Reflection enforces
 * that every tool exposed to the model is a read-only query (non-void return,
 * at most one argument) and that no write/execute/mutate entry point exists.
 */
class ToolSurfaceSecurityTest {

    @Test
    void toolSurfaceIsExactlyTheReadOnlyEvidenceSet() {
        List<Method> tools = Arrays.stream(InvestigationAiTools.class.getDeclaredMethods())
                .filter(ToolSurfaceSecurityTest::isTool)
                .toList();

        assertThat(tools).extracting(Method::getName).containsExactlyInAnyOrder(
                "getInvestigationContext", "getRiskDecision", "getEvidence", "getBehavioralContext");

        for (Method tool : tools) {
            assertThat(Void.TYPE.equals(tool.getReturnType()))
                    .as(tool.getName() + " must be read-only (non-void)").isFalse();
            assertThat(writeLike(tool.getName()))
                    .as("tool name %s must not imply mutation", tool.getName()).isFalse();
            assertThat(tool.getParameterCount()).isLessThanOrEqualTo(1);
        }
    }

    @Test
    void noWritePrimitivesExposedToTheModel() {
        Set<String> names = Arrays.stream(InvestigationAiTools.class.getDeclaredMethods())
                .filter(ToolSurfaceSecurityTest::isTool)
                .map(Method::getName)
                .collect(Collectors.toSet());
        for (String forbidden : List.of("execute", "update", "delete", "insert", "approve",
                "block", "write", "save", "mutate")) {
            assertThat(names.stream().anyMatch(n -> n.toLowerCase().contains(forbidden)))
                    .as("no tool may be " + forbidden + "-like").isFalse();
        }
    }

    private static boolean isTool(Method m) {
        for (Annotation a : m.getAnnotations()) {
            if (a.annotationType().getSimpleName().equals("Tool")) {
                return true;
            }
        }
        return false;
    }

    private static boolean writeLike(String name) {
        String lower = name.toLowerCase();
        return List.of("write", "execute", "update", "delete", "insert", "save", "approve", "block")
                .stream().anyMatch(lower::contains);
    }
}