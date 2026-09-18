package dev.simulated_team.simulated.content.blocks.steering_wheel;

public final class SteeringWheelDriveDecisionTest {

    private SteeringWheelDriveDecisionTest() {
    }

    public static void run() {
        final float oldTarget = 27.344967F;
        final float newTarget = 27.824955F;
        check(SteeringWheelDriveDecision.evaluate(newTarget, oldTarget, oldTarget, 1, 16.0F)
                .action() == SteeringWheelDriveDecision.Action.RETARGET, "stale active target must not stop");
        check(SteeringWheelDriveDecision.evaluate(newTarget, newTarget, oldTarget, 1, 16.0F)
                .action() == SteeringWheelDriveDecision.Action.RESUME_AFTER_ERROR, "unfinished target must resume");

        float angle = 0.0F;
        float activeTarget = 0.0F;
        float speed = 0.0F;
        float remainingTravel = 0.0F;
        int ticksRemaining = 0;
        for (int tick = 0; tick < 300; tick++) {
            if (speed != 0.0F) {
                final float step = Math.copySign(Math.min(4.8F, remainingTravel), speed);
                angle += step;
                remainingTravel -= Math.abs(step);
            }
            final float requested = (tick + 1) * 0.24F;
            final SteeringWheelDriveDecision.Result decision = SteeringWheelDriveDecision.evaluate(
                    requested, activeTarget, angle, ticksRemaining, speed);
            switch (decision.action()) {
                case RETARGET, RESUME_AFTER_ERROR -> {
                    activeTarget = requested;
                    speed = Math.copySign(16.0F, decision.signedError());
                    remainingTravel = Math.abs(decision.signedError());
                    ticksRemaining = (int) Math.ceil(remainingTravel / 4.8F) + 2;
                }
                case MOVING_TO_TARGET -> ticksRemaining--;
                case WITHIN_STOP_TOLERANCE, NO_CONTROL_INPUT -> speed = 0.0F;
            }
            check(speed != 0.0F, "continuous input lost the kinetic source at tick " + tick);
            check(Math.abs(requested - angle) > SteeringWheelDriveDecision.STOP_TOLERANCE,
                    "continuous test must remain outside stop tolerance");
        }

        for (int tick = 0; tick < 5; tick++) {
            if (speed != 0.0F) {
                final float step = Math.copySign(Math.min(4.8F, remainingTravel), speed);
                angle += step;
                remainingTravel -= Math.abs(step);
            }
            final SteeringWheelDriveDecision.Result decision = SteeringWheelDriveDecision.evaluate(
                    activeTarget, activeTarget, angle, ticksRemaining, speed);
            if (decision.action() == SteeringWheelDriveDecision.Action.WITHIN_STOP_TOLERANCE) {
                check(Math.abs(decision.signedError()) <= SteeringWheelDriveDecision.STOP_TOLERANCE,
                        "stopped outside tolerance");
                speed = 0.0F;
            } else if (decision.action() == SteeringWheelDriveDecision.Action.MOVING_TO_TARGET) {
                ticksRemaining--;
            }
        }
        check(speed == 0.0F, "settled source did not stop");
        check(SteeringWheelDriveDecision.evaluate(activeTarget + 20.0F, activeTarget, angle, 0, 0.0F)
                .action() == SteeringWheelDriveDecision.Action.RETARGET, "resume from hold failed");
        check(SteeringWheelDriveDecision.evaluate(-30.0F, activeTarget + 20.0F, angle, 3, 16.0F)
                .signedError() < 0.0F, "direction reversal sign failed");
        check(Math.abs(SteeringWheelDriveDecision.additionalBearingTravel(
                0.48F, 0.24F, 16.0F, -16.0F) - 0.24D) < 0.00001D,
                "same-speed retarget did not extend the inverse-bearing travel");
        check(Math.abs(SteeringWheelDriveDecision.additionalBearingTravel(
                0.48F, 0.24F, 16.0F, -32.0F) - 0.48D) < 0.00001D,
                "retarget did not respect a gearbox speed ratio");
        check(Math.abs(SteeringWheelDriveDecision.additionalBearingTravel(
                0.24F, 0.48F, 16.0F, -16.0F) + 0.24D) < 0.00001D,
                "retarget in the same running direction could not shorten travel");
        for (int cycle = 0; cycle < 100; cycle++) {
            final float target = cycle % 2 == 0 ? 30.0F : -30.0F;
            final SteeringWheelDriveDecision.Result reversal = SteeringWheelDriveDecision.evaluate(
                    target, -target, 0.0F, 3, cycle % 2 == 0 ? -16.0F : 16.0F);
            check(reversal.action() == SteeringWheelDriveDecision.Action.RETARGET,
                    "repeated reversal dropped the requested target at cycle " + cycle);
            check(Math.copySign(1.0F, reversal.signedError()) == Math.copySign(1.0F, target),
                    "repeated reversal lost direction at cycle " + cycle);
        }
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
