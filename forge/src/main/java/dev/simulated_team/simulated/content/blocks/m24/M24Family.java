package dev.simulated_team.simulated.content.blocks.m24;

public enum M24Family {
    TORSION_SPRING("torsion_spring", "TORSION_STRUCTURAL"),
    SWIVEL_BEARING("swivel_bearing", "ROTARY_CONSTRAINT"),
    SWIVEL_BEARING_LINK_BLOCK("swivel_bearing_link_block", "ROTARY_CONSTRAINT_LINK"),
    ROPE_CONNECTOR("rope_connector", "ROPE_ATTACHMENT"),
    ROPE_WINCH("rope_winch", "ROPE_LENGTH_CONTROL"),
    DOCKING_CONNECTOR("docking_connector", "FIXED_CONSTRAINT"),
    PAIRED_DOCKING_CONNECTOR("paired_docking_connector", "FIXED_CONSTRAINT_PAIR"),
    ALTITUDE_SENSOR("altitude_sensor", "ONBOARD_SENSOR"),
    VELOCITY_SENSOR("velocity_sensor", "ONBOARD_SENSOR"),
    OPTICAL_SENSOR("optical_sensor", "ONBOARD_SENSOR"),
    STEERING_WHEEL("steering_wheel", "ONBOARD_CONTROL");

    private final String id;
    private final String upstreamRole;

    M24Family(final String id, final String upstreamRole) {
        this.id = id;
        this.upstreamRole = upstreamRole;
    }

    public String id() {
        return this.id;
    }

    public String upstreamRole() {
        return this.upstreamRole;
    }

    public boolean isConstraintBacked() {
        return this == SWIVEL_BEARING
                || this == ROPE_CONNECTOR
                || this == ROPE_WINCH
                || this == DOCKING_CONNECTOR
                || this == PAIRED_DOCKING_CONNECTOR;
    }

    public boolean isSensorOrControl() {
        return this == ALTITUDE_SENSOR
                || this == VELOCITY_SENSOR
                || this == OPTICAL_SENSOR
                || this == STEERING_WHEEL;
    }
}
