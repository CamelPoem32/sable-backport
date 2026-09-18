# M28.4 Steering Wheel Stress Audit

## Frozen authority

- Repository: `Creators-of-Aeronautics/Simulated-Project`
- Commit: `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`
- Steering Wheel generated speed: signed `16 RPM`
- Steering Wheel base stress capacity: configurable `16 SU/RPM`
- Steering Wheel stress impact: no Simulated impact registration, therefore `0 SU/RPM`
- Stress key: `SimBlocks.STEERING_WHEEL`, resource ID `simulated:steering_wheel`

Frozen registration uses `SimStress.setCapacity(16.0)` and
`BlockStressValues.setGeneratorSpeed(SteeringWheelBlockEntity.RPM)`. The
capacity is stored under `kinetics.stressValues.capacity.steering_wheel`.
`SteeringWheelBlockEntity#getStressConfigKey()` returns the registered Steering
Wheel block.

## Create 6.0.8 contract

Create 6.0.8 exposes block-keyed registries through
`BlockStressValues.CAPACITIES`, `BlockStressValues.IMPACTS`, and
`BlockStressValues.RPM`. `KineticBlockEntity#calculateAddedStressCapacity()`
looks up the block returned by `getStressConfigKey()` in `CAPACITIES`.
`KineticNetwork#getActualCapacityOf()` multiplies that base capacity by the
absolute generated RPM. Network stress is the corresponding per-RPM impact sum
multiplied by absolute theoretical speed.

For a moving Steering Wheel, the frozen base value `16 SU/RPM` at `16 RPM`
contributes `256 SU` of network capacity. The wheel itself contributes zero
stress impact. The attached gearbox and shaft transmit rotation, while the one
Mechanical Bearing is the intended load.

## Target divergence and adaptation

The target Steering Wheel already extended `GeneratingKineticBlockEntity`,
returned signed 16 RPM, and returned its own block from `getStressConfigKey()`.
The target DeferredRegister path omitted the frozen Registrate transforms that
registered capacity and generator-speed metadata. Consequently
`BlockStressValues.getCapacity(simulated:steering_wheel)` returned zero, so all
three correctly isolated control networks were overstressed as soon as their
bearings consumed stress.

M28.4 registers the target block during Forge common setup using the exact
Create 6.0.8 registries. The capacity supplier reads the frozen-default Forge
config value `kinetics.stressValues.capacity.steering_wheel=16.0`, and generator
metadata remains 16 RPM. No Create class, network algorithm, drivetrain block,
or Golden Aircraft coordinate changes.

Each M28.3 channel remains isolated and contains one Steering Wheel source, its
documented gearbox/shaft transmission, and one Mechanical Bearing. Runtime
inspection lists every real network member and rejects assumptions based only
on blueprint text.
