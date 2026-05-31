# =============================================================================
# MPM Snow Ball Kernel for Ramanujan  —  with P2G / Grid / G2P
# =============================================================================
#
# Explicit MPM loop per physics frame:
#   1. P2G        — scatter particle mass + momentum to background grid (host)
#   2. Grid step  — normalise, apply gravity, enforce wall/floor BCs  (GPU)
#   3. G2P        — gather velocity from grid, Euler-integrate positions (host)
#   4. Clamp      — safety-clamp particle positions to domain bounds    (GPU)
#
# GPU-accelerated steps (Ramanujan OpenCL _GPU_N kernels):
#   grid_update_GPU_3      — 3-D NDRange over MPM_NX×MPM_NY×MPM_NZ grid nodes
#   clamp_particles_GPU_1  — 1-D NDRange over N particles
#
# P2G and G2P must run on host because:
#   • P2G scatters from N particles to shared grid nodes (needs atomics, which
#     Ramanujan's OpenCL translator does not expose).
#   • G2P uses FLOOR() to locate grid nodes; FLOOR is a host built-in and is
#     not available inside _GPU_N device functions.
#
# Usage (driven by run_mpm_snow_ball.py via execute_inline server):
#   rj mpm_snow_ball_kernel.py positions.csv velocities.csv params.csv
#
# params  (1-D, 19 floats):
#   [0]  dt
#   [1]  gravity_z
#   [2]  throw_speed_y   initial +y velocity on frame 0
#   [3]  wall_y          wall position along y axis
#   [4]  wall_friction   lateral velocity retention at wall  (e.g. 0.35)
#   [5]  ground_friction lateral velocity retention at floor (e.g. 0.70)
#   [6]  frame_num
#   [7]  num_particles
#   [8]  grid_nx         ball lattice side x
#   [9]  grid_ny         ball lattice side y
#   [10] grid_nz         ball lattice side z
#   [11] spacing         inter-particle spacing (m)
#   [12] center_x        ball centre x (m)
#   [13] center_y        ball centre y (m)
#   [14] center_z        ball centre z (m)
#   [15] p_mass          particle mass (kg)
#   [16] board_cos       cos(board_angle_rad)  — wall plane normal y-component
#   [17] board_sin       sin(board_angle_rad)  — wall plane normal z-component
#   [18] d_wall          wall plane threshold  = wall_y*board_cos + wall_visual_z*board_sin
#
# Outputs (extracted via dump):
#   positions   (updated in place)
#   velocities  (updated in place)
#
# MPM background grid (hardcoded to cover the simulation domain):
#   x ∈ [-1.0,  1.0]  NX = 20 nodes   dx = 0.1 m
#   y ∈ [-1.5,  0.5]  NY = 20 nodes
#   z ∈ [-0.1,  2.7]  NZ = 28 nodes
#   total = 11 200 nodes
# =============================================================================

# ── MPM grid constants ────────────────────────────────────────────────────────
MPM_NX     = 20
MPM_NY     = 20
MPM_NZ     = 28
MPM_NY_NZ  = 560      # NY * NZ  — stride for ni dimension
MPM_TOTAL  = 11200    # NX * NY * NZ
MPM_TOTAL3 = 33600    # NX * NY * NZ * 3
MPM_OX     = -1.0     # grid origin x
MPM_OY     = -1.5     # grid origin y  (wall at y=0 is node nj=15)
MPM_OZ     = -0.1     # grid origin z  (floor at z=0 is node nk=1)
MPM_INV_DX = 10.0     # 1 / dx  (dx = 0.1 m)
MPM_DX     = 0.1

# ── Parameter buffers for GPU kernels ────────────────────────────────────────
# g_mass (MPM_TOTAL floats) and g_vel (MPM_TOTAL3 floats) are pre-zeroed CSV
# inputs supplied by run_mpm_snow_ball.py so Ramanujan can create valid OpenCL
# buffers for them (locally-allocated Python lists don't get cl_mem handles).

# grid_params_buf: [0] grav_dv, [1] wall_friction, [2] ground_friction,
#                  [3] board_cos, [4] board_sin, [5] d_wall
grid_params_buf = [0 for _ in range(6)]
# clamp_params: [0] wall_y (ref), [1] board_cos, [2] board_sin, [3] d_wall
clamp_params = [0 for _ in range(4)]

# ── Stencil scratch arrays (size 2, reused every particle) ───────────────────
wi   = [0 for _ in range(2)]
wj   = [0 for _ in range(2)]
wk   = [0 for _ in range(2)]
ni_s = [0 for _ in range(2)]
nj_s = [0 for _ in range(2)]
nk_s = [0 for _ in range(2)]

# ── Unpack params ─────────────────────────────────────────────────────────────
dt               = params[0]
gravity_z        = params[1]
throw_speed_y    = params[2]
wall_y           = params[3]
wall_friction    = params[4]
ground_friction  = params[5]
frame_num        = params[6]
num_particles    = params[7]
grid_nx          = params[8]
grid_ny          = params[9]
grid_nz          = params[10]
spacing          = params[11]
center_x         = params[12]
center_y         = params[13]
center_z         = params[14]
p_mass           = params[15]
board_cos        = params[16]
board_sin        = params[17]
d_wall           = params[18]

n = num_particles


# ── GPU kernel: grid node update (normalise → gravity → wall/floor BCs) ──────
# 3-D NDRange dispatched over the full MPM_NX × MPM_NY × MPM_NZ grid.
# Constants embedded as literals: 560 = MPM_NY_NZ, 28 = MPM_NZ,
#   -1.5 = MPM_OY, -0.1 = MPM_OZ, 0.1 = MPM_DX.
# grid_params_buf: [0] grav_dv, [1] wall_friction, [2] ground_friction,
#                  [3] board_cos, [4] board_sin, [5] d_wall
def grid_update_GPU_3(g_mass, g_vel, grid_params_buf, ni, nj, nk):
    gnode = ni * 560 + nj * 28 + nk
    m = g_mass[gnode]
    if m > 0:
        gnode3  = gnode * 3
        gnode31 = gnode3 + 1
        gnode32 = gnode3 + 2
        inv_m = 1.0 / m
        gvx = g_vel[gnode3]  * inv_m
        gvy = g_vel[gnode31] * inv_m
        gvz = g_vel[gnode32] * inv_m

        gvz = gvz + grid_params_buf[0]

        node_y = -1.5 + nj * 0.1
        node_z = -0.1 + nk * 0.1

        bc = grid_params_buf[3]
        bs = grid_params_buf[4]
        dw = grid_params_buf[5]
        wall_chk = node_y * bc + node_z * bs
        if wall_chk >= dw:
            v_n = gvy * bc + gvz * bs
            if v_n > 0:
                gvy = gvy - v_n * bc
                gvz = gvz - v_n * bs
            wf = grid_params_buf[1]
            gvx = gvx * wf
            gvy = gvy * wf
            gvz = gvz * wf

        if node_z <= 0:
            if gvz < 0:
                gvz = 0
            gf = grid_params_buf[2]
            gvx = gvx * gf
            gvy = gvy * gf

        g_vel[gnode3]  = gvx
        g_vel[gnode31] = gvy
        g_vel[gnode32] = gvz


# ── GPU kernel: per-particle position safety clamp ───────────────────────────
# clamp_params: [0] wall_y (ref), [1] board_cos, [2] board_sin, [3] d_wall
def clamp_particles_GPU_1(positions, clamp_params, gid):
    b2  = gid * 3 + 2
    pz  = positions[b2]
    if pz < 0:
        pz = 0
        positions[b2] = 0
    b1  = gid * 3 + 1
    py  = positions[b1]
    bc  = clamp_params[1]
    bs  = clamp_params[2]
    dw  = clamp_params[3]
    wall_chk = py * bc + pz * bs
    if wall_chk > dw:
        excess  = wall_chk - dw
        new_py  = py - excess * bc
        new_pz  = pz - excess * bs
        positions[b1] = new_py
        if new_pz < 0:
            new_pz = 0
        positions[b2] = new_pz


# ── Frame 0: place particles on sphere lattice, assign throw velocity ─────────
half_n = grid_nx * 0.5
R_sq   = (half_n - 0.5) * (half_n - 0.5)

if frame_num < 0.5:
    pi = 0
    iz = 0
    while iz < grid_nz:
        iy = 0
        while iy < grid_ny:
            ix = 0
            while ix < grid_nx:
                ddx = ix - half_n + 0.5
                ddy = iy - half_n + 0.5
                ddz = iz - half_n + 0.5
                r_sq = ddx * ddx + ddy * ddy + ddz * ddz
                if r_sq < R_sq:
                    if pi < num_particles:
                        base  = pi * 3
                        base1 = base + 1
                        base2 = base + 2
                        positions[base]  = center_x + ddx * spacing
                        positions[base1] = center_y + ddy * spacing
                        positions[base2] = center_z + ddz * spacing
                        velocities[base]  = 0
                        velocities[base1] = throw_speed_y
                        velocities[base2] = 0
                        pi = pi + 1
                ix = ix + 1
            iy = iy + 1
        iz = iz + 1


# ── Physics step (skipped on frame 0 — that frame only initialises state) ────
if frame_num > 0.5:

    # ── 1. P2G: scatter particle mass + momentum onto grid nodes ─────────────
    # Linear B-spline (hat function): 2×2×2 stencil per particle.
    # P2G runs on host because multiple particles can map to the same node
    # (race condition in GPU scatter without atomic float add).
    pi = 0
    while pi < num_particles:
        b0 = pi * 3
        b1 = b0 + 1
        b2 = b0 + 2
        xp  = positions[b0]
        yp  = positions[b1]
        zp  = positions[b2]
        vxp = velocities[b0]
        vyp = velocities[b1]
        vzp = velocities[b2]

        gxp = (xp - MPM_OX) * MPM_INV_DX
        gyp = (yp - MPM_OY) * MPM_INV_DX
        gzp = (zp - MPM_OZ) * MPM_INV_DX

        gxf = gxp
        FLOOR(gxf)
        gyf = gyp
        FLOOR(gyf)
        gzf = gzp
        FLOOR(gzf)

        i0 = gxf
        j0 = gyf
        k0 = gzf

        fx = gxp - i0
        fy = gyp - j0
        fz = gzp - k0

        wi[0]   = 1.0 - fx
        wi[1]   = fx
        wj[0]   = 1.0 - fy
        wj[1]   = fy
        wk[0]   = 1.0 - fz
        wk[1]   = fz
        ni_s[0] = i0
        ni_s[1] = i0 + 1
        nj_s[0] = j0
        nj_s[1] = j0 + 1
        nk_s[0] = k0
        nk_s[1] = k0 + 1

        sii = 0
        while sii < 2:
            n_i  = ni_s[sii]
            w_i  = wi[sii]
            sij = 0
            while sij < 2:
                n_j   = nj_s[sij]
                w_ij  = w_i * wj[sij]
                sik = 0
                while sik < 2:
                    n_k    = nk_s[sik]
                    w_ijk  = w_ij * wk[sik]
                    if n_i >= 0:
                        if n_i < MPM_NX:
                            if n_j >= 0:
                                if n_j < MPM_NY:
                                    if n_k >= 0:
                                        if n_k < MPM_NZ:
                                            gnode   = n_i * MPM_NY_NZ + n_j * MPM_NZ + n_k
                                            gnode3  = gnode * 3
                                            gnode31 = gnode3 + 1
                                            gnode32 = gnode3 + 2
                                            wm = w_ijk * p_mass
                                            g_mass[gnode]  = g_mass[gnode]  + wm
                                            g_vel[gnode3]  = g_vel[gnode3]  + wm * vxp
                                            g_vel[gnode31] = g_vel[gnode31] + wm * vyp
                                            g_vel[gnode32] = g_vel[gnode32] + wm * vzp
                    sik = sik + 1
                sij = sij + 1
            sii = sii + 1

        pi = pi + 1

    # ── 2. Grid step: normalise momentum→velocity, gravity, BCs  (GPU) ───────
    grav_dv = gravity_z * dt
    grid_params_buf[0] = grav_dv
    grid_params_buf[1] = wall_friction
    grid_params_buf[2] = ground_friction
    grid_params_buf[3] = board_cos
    grid_params_buf[4] = board_sin
    grid_params_buf[5] = d_wall
    grid_update_GPU_3(g_mass, g_vel, grid_params_buf, MPM_NX, MPM_NY, MPM_NZ)

    # ── 3. G2P: gather velocity from grid, Euler-integrate particle positions ─
    # G2P runs on host: FLOOR() is a host built-in not available in GPU kernels.
    pi = 0
    while pi < num_particles:
        b0 = pi * 3
        b1 = b0 + 1
        b2 = b0 + 2
        xp = positions[b0]
        yp = positions[b1]
        zp = positions[b2]

        gxp = (xp - MPM_OX) * MPM_INV_DX
        gyp = (yp - MPM_OY) * MPM_INV_DX
        gzp = (zp - MPM_OZ) * MPM_INV_DX

        gxf = gxp
        FLOOR(gxf)
        gyf = gyp
        FLOOR(gyf)
        gzf = gzp
        FLOOR(gzf)

        i0 = gxf
        j0 = gyf
        k0 = gzf

        fx = gxp - i0
        fy = gyp - j0
        fz = gzp - k0

        wi[0]   = 1.0 - fx
        wi[1]   = fx
        wj[0]   = 1.0 - fy
        wj[1]   = fy
        wk[0]   = 1.0 - fz
        wk[1]   = fz
        ni_s[0] = i0
        ni_s[1] = i0 + 1
        nj_s[0] = j0
        nj_s[1] = j0 + 1
        nk_s[0] = k0
        nk_s[1] = k0 + 1

        new_vx = 0.0
        new_vy = 0.0
        new_vz = 0.0

        sii = 0
        while sii < 2:
            n_i  = ni_s[sii]
            w_i  = wi[sii]
            sij = 0
            while sij < 2:
                n_j   = nj_s[sij]
                w_ij  = w_i * wj[sij]
                sik = 0
                while sik < 2:
                    n_k   = nk_s[sik]
                    w_ijk = w_ij * wk[sik]
                    if n_i >= 0:
                        if n_i < MPM_NX:
                            if n_j >= 0:
                                if n_j < MPM_NY:
                                    if n_k >= 0:
                                        if n_k < MPM_NZ:
                                            gnode   = n_i * MPM_NY_NZ + n_j * MPM_NZ + n_k
                                            gnode3  = gnode * 3
                                            gnode31 = gnode3 + 1
                                            gnode32 = gnode3 + 2
                                            new_vx = new_vx + w_ijk * g_vel[gnode3]
                                            new_vy = new_vy + w_ijk * g_vel[gnode31]
                                            new_vz = new_vz + w_ijk * g_vel[gnode32]
                    sik = sik + 1
                sij = sij + 1
            sii = sii + 1

        positions[b0] = xp + new_vx * dt
        positions[b1] = yp + new_vy * dt
        positions[b2] = zp + new_vz * dt
        velocities[b0] = new_vx
        velocities[b1] = new_vy
        velocities[b2] = new_vz

        pi = pi + 1

    # ── 4. Safety clamp: keep particles inside the physical domain  (GPU) ────
    clamp_params[0] = wall_y
    clamp_params[1] = board_cos
    clamp_params[2] = board_sin
    clamp_params[3] = d_wall
    clamp_particles_GPU_1(positions, clamp_params, n)
