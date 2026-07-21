package dev.thewander02.cinder.render;

import org.jspecify.annotations.Nullable;

final class PipelineTransaction<T> {
    @Nullable
    private T active;

    Transition install(T candidate, Compiler<T> compiler) {
        T previous = active;
        Exception candidateFailure;

        try {
            compiler.reset();
            if (compiler.compile(candidate)) {
                active = candidate;
                return new Transition(true, false, null);
            }
            candidateFailure = new IllegalStateException("Candidate pipeline compilation failed");
        } catch (Exception exception) {
            candidateFailure = exception;
        }

        if (previous != null) {
            try {
                compiler.reset();
                if (compiler.compile(previous)) {
                    active = previous;
                    return new Transition(false, true, candidateFailure);
                }
                candidateFailure.addSuppressed(new IllegalStateException("Previous pipeline recompilation failed"));
            } catch (Exception rollbackFailure) {
                candidateFailure.addSuppressed(rollbackFailure);
            }
        } else {
            try {
                compiler.reset();
            } catch (Exception cleanupFailure) {
                candidateFailure.addSuppressed(cleanupFailure);
            }
        }

        active = null;
        return new Transition(false, false, candidateFailure);
    }

    @Nullable
    Exception disable(Compiler<T> compiler) {
        if (active == null) {
            return null;
        }
        try {
            compiler.reset();
            return null;
        } catch (Exception exception) {
            return exception;
        } finally {
            active = null;
        }
    }

    @Nullable
    T active() {
        return active;
    }

    void forget() {
        active = null;
    }

    interface Compiler<T> {
        void reset() throws Exception;

        boolean compile(T pipeline) throws Exception;
    }

    record Transition(boolean installed, boolean previousRestored, @Nullable Exception failure) {
    }
}
