# =============================================================================
# MPM-style Anymal Sand Kernel for Ramanujan
# =============================================================================
#
# A simplified Material-Point-Method-flavoured sand simulation inspired by
# Newton's `mpm_anymal` example. A 4-legged kinematic walker (the "Anymal"
# stand-in) tramples through a bed of sand particles. Every physics step
# runs entirely on the Ramanujan platform; OpenCL `_GPU_N` kernels do the
# per-particle work, host-side Ramanujan code computes foot positions for
# the trot gait.
#
# Usage (driven by run_mpm_anymal.py via execute_inline):
#   rj mpm_anymal_kernel.py positions.csv velocities.csv params.csv
#
# Input arrays (loaded from same-named CSV files):
#   positions   1D, num_particles * 3  (x,y,z per particle, flattened)
#   velocities  1D, num_particles * 3  (vx,vy,vz per particle, flattened)
#   params      1D, 15 floats
#                 [0]  dt              physics step (s)
#                 [1]  gravity         z-acceleration (m/s^2, negative)
#                 [2]  foot_radius     collision radius (m)
#                 [3]  foot_strength   impulse magnitude
#                 [4]  gait_speed      forward velocity (m/s)
#                 [5]  step_height     foot lift amplitude (m)
#                 [6]  frame_num       integer-valued frame counter
#                 [7]  num_particles   total particle count
#                 [8]  grid_nx         grid width
#                 [9]  grid_ny         grid depth
#                 [10] grid_nz         grid height layers
#                 [11] spacing         inter-particle spacing (m)
#                 [12] origin_x        grid origin x (m)
#                 [13] origin_y        grid origin y (m)
#                 [14] origin_z        grid origin z (m)
#
# Outputs (extracted via dump):
#   positions  (updated in place)
#   velocities (updated in place)
#   feet       12 floats: foot0_xyz, foot1_xyz, foot2_xyz, foot3_xyz
# =============================================================================

# Working buffers exposed to the orchestrator
feet = [0 for _ in range(12)]
gravity_buf = [0 for _ in range(1)]
dt_buf = [0 for _ in range(1)]
foot_params = [0 for _ in range(2)]

# ── Unpack params on host (Ramanujan host-side runs as doubles) ─────────────
dt = params[0]
gravity_z = params[1]
foot_radius = params[2]
foot_strength = params[3]
gait_speed = params[4]
step_height = params[5]
frame_num = params[6]
num_particles = params[7]
grid_nx = params[8]
grid_ny = params[9]
grid_nz = params[10]
spacing = params[11]
origin_x = params[12]
origin_y = params[13]
origin_z = params[14]

dt_buf[0] = dt
gravity_buf[0] = gravity_z * dt
foot_params[0] = foot_radius
foot_params[1] = foot_strength

# ── Frame 0: lay out the particle grid on host ───────────────────────────────
if frame_num < 0.5:
    pi = 0
    iz = 0
    while iz < grid_nz:
        iy = 0
        while iy < grid_ny:
            ix = 0
            while ix < grid_nx:
                base = pi * 3
                base1 = base + 1
                base2 = base + 2
                positions[base] = origin_x + ix * spacing
                positions[base1] = origin_y + iy * spacing
                positions[base2] = origin_z + iz * spacing
                velocities[base] = 0
                velocities[base1] = 0
                velocities[base2] = 0
                pi = pi + 1
                ix = ix + 1
            iy = iy + 1
        iz = iz + 1

# ── Foot kinematics: trot gait, body walks in +y, lift in +z ───────────────
# Diagonal pairs lift together: (LF, RH) phase 0, (RF, LH) phase pi.
body_y = frame_num * dt * gait_speed
phase_a = frame_num * dt * gait_speed * 6.283185307
phase_b = phase_a + 3.141592653

# Use SIN built-in on host (it mutates its argument by reference).
sa = phase_a
SIN(sa)
sb = phase_b
SIN(sb)

lift_a = sa * step_height
if lift_a < 0:
    lift_a = 0
lift_b = sb * step_height
if lift_b < 0:
    lift_b = 0

# foot 0 LF (-x, +y, lifted on phase_a)
feet[0] = -0.18
feet[1] = body_y + 0.30
feet[2] = lift_a

# foot 1 RF (+x, +y, lifted on phase_b)
feet[3] = 0.18
feet[4] = body_y + 0.30
feet[5] = lift_b

# foot 2 LH (-x, -y, lifted on phase_b)
feet[6] = -0.18
feet[7] = body_y - 0.30
feet[8] = lift_b

# foot 3 RH (+x, -y, lifted on phase_a)
feet[9] = 0.18
feet[10] = body_y - 0.30
feet[11] = lift_a


# ── GPU kernel: gravity (one work item per particle) ────────────────────────
def apply_gravity_GPU_1(velocities, gravity_buf, gid):
    vz_idx = gid * 3 + 2
    velocities[vz_idx] = velocities[vz_idx] + gravity_buf[0]


# ── GPU kernel: 4-foot collision push ──────────────────────────────────────
# Each work item is a particle, loops over the 4 feet and accumulates impulse
# in local scalars before writing to the velocity array once. No nested GPU
# calls (forbidden by translator).
def apply_feet_GPU_1(positions, velocities, feet, foot_params, gid):
    px_idx = gid * 3
    py_idx = gid * 3 + 1
    pz_idx = gid * 3 + 2
    px = positions[px_idx]
    py = positions[py_idx]
    pz = positions[pz_idx]
    radius = foot_params[0]
    strength = foot_params[1]
    r_sq = radius * radius

    acc_vx = velocities[px_idx]
    acc_vy = velocities[py_idx]
    acc_vz = velocities[pz_idx]

    fi = 0
    while fi < 4:
        fx_idx = fi * 3
        fy_idx = fi * 3 + 1
        fz_idx = fi * 3 + 2
        fx = feet[fx_idx]
        fy = feet[fy_idx]
        fz = feet[fz_idx]
        dx = px - fx
        dy = py - fy
        dz = pz - fz
        dist_sq = dx * dx + dy * dy + dz * dz
        if dist_sq < r_sq:
            acc_vx = acc_vx + dx * strength
            acc_vy = acc_vy + dy * strength
            acc_vz = acc_vz + dz * strength + 0.05
        fi = fi + 1

    velocities[px_idx] = acc_vx
    velocities[py_idx] = acc_vy
    velocities[pz_idx] = acc_vz


# ── GPU kernel: ground collide + Euler integrate + damping ─────────────────
def integrate_GPU_1(positions, velocities, dt_buf, gid):
    dt_local = dt_buf[0]
    px_idx = gid * 3
    py_idx = gid * 3 + 1
    pz_idx = gid * 3 + 2

    vx = velocities[px_idx] * 0.985
    vy = velocities[py_idx] * 0.985
    vz = velocities[pz_idx] * 0.985

    new_x = positions[px_idx] + vx * dt_local
    new_y = positions[py_idx] + vy * dt_local
    new_z = positions[pz_idx] + vz * dt_local

    if new_z < 0:
        new_z = 0
        if vz < 0:
            vz = vz * (-0.25)
        vx = vx * 0.75
        vy = vy * 0.75

    positions[px_idx] = new_x
    positions[py_idx] = new_y
    positions[pz_idx] = new_z
    velocities[px_idx] = vx
    velocities[py_idx] = vy
    velocities[pz_idx] = vz


# ── Run physics step (skip on frame 0; that frame just initialises state) ──
# num_particles from params drives the GPU work-item count dynamically.
n = num_particles
if frame_num > 0.5:
    apply_gravity_GPU_1(velocities, gravity_buf, n)
    apply_feet_GPU_1(positions, velocities, feet, foot_params, n)
    integrate_GPU_1(positions, velocities, dt_buf, n)
