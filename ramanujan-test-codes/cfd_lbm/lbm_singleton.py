# lbm_singleton.py
# Single-pass D2Q9 Lattice-Boltzmann simulation — flow past a cylinder, Re=80.
# 300 x 50 grid, 15 000 time-steps, all in ONE Ramanujan kernel call.
# No inter-batch CSV round-trips; GPU buffers stay live the whole time.
#
# Rule: array indices MUST be a single variable or literal integer.
#       Never use arithmetic expressions directly as indices.
#       Always pre-compute: tmp = a + b; arr[tmp] = ...

# ── Obstacle initialisation ───────────────────────────────────────────────────
def init_obstacle_GPU_1(obs_arr, gid):
    N_Y = 50.0
    x = gid / N_Y
    FLOOR(x)
    y = gid - (x * N_Y)
    y = y + 0.1
    FLOOR(y)
    dx = x - 60.0
    dy = y - 25.0
    dist_sq = dx * dx + dy * dy
    if dist_sq < 25.0:
        obs_arr[gid] = 1.0
    else:
        obs_arr[gid] = 0.0

# ── Equilibrium initialisation with symmetry-breaking perturbation ────────────
def init_fluid_GPU_1(f_arr, gid):
    N_Y = 50.0
    x = gid / N_Y
    FLOOR(x)
    y = gid - (x * N_Y)
    y = y + 0.1
    FLOOR(y)
    u = 0.04
    v = 0.0
    if y > 25.5:
        v = 0.002
    rho = 1.0
    idx0 = gid * 9
    tmp_idx = 0
    cx = 0.0
    cy = 0.0
    w = 0.0
    cu = 0.0
    uv2 = 0.0
    i = 0
    while i < 9:
        cx = 0.0
        if i == 1:
            cx = 1.0
        if i == 3:
            cx = -1.0
        if i == 5:
            cx = 1.0
        if i == 6:
            cx = -1.0
        if i == 7:
            cx = -1.0
        if i == 8:
            cx = 1.0
        cy = 0.0
        if i == 2:
            cy = 1.0
        if i == 4:
            cy = -1.0
        if i == 5:
            cy = 1.0
        if i == 6:
            cy = 1.0
        if i == 7:
            cy = -1.0
        if i == 8:
            cy = -1.0
        w = 0.0
        if i == 0:
            w = 0.4444444444444444
        if i == 1:
            w = 0.1111111111111111
        if i == 2:
            w = 0.1111111111111111
        if i == 3:
            w = 0.1111111111111111
        if i == 4:
            w = 0.1111111111111111
        if i == 5:
            w = 0.027777777777777776
        if i == 6:
            w = 0.027777777777777776
        if i == 7:
            w = 0.027777777777777776
        if i == 8:
            w = 0.027777777777777776
        cu = cx * u + cy * v
        uv2 = u * u + v * v
        tmp_idx = idx0 + i
        f_arr[tmp_idx] = rho * w * (1.0 + 3.0 * cu + 4.5 * cu * cu - 1.5 * uv2)
        i += 1

# ── BGK collision + macroscopic quantities + boundary conditions ──────────────
def compute_macro_and_collide_GPU_1(f_in, f_out, den_arr, u_arr, v_arr, obs_arr, p_arr, gid):
    omega = p_arr[0]
    idx0 = gid * 9
    N_Y = 50.0
    x = gid / N_Y
    FLOOR(x)
    y = gid - (x * N_Y)
    y = y + 0.1
    FLOOR(y)

    # Pre-declare all index variables (Ramanujan requires single-variable indices)
    left_base = 0
    left_idx = 0
    i3 = 0
    i6 = 0
    i7 = 0
    li3 = 0
    li6 = 0
    li7 = 0
    tmp_idx = 0
    val = 0.0
    cx = 0.0
    cy = 0.0
    t0 = 0
    t1 = 0
    t2 = 0
    t3 = 0
    t4 = 0
    t5 = 0
    t6 = 0
    t7 = 0
    t8 = 0
    f0 = 0.0
    f2 = 0.0
    f4 = 0.0
    f3 = 0.0
    f6 = 0.0
    f7 = 0.0
    eq1 = 0.0
    eq5 = 0.0
    eq8 = 0.0
    cu = 0.0
    uv2 = 0.0
    ob0 = 0
    ob1 = 0
    ob2 = 0
    ob3 = 0
    ob4 = 0
    ob5 = 0
    ob6 = 0
    ob7 = 0
    ob8 = 0
    i2 = 0
    cur_idx = 0
    fval = 0.0
    w = 0.0
    feq = 0.0

    # Outflow BC (x = 299): pull left-going populations from x = 298
    if x > 298.0:
        left_base = gid - 50
        left_idx = left_base * 9
        i3 = idx0 + 3
        li3 = left_idx + 3
        i6 = idx0 + 6
        li6 = left_idx + 6
        i7 = idx0 + 7
        li7 = left_idx + 7
        f_in[i3] = f_in[li3]
        f_in[i6] = f_in[li6]
        f_in[i7] = f_in[li7]

    # Macroscopic density and velocity
    rho = 0.0
    u = 0.0
    v = 0.0
    i = 0
    while i < 9:
        tmp_idx = idx0 + i
        val = f_in[tmp_idx]
        rho += val
        cx = 0.0
        if i == 1:
            cx = 1.0
        if i == 3:
            cx = -1.0
        if i == 5:
            cx = 1.0
        if i == 6:
            cx = -1.0
        if i == 7:
            cx = -1.0
        if i == 8:
            cx = 1.0
        cy = 0.0
        if i == 2:
            cy = 1.0
        if i == 4:
            cy = -1.0
        if i == 5:
            cy = 1.0
        if i == 6:
            cy = 1.0
        if i == 7:
            cy = -1.0
        if i == 8:
            cy = -1.0
        u += val * cx
        v += val * cy
        i += 1
    u = u / rho
    v = v / rho

    # Inflow BC (x = 0, interior y 1..48): Zou/He inlet u = 0.04, v = 0
    if x < 1.0:
        if y > 0.5:
            if y < 48.5:
                t0 = idx0
                t2 = idx0 + 2
                t4 = idx0 + 4
                t3 = idx0 + 3
                t6 = idx0 + 6
                t7 = idx0 + 7
                f0 = f_in[t0]
                f2 = f_in[t2]
                f4 = f_in[t4]
                f3 = f_in[t3]
                f6 = f_in[t6]
                f7 = f_in[t7]
                u = 0.04
                v = 0.0
                rho = (f0 + f2 + f4 + 2.0 * (f3 + f6 + f7)) / (1.0 - u)
                cu = u
                uv2 = u * u
                eq1 = rho * 0.1111111111111111 * (1.0 + 3.0 * cu + 4.5 * cu * cu - 1.5 * uv2)
                cu = u + v
                eq5 = rho * 0.027777777777777776 * (1.0 + 3.0 * cu + 4.5 * cu * cu - 1.5 * uv2)
                cu = u - v
                eq8 = rho * 0.027777777777777776 * (1.0 + 3.0 * cu + 4.5 * cu * cu - 1.5 * uv2)
                t1 = idx0 + 1
                t5 = idx0 + 5
                t8 = idx0 + 8
                f_in[t1] = eq1
                f_in[t5] = eq5
                f_in[t8] = eq8

    den_arr[gid] = rho
    u_arr[gid] = u
    v_arr[gid] = v

    # Obstacle: bounce-back. Fluid: BGK relaxation toward equilibrium.
    if obs_arr[gid] > 0.5:
        ob0 = idx0
        ob1 = idx0 + 1
        ob2 = idx0 + 2
        ob3 = idx0 + 3
        ob4 = idx0 + 4
        ob5 = idx0 + 5
        ob6 = idx0 + 6
        ob7 = idx0 + 7
        ob8 = idx0 + 8
        f_out[ob0] = f_in[ob0]
        f_out[ob1] = f_in[ob3]
        f_out[ob2] = f_in[ob4]
        f_out[ob3] = f_in[ob1]
        f_out[ob4] = f_in[ob2]
        f_out[ob5] = f_in[ob7]
        f_out[ob6] = f_in[ob8]
        f_out[ob7] = f_in[ob5]
        f_out[ob8] = f_in[ob6]
    else:
        i2 = 0
        while i2 < 9:
            cx = 0.0
            if i2 == 1:
                cx = 1.0
            if i2 == 3:
                cx = -1.0
            if i2 == 5:
                cx = 1.0
            if i2 == 6:
                cx = -1.0
            if i2 == 7:
                cx = -1.0
            if i2 == 8:
                cx = 1.0
            cy = 0.0
            if i2 == 2:
                cy = 1.0
            if i2 == 4:
                cy = -1.0
            if i2 == 5:
                cy = 1.0
            if i2 == 6:
                cy = 1.0
            if i2 == 7:
                cy = -1.0
            if i2 == 8:
                cy = -1.0
            w = 0.0
            if i2 == 0:
                w = 0.4444444444444444
            if i2 == 1:
                w = 0.1111111111111111
            if i2 == 2:
                w = 0.1111111111111111
            if i2 == 3:
                w = 0.1111111111111111
            if i2 == 4:
                w = 0.1111111111111111
            if i2 == 5:
                w = 0.027777777777777776
            if i2 == 6:
                w = 0.027777777777777776
            if i2 == 7:
                w = 0.027777777777777776
            if i2 == 8:
                w = 0.027777777777777776
            cu = cx * u + cy * v
            uv2 = u * u + v * v
            feq = rho * w * (1.0 + 3.0 * cu + 4.5 * cu * cu - 1.5 * uv2)
            cur_idx = idx0 + i2
            fval = f_in[cur_idx]
            f_out[cur_idx] = fval - omega * (fval - feq)
            i2 += 1

# ── Streaming step (periodic walls, pull scheme) ─────────────────────────────
def stream_GPU_1(f_in, f_out, gid):
    N_Y = 50.0
    N_X = 300.0
    idx0 = gid * 9
    x = gid / N_Y
    FLOOR(x)
    y = gid - (x * N_Y)
    y = y + 0.1
    FLOOR(y)
    tx = 0.0
    ty = 0.0
    target_gid = 0.0
    target_idx = 0.0
    tmp_src = 0
    tmp_dst = 0
    val = 0.0
    cx = 0.0
    cy = 0.0
    i = 0
    while i < 9:
        cx = 0.0
        if i == 1:
            cx = 1.0
        if i == 3:
            cx = -1.0
        if i == 5:
            cx = 1.0
        if i == 6:
            cx = -1.0
        if i == 7:
            cx = -1.0
        if i == 8:
            cx = 1.0
        cy = 0.0
        if i == 2:
            cy = 1.0
        if i == 4:
            cy = -1.0
        if i == 5:
            cy = 1.0
        if i == 6:
            cy = 1.0
        if i == 7:
            cy = -1.0
        if i == 8:
            cy = -1.0
        tx = x + cx
        if tx < 0.0:
            tx = N_X - 1.0
        else:
            if tx > 299.0:
                tx = 0.0
        ty = y + cy
        if ty < 0.0:
            ty = N_Y - 1.0
        else:
            if ty > 49.0:
                ty = 0.0
        target_gid = tx * N_Y + ty
        target_idx = target_gid * 9
        tmp_src = idx0 + i
        tmp_dst = target_idx + i
        val = f_in[tmp_src]
        f_out[tmp_dst] = val
        i += 1

# ── Copy one velocity field slice into the frame buffer ──────────────────────
# fi[0] = frame_count * 15000.0  (float, used as integer offset)
# float32 can exactly represent integers up to 2^24 = 16 777 216, so
# the max offset 99 * 15 000 = 1 485 000 is lossless.
def copy_frame_GPU_1(src, dst, fi, gid):
    offset = fi[0]
    dst_idx = offset + gid
    dst[dst_idx] = src[gid]

# ── Array declarations ────────────────────────────────────────────────────────
f_prev   = [0 for _ in range(135000)]
f_next   = [0 for _ in range(135000)]
f_post   = [0 for _ in range(135000)]
density  = [0 for _ in range(15000)]
u_vel    = [0 for _ in range(15000)]
v_vel    = [0 for _ in range(15000)]
obstacle = [0 for _ in range(15000)]
params   = [0 for _ in range(2)]
u_frames   = [0 for _ in range(1500000)]
v_frames   = [0 for _ in range(1500000)]
frame_info = [0 for _ in range(1)]

# ── Physics parameters ────────────────────────────────────────────────────────
nu = (0.04 * 5.0) / 80.0
omega = 1.0 / (3.0 * nu + 0.5)
params[0] = omega

# ── Upload all arrays to GPU before any _GPU_N dispatch ──────────────────────
LOAD_MEM(f_prev)
LOAD_MEM(f_next)
LOAD_MEM(f_post)
LOAD_MEM(density)
LOAD_MEM(u_vel)
LOAD_MEM(v_vel)
LOAD_MEM(obstacle)
LOAD_MEM(params)
LOAD_MEM(u_frames)
LOAD_MEM(v_frames)
LOAD_MEM(frame_info)
GPU_LOAD(params)

# ── Initialise geometry and flow field ───────────────────────────────────────
init_obstacle_GPU_1(obstacle, 15000)
init_fluid_GPU_1(f_prev, 15000)

# ── Time loop: 7500 pairs = 15 000 LBM steps ─────────────────────────────────
# Each pair (A+B) leaves the live state in f_prev — no buffer ambiguity.
# Frames are saved every 50 pairs (100 steps) after skipping the first
# 2500 pairs (5000 steps) while the flow is still establishing.
N_PAIRS    = 7500
SKIP_PAIRS = 2500
SAVE_EVERY = 50

frame_count    = 0
save_countdown = SKIP_PAIRS
it = 0
while it < N_PAIRS:
    compute_macro_and_collide_GPU_1(f_prev, f_post, density, u_vel, v_vel, obstacle, params, 15000)
    stream_GPU_1(f_post, f_next, 15000)
    compute_macro_and_collide_GPU_1(f_next, f_post, density, u_vel, v_vel, obstacle, params, 15000)
    stream_GPU_1(f_post, f_prev, 15000)
    it = it + 1
    save_countdown = save_countdown - 1
    if save_countdown < 0.5:
        if frame_count < 100:
            frame_info[0] = frame_count * 15000.0
            GPU_LOAD(frame_info)
            copy_frame_GPU_1(u_vel, u_frames, frame_info, 15000)
            copy_frame_GPU_1(v_vel, v_frames, frame_info, 15000)
            frame_count = frame_count + 1
        save_countdown = SAVE_EVERY

# ── Flush GPU and return ──────────────────────────────────────────────────────
GPU_SYNC(u_frames)
GPU_SYNC(v_frames)

RETURN(u_frames, v_frames)
