package dev.ryanhcode.sable.util;

/** Central policy for legacy milestone diagnostics retained for explicit regression runs. */
public final class SableDiagnosticFlags {
    public static final boolean TRACE_STATIC_RENDERING = Boolean.getBoolean("sable.m10.traceRendering")
            || Boolean.getBoolean("sable.m14.traceRendering")
            || Boolean.getBoolean("sable.m28.visualOwnershipTrace");
    public static final boolean TRACE_BLOCK_EDITS = Boolean.getBoolean("sable.m11.traceBlockEdits");
    public static final boolean TRACE_M11 = Boolean.getBoolean("sable.m11.traceRuntime")
            || TRACE_BLOCK_EDITS;
    public static final boolean TRACE_M13 = Boolean.getBoolean("sable.m13.traceRuntime")
            || Boolean.getBoolean("sable.m28.visualOwnershipTrace");
    public static final boolean TRACE_M20 = Boolean.getBoolean("sable.m20.traceCreateRendering");
    public static final boolean TRACE_OUTER_TRANSFER = Boolean.getBoolean("sable.m22.traceAssembly")
            || Boolean.getBoolean("sable.m28.traceNestedBearingPayload");
    public static final boolean TRACE_STEERING = Boolean.getBoolean("sable.m28.traceSteering")
            || Boolean.getBoolean("sable.m28.traceBearingAssemblyLifecycle")
            || Boolean.getBoolean("sable.m28.traceNestedBearingPayload");
    public static final boolean TRACE_M24 = Boolean.getBoolean("sable.m24.traceRuntime");
    public static final boolean TRACE_M28_BUFFER = Boolean.getBoolean("sable.m28.traceBufferProbe")
            || Boolean.getBoolean("sable.m28.visualOwnershipTrace")
            || Boolean.getBoolean("sable.m28.normalCreateAB")
            || Boolean.getBoolean("sable.m28.referenceGeometry")
            || Boolean.getBoolean("sable.m28.tintCpuTargetContraption")
            || Boolean.getBoolean("sable.m28.showCpuTargetOverlay")
            || Boolean.getBoolean("sable.m28.traceFinalPresentation");

    private SableDiagnosticFlags() {
    }
}
