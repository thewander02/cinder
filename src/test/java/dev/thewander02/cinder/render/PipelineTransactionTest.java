package dev.thewander02.cinder.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PipelineTransactionTest {
    @Test
    void installsFirstValidCandidate() {
        PipelineTransaction<String> transaction = new PipelineTransaction<>();
        FakeCompiler compiler = new FakeCompiler();

        PipelineTransaction.Transition transition = transaction.install("first", compiler);

        assertTrue(transition.installed());
        assertFalse(transition.previousRestored());
        assertNull(transition.failure());
        assertEquals("first", transaction.active());
        assertEquals(1, compiler.resets);
    }

    @Test
    void replacesAValidPipeline() {
        PipelineTransaction<String> transaction = new PipelineTransaction<>();
        FakeCompiler compiler = new FakeCompiler();
        transaction.install("first", compiler);

        PipelineTransaction.Transition transition = transaction.install("second", compiler);

        assertTrue(transition.installed());
        assertEquals("second", transaction.active());
        assertEquals(2, compiler.resets);
    }

    @Test
    void restoresPreviousPipelineAfterCandidateFailure() {
        PipelineTransaction<String> transaction = new PipelineTransaction<>();
        FakeCompiler compiler = new FakeCompiler();
        transaction.install("first", compiler);
        compiler.invalid.add("broken");

        PipelineTransaction.Transition transition = transaction.install("broken", compiler);

        assertFalse(transition.installed());
        assertTrue(transition.previousRestored());
        assertNotNull(transition.failure());
        assertEquals("first", transaction.active());
        assertEquals(3, compiler.resets);
    }

    @Test
    void fallsBackToNoPipelineWhenCandidateAndRollbackFail() {
        PipelineTransaction<String> transaction = new PipelineTransaction<>();
        FakeCompiler compiler = new FakeCompiler();
        transaction.install("first", compiler);
        compiler.invalid.add("first");
        compiler.invalid.add("broken");

        PipelineTransaction.Transition transition = transaction.install("broken", compiler);

        assertFalse(transition.installed());
        assertFalse(transition.previousRestored());
        assertNotNull(transition.failure());
        assertNull(transaction.active());
    }

    @Test
    void failedInitialCandidateCleansTheCacheAndStaysInactive() {
        PipelineTransaction<String> transaction = new PipelineTransaction<>();
        FakeCompiler compiler = new FakeCompiler();
        compiler.invalid.add("broken");

        PipelineTransaction.Transition transition = transaction.install("broken", compiler);

        assertFalse(transition.installed());
        assertNull(transaction.active());
        assertEquals(2, compiler.resets);
    }

    @Test
    void disableResetsCacheAndDropsActivePipeline() {
        PipelineTransaction<String> transaction = new PipelineTransaction<>();
        FakeCompiler compiler = new FakeCompiler();
        transaction.install("first", compiler);

        Exception failure = transaction.disable(compiler);

        assertNull(failure);
        assertNull(transaction.active());
        assertEquals(2, compiler.resets);
    }

    @Test
    void resetExceptionIsReportedAndRollbackIsAttempted() {
        PipelineTransaction<String> transaction = new PipelineTransaction<>();
        FakeCompiler compiler = new FakeCompiler();
        transaction.install("first", compiler);
        RuntimeException resetFailure = new RuntimeException("reset failed");
        compiler.nextResetFailure = resetFailure;

        PipelineTransaction.Transition transition = transaction.install("second", compiler);

        assertFalse(transition.installed());
        assertTrue(transition.previousRestored());
        assertSame(resetFailure, transition.failure());
        assertEquals("first", transaction.active());
    }

    private static final class FakeCompiler implements PipelineTransaction.Compiler<String> {
        private final Set<String> invalid = new HashSet<>();
        private int resets;
        private RuntimeException nextResetFailure;

        @Override
        public void reset() {
            resets++;
            if (nextResetFailure != null) {
                RuntimeException failure = nextResetFailure;
                nextResetFailure = null;
                throw failure;
            }
        }

        @Override
        public boolean compile(String pipeline) {
            return !invalid.contains(pipeline);
        }
    }
}
