package dev.thewander02.cinder.render;

interface GpuAdapter {
    void assertOnRenderThread();

    String backendName();

    void resetPipelines() throws Exception;

    boolean compile(PackPipeline pipeline) throws Exception;

    Target createTarget(int width, int height);

    void draw(PackPipeline pipeline, Target offscreenTarget, Frame mainTarget);

    interface Frame {
        int width();

        int height();
    }

    interface Target extends AutoCloseable {
        int width();

        int height();

        void resize(int width, int height);

        @Override
        void close();
    }
}
