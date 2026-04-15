package io.agentframework.core;

import io.agentframework.core.TestFixtures.TestPhase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StepTypeTest {

    @Test
    void respond_isCorrectType() {
        StepType step = new StepType.Respond();
        assertInstanceOf(StepType.Respond.class, step);
    }

    @Test
    void callTool_withInputOnly_hasNullPreReply() {
        var step = new StepType.CallTool("search_web", Map.of("query", "Java frameworks"));
        assertEquals("search_web", step.toolName());
        assertEquals("Java frameworks", step.toolInput().get("query"));
        assertFalse(step.hasPreReply());
        assertNull(step.preReply());
    }

    @Test
    void callTool_withPreReply_hasPreReply() {
        var step = new StepType.CallTool("search_web",
                Map.of("query", "q"), "Let me search for that...");
        assertTrue(step.hasPreReply());
        assertEquals("Let me search for that...", step.preReply());
    }

    @Test
    void transitionPhase_withPhaseOnly_hasNullReply() {
        var step = new StepType.TransitionPhase(TestPhase.PROCESSING);
        assertEquals(TestPhase.PROCESSING, step.targetPhase());
        assertFalse(step.hasReply());
    }

    @Test
    void transitionPhase_withReply_hasReply() {
        var step = new StepType.TransitionPhase(TestPhase.DONE, "Moving to done phase");
        assertTrue(step.hasReply());
        assertEquals("Moving to done phase", step.reply());
    }

    @Test
    void custom_holdsTypeId() {
        var step = new StepType.Custom("my_domain_step");
        assertEquals("my_domain_step", step.typeId());
    }

    @Test
    void sealedHierarchy_patternMatchingWorks() {
        StepType step = new StepType.CallTool("search_web", Map.of());
        String result = switch (step) {
            case StepType.Respond r         -> "respond";
            case StepType.CallTool ct       -> "tool:" + ct.toolName();
            case StepType.TransitionPhase t -> "phase:" + t.targetPhase().id();
            case StepType.Custom c          -> "custom:" + c.typeId();
        };
        assertEquals("tool:search_web", result);
    }
}