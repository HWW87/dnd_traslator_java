package com.dndtranslator.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para TranslationJob (Phase 1).
 */
public class TranslationJobTest {

    @Test
    public void testJobCreation() {
        TranslationJob job = new TranslationJob(
                "/path/to/input.pdf",
                "/path/to/output.pdf",
                "English",
                "ollama"
        );

        assertNotNull(job.getJobId());
        assertEquals("/path/to/input.pdf", job.getInputPdfPath());
        assertEquals("/path/to/output.pdf", job.getOutputPdfPath());
        assertEquals("English", job.getTargetLanguage());
        assertEquals("ollama", job.getProviderId());
        assertEquals(JobState.QUEUED, job.getCurrentState());
        assertNotNull(job.getCreatedAt());
    }

    @Test
    public void testStateTransition() {
        TranslationJob job = new TranslationJob(
                "/input.pdf",
                "/output.pdf",
                "English",
                "ollama"
        );

        job.transitionTo(JobState.VALIDATING);
        assertEquals(JobState.VALIDATING, job.getCurrentState());

        job.transitionTo(JobState.EXTRACTING);
        job.transitionTo(JobState.TRANSLATING);

        assertNotNull(job.getStartedAt());
        assertTrue(job.isActive());
    }

    @Test
    public void testCompletionTransition() {
        TranslationJob job = new TranslationJob(
                "/input.pdf",
                "/output.pdf",
                "English",
                "ollama"
        );

        job.transitionTo(JobState.TRANSLATING);
        job.transitionTo(JobState.REBUILDING);
        job.transitionTo(JobState.COMPLETED);

        assertTrue(job.isComplete());
        assertTrue(job.isSuccessful());
        assertNotNull(job.getCompletedAt());
    }

    @Test
    public void testTerminalStateBlocking() {
        TranslationJob job = new TranslationJob(
                "/input.pdf",
                "/output.pdf",
                "English",
                "ollama"
        );

        job.transitionTo(JobState.FAILED);

        assertThrows(IllegalStateException.class, () -> {
            job.transitionTo(JobState.TRANSLATING);
        });
    }

    @Test
    public void testProgressTracking() {
        TranslationJob job = new TranslationJob(
                "/input.pdf",
                "/output.pdf",
                "English",
                "ollama"
        );

        job.setTotalUnits(10);
        assertEquals(0.0, job.getProgress());

        job.recordUnitCompleted(true, false);
        job.recordUnitCompleted(true, false);
        job.recordUnitCompleted(false, true);

        assertEquals(3, job.getCompletedUnits());
        assertEquals(1, job.getSkippedUnits());
        assertEquals(0, job.getFailedUnits());
        assertEquals(30.0, job.getProgress());
    }

    @Test
    public void testMetrics() {
        TranslationJob job = new TranslationJob(
                "/input.pdf",
                "/output.pdf",
                "English",
                "ollama"
        );

        job.putMetric("avg_latency_ms", 250L);
        job.putMetric("retry_count", 3);

        assertEquals(250L, job.getMetric("avg_latency_ms", Long.class));
        assertEquals(3, job.getMetric("retry_count", Integer.class));
    }

    @Test
    public void testResume() {
        TranslationJob job = new TranslationJob(
                "/input.pdf",
                "/output.pdf",
                "English",
                "ollama"
        );

        job.transitionTo(JobState.TRANSLATING);
        assertFalse(job.canBeResumed());

        job.transitionTo(JobState.PAUSED);
        assertTrue(job.canBeResumed());

        job.transitionTo(JobState.TRANSLATING);
        assertFalse(job.canBeResumed());
    }
}

