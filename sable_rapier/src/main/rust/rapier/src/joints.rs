use crate::config::{JOINT_SPRING_DAMPING_RATIO, JOINT_SPRING_FREQUENCY};
use crate::scene::{LevelColliderID, PhysicsScene};
use crate::with_handle;
use jni::JNIEnv;
use jni::objects::{JClass, JDoubleArray};
use jni::sys::{jboolean, jbyte, jdouble, jint, jlong};
use marten::Real;
use rapier3d::dynamics::{
    GenericJointBuilder, ImpulseJointSet, JointAxesMask, JointAxis, RevoluteJointBuilder,
    SpringCoefficients,
};
use rapier3d::glamx::{DVec3, Quat};
use rapier3d::math::Vec3;
use rapier3d::prelude::{FixedJointBuilder, ImpulseJointHandle};
use log::info;
use std::collections::HashMap;

type SableJointHandle = jlong;
type RapierJointHandle = ImpulseJointHandle;

struct SubLevelJoint {
    id_a: Option<LevelColliderID>,
    id_b: Option<LevelColliderID>,

    pos_a: DVec3,
    pos_b: DVec3,
    normal_a: DVec3,
    normal_b: DVec3,

    rotation_a: Option<Quat>,
    rotation_b: Option<Quat>,

    handle: RapierJointHandle,

    fixed: bool,
    contacts_enabled: bool,
}

pub struct SableJointSet {
    joints: HashMap<SableJointHandle, SubLevelJoint>,
}

impl SableJointSet {
    #[must_use]
    pub fn new() -> Self {
        Self {
            joints: HashMap::new(),
        }
    }

    pub(crate) fn diagnostic_membership(
        &self,
        joint_id: SableJointHandle,
        impulse_joint_set: &ImpulseJointSet,
    ) -> (bool, bool) {
        let joint = self.joints.get(&joint_id);
        (
            joint.is_some(),
            joint.is_some_and(|joint| impulse_joint_set.contains(joint.handle)),
        )
    }
}

fn normalized_rotary_axis(axis: Vec3, fallback: Vec3) -> Vec3 {
    if axis.length_squared() > 0.0 {
        axis.normalize()
    } else {
        fallback
    }
}

/// Rapier's revolute joint frames use a directed local X axis on both bodies.
/// Sable's public Rotary API accepts endpoint normals, which may point in
/// opposite directions while describing the same physical hinge line.
fn canonicalize_rotary_axes(
    axis_a: Vec3,
    axis_b: Vec3,
    rotation_a: Quat,
    rotation_b: Quat,
) -> (Vec3, Vec3, Real, bool) {
    let axis_a = normalized_rotary_axis(axis_a, Vec3::X);
    let axis_b = normalized_rotary_axis(axis_b, axis_a);
    let world_axis_a = rotation_a * axis_a;
    let world_axis_b = rotation_b * axis_b;
    let world_axis_dot = world_axis_a.dot(world_axis_b);
    let flip_axis_b = world_axis_dot < 0.0;

    (
        axis_a,
        if flip_axis_b { -axis_b } else { axis_b },
        world_axis_dot,
        flip_axis_b,
    )
}

pub fn tick(scene: &PhysicsScene) {
    let mut sable_data = scene.sable_data.write().unwrap();
    let mut sim = scene.sim_data.write().unwrap();

    // filter the joints
    sable_data
        .joint_set
        .joints
        .retain(|_handle, joint| sim.impulse_joint_set.contains(joint.handle));

    // update every joint
    for (_handle, joint) in sable_data.joint_set.joints.iter() {
        let rb_a = joint
            .id_a
            .map(|id| sable_data.rigid_bodies[&id])
            .unwrap_or_else(|| scene.ground_handle.unwrap());
        let rb_b = joint
            .id_b
            .map(|id| sable_data.rigid_bodies[&id])
            .unwrap_or_else(|| scene.ground_handle.unwrap());
        let body_rotation_a = *sim.rigid_body_set[rb_a].rotation();
        let body_rotation_b = *sim.rigid_body_set[rb_b].rotation();
        let impulse_joint = sim.impulse_joint_set.get_mut(joint.handle, false).unwrap();
        impulse_joint.data.contacts_enabled = joint.contacts_enabled;
        if !joint.fixed && joint.rotation_a.is_none() {
            let (axis_a, axis_b, _, _) = canonicalize_rotary_axes(
                joint.normal_a.as_vec3(),
                joint.normal_b.as_vec3(),
                body_rotation_a,
                body_rotation_b,
            );
            impulse_joint.data.set_local_axis1(axis_a);
            impulse_joint.data.set_local_axis2(axis_b);
        }

        let center_of_mass_1 = if let Some(id_a) = joint.id_a
            && let Some(rb_a) = sable_data.level_colliders.get(&id_a)
        {
            rb_a.center_of_mass.unwrap()
        } else {
            DVec3::ZERO
        };

        let local_anchor_1 = joint.pos_a - center_of_mass_1;
        impulse_joint
            .data
            .set_local_anchor1(local_anchor_1.as_vec3());
        let center_of_mass_2 = if let Some(id_b) = joint.id_b
            && let Some(rb_b) = sable_data.level_colliders.get(&id_b)
        {
            rb_b.center_of_mass.unwrap()
        } else {
            DVec3::ZERO
        };

        let local_anchor_2 = joint.pos_b - center_of_mass_2;
        impulse_joint
            .data
            .set_local_anchor2(local_anchor_2.as_vec3());

        if let Some(rotation_a) = joint.rotation_a {
            impulse_joint.data.local_frame1.rotation = rotation_a;
        }
        if let Some(rotation_b) = joint.rotation_b {
            impulse_joint.data.local_frame2.rotation = rotation_b;
        }
    }
}

const AXES: [JointAxis; 6] = [
    JointAxis::LinX,
    JointAxis::LinY,
    JointAxis::LinZ,
    JointAxis::AngX,
    JointAxis::AngY,
    JointAxis::AngZ,
];

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_setConstraintMotor<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    joint_id: jlong,
    axis: jint,
    target_pos: jdouble,
    stiffness: jdouble,
    damping: jdouble,
    has_max_force: jboolean,
    max_force: jdouble,
) {
    with_handle(handle, |scene| {
        let sable_data = scene.sable_data.read().unwrap();
        let mut sim_data = scene.sim_data.write().unwrap();

        let Some(joint) = sable_data.joint_set.joints.get(&joint_id) else {
            return;
        };

        let data = &mut sim_data
            .impulse_joint_set
            .get_mut(joint.handle, false)
            .unwrap()
            .data;
        data.set_motor_position(
            AXES[axis as usize],
            target_pos as Real,
            stiffness as Real,
            damping as Real,
        );

        if has_max_force > 0 {
            data.motors[axis as usize].max_force = max_force as Real
        }
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_setConstraintLimit<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    joint_id: jlong,
    axis: jint,
    min: jdouble,
    max: jdouble,
) {
    with_handle(handle, |scene| {
        let sable_data = scene.sable_data.read().unwrap();
        let mut sim_data = scene.sim_data.write().unwrap();

        let Some(joint) = sable_data.joint_set.joints.get(&joint_id) else {
            return;
        };

        let data = &mut sim_data
            .impulse_joint_set
            .get_mut(joint.handle, false)
            .unwrap()
            .data;

        data.set_limits(AXES[axis as usize], [min as Real, max as Real]);
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_lockConstraintAxes<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    joint_id: jlong,
    mask: jbyte,
) {
    with_handle(handle, |scene| {
        let sable_data = scene.sable_data.read().unwrap();
        let mut sim_data = scene.sim_data.write().unwrap();

        let Some(joint) = sable_data.joint_set.joints.get(&joint_id) else {
            return;
        };

        let data = &mut sim_data
            .impulse_joint_set
            .get_mut(joint.handle, false)
            .unwrap()
            .data;

        data.lock_axes(JointAxesMask::from_bits(mask as u8).expect("Invalid mask!"));
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_isConstraintValid<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    joint_id: jlong,
) -> jboolean {
    with_handle(handle, |scene| {
        let sable_data = scene.sable_data.read().unwrap();
        if sable_data.joint_set.joints.contains_key(&joint_id) {
            1
        } else {
            0
        }
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_getConstraintImpulses<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    joint_id: jlong,
    store: JDoubleArray<'local>,
) {
    with_handle(handle, |scene| {
        let sable_data = scene.sable_data.read().unwrap();
        let sim_data = scene.sim_data.read().unwrap();

        let joint = sable_data.joint_set.joints.get(&joint_id).unwrap();
        let impulse_joint = sim_data.impulse_joint_set.get(joint.handle).unwrap();
        let impulses = impulse_joint.impulses;

        let arr: [jdouble; 6] = [
            impulses[0] as jdouble,
            impulses[1] as jdouble,
            impulses[2] as jdouble,
            impulses[3] as jdouble,
            impulses[4] as jdouble,
            impulses[5] as jdouble,
        ];

        env.set_double_array_region(&store, 0, &arr).unwrap();
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_setConstraintContactsEnabled<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    joint_id: jlong,
    enabled: jboolean,
) {
    with_handle(handle, |scene| {
        let mut sable_data = scene.sable_data.write().unwrap();
        let Some(joint) = sable_data.joint_set.joints.get_mut(&joint_id) else {
            return;
        };

        joint.contacts_enabled = enabled > 0;
    })
}

// removes a constraint
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_removeConstraint<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    joint_id: jlong,
) {
    with_handle(handle, |scene| {
        let mut sable_data = scene.sable_data.write().unwrap();
        let mut sim_data = scene.sim_data.write().unwrap();
        if let Some(joint) = sable_data.joint_set.joints.remove(&joint_id) {
            sim_data.impulse_joint_set.remove(joint.handle, true);
        }
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_addRotaryConstraint<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    id_a: jint,
    id_b: jint,
    local_x_a: jdouble,
    local_y_a: jdouble,
    local_z_a: jdouble,
    local_x_b: jdouble,
    local_y_b: jdouble,
    local_z_b: jdouble,
    axis_x_a: jdouble,
    axis_y_a: jdouble,
    axis_z_a: jdouble,
    axis_x_b: jdouble,
    axis_y_b: jdouble,
    axis_z_b: jdouble,
) -> SableJointHandle {
    with_handle(handle, |scene| {
        let mut sable_data = scene.sable_data.write().unwrap();
        let mut sim_data = scene.sim_data.write().unwrap();

        let rb_a = if id_a == -1 {
            scene.ground_handle.unwrap()
        } else {
            sable_data.rigid_bodies[&(id_a as LevelColliderID)]
        };

        let rb_b = if id_b == -1 {
            scene.ground_handle.unwrap()
        } else {
            sable_data.rigid_bodies[&(id_b as LevelColliderID)]
        };

        let center_of_mass_1 = if id_a == -1 {
            DVec3::ZERO
        } else {
            sable_data
                .level_colliders
                .get(&(id_a as LevelColliderID))
                .and_then(|info| info.center_of_mass)
                .unwrap_or(DVec3::ZERO)
        };
        let center_of_mass_2 = if id_b == -1 {
            DVec3::ZERO
        } else {
            sable_data
                .level_colliders
                .get(&(id_b as LevelColliderID))
                .and_then(|info| info.center_of_mass)
                .unwrap_or(DVec3::ZERO)
        };
        let public_anchor_1 = DVec3::new(local_x_a, local_y_a, local_z_a);
        let public_anchor_2 = DVec3::new(local_x_b, local_y_b, local_z_b);
        let local_anchor_1 = public_anchor_1 - center_of_mass_1;
        let local_anchor_2 = public_anchor_2 - center_of_mass_2;
        let axis_a_public = Vec3::new(axis_x_a as Real, axis_y_a as Real, axis_z_a as Real);
        let axis_b_public = Vec3::new(axis_x_b as Real, axis_y_b as Real, axis_z_b as Real);
        let body_a_rotation = *sim_data.rigid_body_set[rb_a].rotation();
        let body_b_rotation = *sim_data.rigid_body_set[rb_b].rotation();
        let (axis_a, axis_b, axis_dot, anti_parallel_axes_converted) = canonicalize_rotary_axes(
            axis_a_public,
            axis_b_public,
            body_a_rotation,
            body_b_rotation,
        );
        let converted_axis_b = normalized_rotary_axis(axis_b_public, axis_a);

        let mut revolute = RevoluteJointBuilder::new(axis_a)
            .local_anchor1(local_anchor_1.as_vec3())
            .local_anchor2(local_anchor_2.as_vec3())
            .softness(SpringCoefficients::new(
                JOINT_SPRING_FREQUENCY,
                JOINT_SPRING_DAMPING_RATIO,
            ));
        revolute.0.data.set_local_axis2(axis_b);
        let frame_rotation_a = revolute.0.data.local_frame1.rotation;
        let frame_rotation_b = revolute.0.data.local_frame2.rotation;
        let frame_a_norm = (frame_rotation_a.x * frame_rotation_a.x
            + frame_rotation_a.y * frame_rotation_a.y
            + frame_rotation_a.z * frame_rotation_a.z
            + frame_rotation_a.w * frame_rotation_a.w)
            .sqrt();
        let frame_b_norm = (frame_rotation_b.x * frame_rotation_b.x
            + frame_rotation_b.y * frame_rotation_b.y
            + frame_rotation_b.z * frame_rotation_b.z
            + frame_rotation_b.w * frame_rotation_b.w)
            .sqrt();

        let body_a = &sim_data.rigid_body_set[rb_a];
        let body_b = &sim_data.rigid_body_set[rb_b];
        let body_a_translation = body_a.translation();
        let body_b_translation = body_b.translation();
        let body_a_rotation = body_a.rotation();
        let body_b_rotation = body_b.rotation();
        let body_a_linear_velocity = body_a.linvel();
        let body_b_linear_velocity = body_b.linvel();
        let body_a_angular_velocity = body_a.angvel();
        let body_b_angular_velocity = body_b.angvel();
        let reconstructed_a = body_a.position().transform_point(local_anchor_1.as_vec3());
        let reconstructed_b = body_b.position().transform_point(local_anchor_2.as_vec3());
        let reconstruction_error = (reconstructed_a - reconstructed_b).length();
        let finite = local_anchor_1.x.is_finite()
            && local_anchor_1.y.is_finite()
            && local_anchor_1.z.is_finite()
            && local_anchor_2.x.is_finite()
            && local_anchor_2.y.is_finite()
            && local_anchor_2.z.is_finite()
            && axis_a.x.is_finite()
            && axis_a.y.is_finite()
            && axis_a.z.is_finite()
            && axis_b.x.is_finite()
            && axis_b.y.is_finite()
            && axis_b.z.is_finite()
            && frame_rotation_a.x.is_finite()
            && frame_rotation_a.y.is_finite()
            && frame_rotation_a.z.is_finite()
            && frame_rotation_a.w.is_finite()
            && frame_rotation_b.x.is_finite()
            && frame_rotation_b.y.is_finite()
            && frame_rotation_b.z.is_finite()
            && frame_rotation_b.w.is_finite();

        info!(
            "SABLE_ROTARY_BACKEND_CONSTRUCT bodyAHandle={} bodyBHandle={} bodyATranslation=({},{},{}) bodyBTranslation=({},{},{}) bodyARotation=({},{},{},{}) bodyBRotation=({},{},{},{}) bodyALinearVelocity=({},{},{}) bodyBLinearVelocity=({},{},{}) bodyAAngularVelocity=({},{},{}) bodyBAngularVelocity=({},{},{}) publicRawAnchorA=({},{},{}) publicRawAnchorB=({},{},{}) convertedLocalAnchorA=({},{},{}) convertedLocalAnchorB=({},{},{}) finalRapierLocalAnchorA=({},{},{}) finalRapierLocalAnchorB=({},{},{}) publicAxisA=({},{},{}) publicAxisB=({},{},{}) convertedLocalAxisA=({},{},{}) convertedLocalAxisB=({},{},{}) finalRapierAxisA=({},{},{}) finalRapierAxisB=({},{},{}) frameRotationA=({},{},{},{}) frameRotationB=({},{},{},{}) frameQuaternionNormA={} frameQuaternionNormB={} axisNormA={} axisNormB={} worldAxisDotBefore={} antiParallelAxesValid=false antiParallelAxesConverted={} reconstructionError={} lockedAxes=LIN_X|LIN_Y|LIN_Z|ANG_Y|ANG_Z freeAxis=ANG_X limits=none motor=none friction=none contactsEnabled=true bodyAMass=java_logged bodyBMass=java_logged bodyAInertia=java_logged bodyBInertia=java_logged finite={}",
            id_a,
            id_b,
            body_a_translation.x,
            body_a_translation.y,
            body_a_translation.z,
            body_b_translation.x,
            body_b_translation.y,
            body_b_translation.z,
            body_a_rotation.x,
            body_a_rotation.y,
            body_a_rotation.z,
            body_a_rotation.w,
            body_b_rotation.x,
            body_b_rotation.y,
            body_b_rotation.z,
            body_b_rotation.w,
            body_a_linear_velocity.x,
            body_a_linear_velocity.y,
            body_a_linear_velocity.z,
            body_b_linear_velocity.x,
            body_b_linear_velocity.y,
            body_b_linear_velocity.z,
            body_a_angular_velocity.x,
            body_a_angular_velocity.y,
            body_a_angular_velocity.z,
            body_b_angular_velocity.x,
            body_b_angular_velocity.y,
            body_b_angular_velocity.z,
            public_anchor_1.x,
            public_anchor_1.y,
            public_anchor_1.z,
            public_anchor_2.x,
            public_anchor_2.y,
            public_anchor_2.z,
            local_anchor_1.x,
            local_anchor_1.y,
            local_anchor_1.z,
            local_anchor_2.x,
            local_anchor_2.y,
            local_anchor_2.z,
            local_anchor_1.x,
            local_anchor_1.y,
            local_anchor_1.z,
            local_anchor_2.x,
            local_anchor_2.y,
            local_anchor_2.z,
            axis_a_public.x,
            axis_a_public.y,
            axis_a_public.z,
            axis_b_public.x,
            axis_b_public.y,
            axis_b_public.z,
            axis_a.x,
            axis_a.y,
            axis_a.z,
            converted_axis_b.x,
            converted_axis_b.y,
            converted_axis_b.z,
            axis_a.x,
            axis_a.y,
            axis_a.z,
            axis_b.x,
            axis_b.y,
            axis_b.z,
            frame_rotation_a.x,
            frame_rotation_a.y,
            frame_rotation_a.z,
            frame_rotation_a.w,
            frame_rotation_b.x,
            frame_rotation_b.y,
            frame_rotation_b.z,
            frame_rotation_b.w,
            frame_a_norm,
            frame_b_norm,
            axis_a.length(),
            axis_b.length(),
            axis_dot,
            anti_parallel_axes_converted,
            reconstruction_error,
            finite,
        );

        let handle = sim_data
            .impulse_joint_set
            .insert(rb_a, rb_b, revolute.build(), true);

        let (index, generation) = handle.0.into_raw_parts();
        let handle_long: SableJointHandle = index as jlong | (generation as jlong) << 32;

        sable_data.joint_set.joints.insert(
            handle_long,
            SubLevelJoint {
                id_a: if id_a == -1 {
                    None
                } else {
                    Some(id_a as LevelColliderID)
                },
                id_b: if id_b == -1 {
                    None
                } else {
                    Some(id_b as LevelColliderID)
                },

                pos_a: DVec3::new(local_x_a, local_y_a, local_z_a),
                pos_b: DVec3::new(local_x_b, local_y_b, local_z_b),

                normal_a: DVec3::new(axis_x_a, axis_y_a, axis_z_a),
                normal_b: DVec3::new(axis_x_b, axis_y_b, axis_z_b),

                rotation_a: None,
                rotation_b: None,

                handle,

                fixed: false,
                contacts_enabled: true,
            },
        );

        handle_long
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_addFixedConstraint<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    id_a: jint,
    id_b: jint,
    local_x_a: jdouble,
    local_y_a: jdouble,
    local_z_a: jdouble,
    local_x_b: jdouble,
    local_y_b: jdouble,
    local_z_b: jdouble,
    local_q_x: jdouble,
    local_q_y: jdouble,
    local_q_z: jdouble,
    local_q_w: jdouble,
) -> SableJointHandle {
    with_handle(handle, |scene| {
        let mut sable_data = scene.sable_data.write().unwrap();
        let mut sim_data = scene.sim_data.write().unwrap();

        let rb_a = if id_a == -1 {
            scene.ground_handle.unwrap()
        } else {
            sable_data.rigid_bodies[&(id_a as LevelColliderID)]
        };

        let rb_b = if id_b == -1 {
            scene.ground_handle.unwrap()
        } else {
            sable_data.rigid_bodies[&(id_b as LevelColliderID)]
        };

        let quat = Quat::from_xyzw(
            local_q_x as Real,
            local_q_y as Real,
            local_q_z as Real,
            local_q_w as Real,
        );
        let mut revolute = FixedJointBuilder::new()
            .local_anchor1(Vec3::ZERO)
            .local_anchor2(Vec3::ZERO)
            .softness(SpringCoefficients::new(
                JOINT_SPRING_FREQUENCY,
                JOINT_SPRING_DAMPING_RATIO,
            ));
        revolute.0.data.local_frame1.rotation = quat;

        let handle = sim_data
            .impulse_joint_set
            .insert(rb_a, rb_b, revolute.build(), true);

        let (index, generation) = handle.0.into_raw_parts();
        let handle_long: SableJointHandle = index as jlong | (generation as jlong) << 32;

        sable_data.joint_set.joints.insert(
            handle_long,
            SubLevelJoint {
                id_a: if id_a == -1 {
                    None
                } else {
                    Some(id_a as LevelColliderID)
                },
                id_b: if id_b == -1 {
                    None
                } else {
                    Some(id_b as LevelColliderID)
                },

                pos_a: DVec3::new(local_x_a, local_y_a, local_z_a),
                pos_b: DVec3::new(local_x_b, local_y_b, local_z_b),

                normal_a: DVec3::new(0.0, 0.0, 0.0),
                normal_b: DVec3::new(0.0, 0.0, 0.0),

                rotation_a: None,
                rotation_b: None,

                handle,

                fixed: true,
                contacts_enabled: false,
            },
        );

        handle_long
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_addFreeConstraint<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    id_a: jint,
    id_b: jint,
    local_x_a: jdouble,
    local_y_a: jdouble,
    local_z_a: jdouble,
    local_x_b: jdouble,
    local_y_b: jdouble,
    local_z_b: jdouble,
    local_q_x: jdouble,
    local_q_y: jdouble,
    local_q_z: jdouble,
    local_q_w: jdouble,
) -> SableJointHandle {
    with_handle(handle, |scene| {
        let mut sable_data = scene.sable_data.write().unwrap();
        let mut sim_data = scene.sim_data.write().unwrap();

        let rb_a = if id_a == -1 {
            scene.ground_handle.unwrap()
        } else {
            sable_data.rigid_bodies[&(id_a as LevelColliderID)]
        };

        let rb_b = if id_b == -1 {
            scene.ground_handle.unwrap()
        } else {
            sable_data.rigid_bodies[&(id_b as LevelColliderID)]
        };

        let mut joint = GenericJointBuilder::new(JointAxesMask::empty()).softness(
            SpringCoefficients::new(JOINT_SPRING_FREQUENCY, JOINT_SPRING_DAMPING_RATIO),
        );

        let quat = Quat::from_xyzw(
            local_q_x as Real,
            local_q_y as Real,
            local_q_z as Real,
            local_q_w as Real,
        );
        joint.0.local_frame1.rotation = quat;

        let handle = sim_data
            .impulse_joint_set
            .insert(rb_a, rb_b, joint.build(), true);

        let (index, generation) = handle.0.into_raw_parts();
        let handle_long: SableJointHandle = index as jlong | (generation as jlong) << 32;

        sable_data.joint_set.joints.insert(
            handle_long,
            SubLevelJoint {
                id_a: if id_a == -1 {
                    None
                } else {
                    Some(id_a as LevelColliderID)
                },
                id_b: if id_b == -1 {
                    None
                } else {
                    Some(id_b as LevelColliderID)
                },

                pos_a: DVec3::new(local_x_a, local_y_a, local_z_a),
                pos_b: DVec3::new(local_x_b, local_y_b, local_z_b),

                normal_a: DVec3::new(0.0, 0.0, 0.0),
                normal_b: DVec3::new(0.0, 0.0, 0.0),

                rotation_a: None,
                rotation_b: None,

                handle,

                fixed: true,
                contacts_enabled: true,
            },
        );

        handle_long
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_addGenericConstraint<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    id_a: jint,
    id_b: jint,
    local_x_a: jdouble,
    local_y_a: jdouble,
    local_z_a: jdouble,
    local_q_x_a: jdouble,
    local_q_y_a: jdouble,
    local_q_z_a: jdouble,
    local_q_w_a: jdouble,
    local_x_b: jdouble,
    local_y_b: jdouble,
    local_z_b: jdouble,
    local_q_x_b: jdouble,
    local_q_y_b: jdouble,
    local_q_z_b: jdouble,
    local_q_w_b: jdouble,
    locked_axes_mask: jint,
) -> SableJointHandle {
    with_handle(handle, |scene| {
        let mut sable_data = scene.sable_data.write().unwrap();
        let mut sim_data = scene.sim_data.write().unwrap();

        let rb_a = if id_a == -1 {
            scene.ground_handle.unwrap()
        } else {
            sable_data.rigid_bodies[&(id_a as LevelColliderID)]
        };

        let rb_b = if id_b == -1 {
            scene.ground_handle.unwrap()
        } else {
            sable_data.rigid_bodies[&(id_b as LevelColliderID)]
        };

        let locked_axes = JointAxesMask::from_bits_truncate(locked_axes_mask as u8);

        let rotation_a = Quat::from_xyzw(
            local_q_x_a as Real,
            local_q_y_a as Real,
            local_q_z_a as Real,
            local_q_w_a as Real,
        );
        let rotation_b = Quat::from_xyzw(
            local_q_x_b as Real,
            local_q_y_b as Real,
            local_q_z_b as Real,
            local_q_w_b as Real,
        );

        let mut joint = GenericJointBuilder::new(locked_axes).softness(SpringCoefficients::new(
            JOINT_SPRING_FREQUENCY,
            JOINT_SPRING_DAMPING_RATIO,
        ));
        joint.0.local_frame1.rotation = rotation_a;
        joint.0.local_frame2.rotation = rotation_b;

        let handle = sim_data
            .impulse_joint_set
            .insert(rb_a, rb_b, joint.build(), true);

        let (index, generation) = handle.0.into_raw_parts();
        let handle_long: SableJointHandle = index as jlong | (generation as jlong) << 32;

        sable_data.joint_set.joints.insert(
            handle_long,
            SubLevelJoint {
                id_a: if id_a == -1 {
                    None
                } else {
                    Some(id_a as LevelColliderID)
                },
                id_b: if id_b == -1 {
                    None
                } else {
                    Some(id_b as LevelColliderID)
                },

                pos_a: DVec3::new(local_x_a as f64, local_y_a as f64, local_z_a as f64),
                pos_b: DVec3::new(local_x_b as f64, local_y_b as f64, local_z_b as f64),

                normal_a: DVec3::ZERO,
                normal_b: DVec3::ZERO,

                rotation_a: Some(rotation_a),
                rotation_b: Some(rotation_b),

                handle,

                fixed: true,
                contacts_enabled: true,
            },
        );

        handle_long
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_setConstraintFrame<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    joint_id: jlong,
    side: jint,
    local_x: jdouble,
    local_y: jdouble,
    local_z: jdouble,
    local_q_x: jdouble,
    local_q_y: jdouble,
    local_q_z: jdouble,
    local_q_w: jdouble,
) {
    with_handle(handle, |scene| {
        let mut sable_data = scene.sable_data.write().unwrap();
        let Some(joint) = sable_data.joint_set.joints.get_mut(&joint_id) else {
            return;
        };

        let position = DVec3::new(local_x as f64, local_y as f64, local_z as f64);
        let rotation = Quat::from_xyzw(
            local_q_x as Real,
            local_q_y as Real,
            local_q_z as Real,
            local_q_w as Real,
        );

        match side {
            0 => {
                joint.pos_a = position;
                joint.rotation_a = Some(rotation);
            }
            1 => {
                joint.pos_b = position;
                joint.rotation_b = Some(rotation);
            }
            _ => panic!("Invalid constraint frame side: {}", side),
        }
    })
}

#[cfg(test)]
mod rotary_contract_tests {
    use super::*;
    use rapier3d::prelude::{
        BroadPhaseBvh, CCDSolver, ColliderBuilder, ColliderSet, ImpulseJointSet,
        IntegrationParameters, IslandManager, MultibodyJointSet, NarrowPhase, PhysicsPipeline,
        RigidBodyBuilder, RigidBodySet,
    };

    fn assert_rotary_case(real_inertia: bool, offset_anchors: bool) {
        let mut bodies = RigidBodySet::new();
        let mut colliders = ColliderSet::new();
        let mut impulse_joints = ImpulseJointSet::new();
        let mut multibody_joints = MultibodyJointSet::new();
        let mut pipeline = PhysicsPipeline::new();
        let mut islands = IslandManager::new();
        let mut broad_phase = BroadPhaseBvh::new();
        let mut narrow_phase = NarrowPhase::new();
        let mut ccd = CCDSolver::new();

        let separation = if offset_anchors { 4.0 } else { 0.0 };
        let body_a = bodies.insert(RigidBodyBuilder::dynamic().translation(Vec3::ZERO));
        let body_b = bodies.insert(
            RigidBodyBuilder::dynamic().translation(Vec3::new(separation, 0.0, 0.0)),
        );
        let collider_a = if real_inertia {
            ColliderBuilder::cuboid(1.5, 0.5, 0.75).density(2.0)
        } else {
            ColliderBuilder::ball(0.75).density(2.0)
        };
        let collider_b = if real_inertia {
            ColliderBuilder::cuboid(1.5, 0.5, 0.75).density(2.0)
        } else {
            ColliderBuilder::ball(0.75).density(2.0)
        };
        colliders.insert_with_parent(collider_a, body_a, &mut bodies);
        colliders.insert_with_parent(collider_b, body_b, &mut bodies);

        let anchor_a = if offset_anchors {
            Vec3::new(2.0, 0.0, 0.0)
        } else {
            Vec3::ZERO
        };
        let anchor_b = if offset_anchors {
            Vec3::new(-2.0, 0.0, 0.0)
        } else {
            Vec3::ZERO
        };
        let (axis_a, axis_b, dot_before, flipped) = canonicalize_rotary_axes(
            Vec3::X,
            Vec3::NEG_X,
            Quat::IDENTITY,
            Quat::IDENTITY,
        );
        assert!(dot_before < 0.0);
        assert!(flipped);

        let joint = RevoluteJointBuilder::new(axis_a)
            .local_axis2(axis_b)
            .local_anchor1(anchor_a)
            .local_anchor2(anchor_b)
            .contacts_enabled(false)
            .build();
        assert_eq!(joint.data.locked_axes, JointAxesMask::LOCKED_REVOLUTE_AXES);
        assert!(joint.data.local_axis1().dot(joint.data.local_axis2()) > 0.9999);
        impulse_joints.insert(body_a, body_b, joint, true);

        let parameters = IntegrationParameters {
            dt: 1.0 / 20.0,
            ..Default::default()
        };
        for _ in 0..100 {
            pipeline.step(
                Vec3::ZERO,
                &parameters,
                &mut islands,
                &mut broad_phase,
                &mut narrow_phase,
                &mut bodies,
                &mut colliders,
                &mut impulse_joints,
                &mut multibody_joints,
                &mut ccd,
                &(),
                &(),
            );
        }

        let rb_a = &bodies[body_a];
        let rb_b = &bodies[body_b];
        assert!(rb_a.translation().is_finite() && rb_b.translation().is_finite());
        assert!(rb_a.rotation().is_finite() && rb_b.rotation().is_finite());
        assert!(rb_a.linvel().is_finite() && rb_b.linvel().is_finite());
        assert!(rb_a.angvel().is_finite() && rb_b.angvel().is_finite());
        let world_anchor_a = rb_a.position().transform_point(anchor_a);
        let world_anchor_b = rb_b.position().transform_point(anchor_b);
        assert!((world_anchor_a - world_anchor_b).length() < 0.01);
    }

    #[test]
    fn rotary_target_api_handles_simple_inertia_centered_anchor() {
        assert_rotary_case(false, false);
    }

    #[test]
    fn rotary_target_api_handles_real_inertia_centered_anchor() {
        assert_rotary_case(true, false);
    }

    #[test]
    fn rotary_target_api_handles_simple_inertia_offset_anchor() {
        assert_rotary_case(false, true);
    }

    #[test]
    fn rotary_target_api_handles_real_inertia_offset_anchor() {
        assert_rotary_case(true, true);
    }

    #[test]
    fn rotary_axis_sign_is_compared_in_world_space() {
        let body_b_rotation = Quat::from_rotation_y(std::f32::consts::PI);
        let (_, axis_b, dot_before, flipped) = canonicalize_rotary_axes(
            Vec3::X,
            Vec3::NEG_X,
            Quat::IDENTITY,
            body_b_rotation,
        );
        assert!(dot_before > 0.9999);
        assert!(!flipped);
        assert_eq!(axis_b, Vec3::NEG_X);
    }
}
