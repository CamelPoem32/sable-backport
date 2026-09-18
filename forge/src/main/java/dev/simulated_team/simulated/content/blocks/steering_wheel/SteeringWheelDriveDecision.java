package dev.simulated_team.simulated.content.blocks.steering_wheel;

final class SteeringWheelDriveDecision {

    static final float STOP_TOLERANCE = 0.001F;

    enum Action {
        RETARGET,
        RESUME_AFTER_ERROR,
        MOVING_TO_TARGET,
        WITHIN_STOP_TOLERANCE,
        NO_CONTROL_INPUT
    }

    record Result(Action action, float signedError) {
    }

    private SteeringWheelDriveDecision() {
    }

    static Result evaluate(final float requestedAngle, final float activeTargetAngle,
                           final float currentAngle, final int ticksRemaining, final float generatedRpm) {
        final float error = requestedAngle - currentAngle;
        if (Math.abs(requestedAngle - activeTargetAngle) > STOP_TOLERANCE
                && Math.abs(error) > STOP_TOLERANCE) {
            return new Result(Action.RETARGET, error);
        }
        if (ticksRemaining > 1 && generatedRpm != 0.0F) {
            return new Result(Action.MOVING_TO_TARGET, error);
        }
        if (Math.abs(error) > STOP_TOLERANCE) {
            return new Result(Action.RESUME_AFTER_ERROR, error);
        }
        return new Result(generatedRpm == 0.0F
                ? Action.NO_CONTROL_INPUT : Action.WITHIN_STOP_TOLERANCE, error);
    }

    static double additionalBearingTravel(final float requestedAngle, final float previousTargetAngle,
                                          final float logicalRpm, final float bearingRpm) {
        return (requestedAngle - previousTargetAngle) / logicalRpm * Math.abs(bearingRpm);
    }
}
