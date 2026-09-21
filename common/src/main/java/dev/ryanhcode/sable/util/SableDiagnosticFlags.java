package dev.ryanhcode.sable.util;

/** Central policy for legacy milestone diagnostics retained for explicit regression runs. */
public final class SableDiagnosticFlags {
    public static final boolean TRACE_STATIC_RENDERING = Boolean.getBoolean("sable.m10.traceRendering")
            || Boolean.getBoolean("sable.m14.traceRendering");
    public static final boolean TRACE_BLOCK_EDITS = Boolean.getBoolean("sable.m11.traceBlockEdits");
    public static final boolean TRACE_M11 = Boolean.getBoolean("sable.m11.traceRuntime")
            || TRACE_BLOCK_EDITS;
    public static final boolean TRACE_M13 = Boolean.getBoolean("sable.m13.traceRuntime");
    public static final boolean TRACE_M20 = Boolean.getBoolean("sable.m20.traceCreateRendering");
    public static final boolean TRACE_OUTER_TRANSFER = Boolean.getBoolean("sable.m22.traceAssembly");
    public static final boolean TRACE_STEERING = Boolean.getBoolean("sable.m28.traceSteering");
    public static final boolean TRACE_M24 = Boolean.getBoolean("sable.m24.traceRuntime");

    private SableDiagnosticFlags() {
    }
}
