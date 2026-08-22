

def init_obstacle_GPU_1(obs_arr, gid):
    N_Y = 50.0
    N_X = 300.0
    x = gid / N_Y
    FLOOR(x)
    y = gid - (x * N_Y)
    y = y + 0.1
    FLOOR(y)

    cy_x = 60.0
    cy_y = 25.0
    r_sq = 25.0

    dx = x - cy_x
    dy = y - cy_y
    dist_sq = dx * dx + dy * dy
    if dist_sq < r_sq:
        obs_arr[gid] = 1.0
    else:
        obs_arr[gid] = 0.0

# ── Persistent symmetry-breaking kernel ──────────────────────────────────────
# Called every batch to keep nudging f_prev above the cylinder so the
# collision step cannot damp the asymmetry back to zero before shedding begins.
def perturb_GPU_1(f_arr, gid):
    N_Y = 50.0
    N_X = 300.0
    x = gid / N_Y
    FLOOR(x)
    y = gid - (x * N_Y)
    y = y + 0.1
    FLOOR(y)

    if x > 54.5:
        if x < 75.5:
            if y > 26.5:
                if y < 34.5:
                    idx2 = gid * 9 + 2
                    idx5 = gid * 9 + 5
                    idx6 = gid * 9 + 6
                    idx4 = gid * 9 + 4
                    idx7 = gid * 9 + 7
                    idx8 = gid * 9 + 8
                    delta = 0.00005
                    f_arr[idx2] = f_arr[idx2] + delta
                    f_arr[idx5] = f_arr[idx5] + delta
                    f_arr[idx6] = f_arr[idx6] + delta
                    f_arr[idx4] = f_arr[idx4] - delta
                    f_arr[idx7] = f_arr[idx7] - delta
                    f_arr[idx8] = f_arr[idx8] - delta

def init_fluid_GPU_1(f_prev_arr, gid):

    u = 0.04
    # Small asymmetric perturbation just above cylinder to break
    # top/bottom symmetry and trigger vortex shedding at Re=80.
    # Without this the GPU float32 kernel stays symmetric forever.
    N_Y = 50.0
    N_X = 300.0
    x = gid / N_Y
    FLOOR(x)
    y = gid - (x * N_Y)
    y = y + 0.1
    FLOOR(y)

    v = 0.0
    if x > 54.5:
        if x < 65.5:
            if y > 25.5:
                if y < 30.5:
                    v = 0.005

    rho = 1.0
    idx0 = gid * 9

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
        uv2 = u * u + v * v + 0.0
        eq = rho * w * (1.0 + 3.0 * cu + 4.5 * cu * cu - 1.5 * uv2)
        tmp_idx = idx0 + i
        f_prev_arr[tmp_idx] = eq
        i += 1

def compute_macro_and_collide_GPU_1(f_in, f_out_arr, den_arr, u_arr, v_arr, obs_arr, p_arr, gid):
    omega = p_arr[0]
    idx0 = gid * 9
    N_Y = 50.0
    N_X = 300.0
    x = gid / N_Y
    FLOOR(x)
    y = gid - (x * N_Y)
    y = y + 0.1
    FLOOR(y)

    idx_0 = 0
    idx_1 = 0
    idx_2 = 0
    idx_3 = 0
    idx_4 = 0
    idx_5 = 0
    idx_6 = 0
    idx_7 = 0
    idx_8 = 0
    left_idx = 0
    left_base = 0
    left_3 = 0
    left_6 = 0
    left_7 = 0
    val_3 = 0.0
    val_6 = 0.0
    val_7 = 0.0
    f0 = 0.0
    f2 = 0.0
    f3 = 0.0
    f4 = 0.0
    f6 = 0.0
    f7 = 0.0
    eq0 = 0.0
    eq1 = 0.0
    eq5 = 0.0
    eq8 = 0.0
    f0_val = 0.0
    i2 = 0
    cx = 0.0
    cy = 0.0
    w = 0.0
    cu = 0.0
    uv2 = 0.0

    if x > 298.0:
        left_base = gid - 50
        left_idx = left_base * 9
        idx_3 = idx0 + 3
        left_3 = left_idx + 3
        val_3 = f_in[left_3]
        f_in[idx_3] = val_3
        idx_6 = idx0 + 6
        left_6 = left_idx + 6
        val_6 = f_in[left_6]
        f_in[idx_6] = val_6
        idx_7 = idx0 + 7
        left_7 = left_idx + 7
        val_7 = f_in[left_7]
        f_in[idx_7] = val_7

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

    if x < 1.0:
        if y > 0.5:
            if y < 48.5:
                idx_0 = idx0 + 0
                idx_2 = idx0 + 2
                idx_4 = idx0 + 4
                idx_3 = idx0 + 3
                idx_6 = idx0 + 6
                idx_7 = idx0 + 7
                f0 = f_in[idx_0]
                f2 = f_in[idx_2]
                f4 = f_in[idx_4]
                f3 = f_in[idx_3]
                f6 = f_in[idx_6]
                f7 = f_in[idx_7]
                u = 0.04
                v = 0.0
                rho = (f0 + f2 + f4 + 2.0 * (f3 + f6 + f7)) / (1.0 - u)
                cu = 1.0 * u + 0.0 * v
                uv2 = u * u + v * v + 0.0
                eq1 = rho * 0.1111111111111111 * (1.0 + 3.0 * cu + 4.5 * cu * cu - 1.5 * uv2)
                cu = 1.0 * u + 1.0 * v
                eq5 = rho * 0.027777777777777776 * (1.0 + 3.0 * cu + 4.5 * cu * cu - 1.5 * uv2)
                cu = 1.0 * u + -1.0 * v
                eq8 = rho * 0.027777777777777776 * (1.0 + 3.0 * cu + 4.5 * cu * cu - 1.5 * uv2)
                idx_1 = idx0 + 1
                idx_5 = idx0 + 5
                idx_8 = idx0 + 8
                f_in[idx_1] = eq1
                f_in[idx_5] = eq5
                f_in[idx_8] = eq8

    den_arr[gid] = rho
    u_arr[gid] = u
    v_arr[gid] = v

    if obs_arr[gid] > 0.5:
        idx_0 = idx0 + 0
        idx_1 = idx0 + 1
        idx_2 = idx0 + 2
        idx_3 = idx0 + 3
        idx_4 = idx0 + 4
        idx_5 = idx0 + 5
        idx_6 = idx0 + 6
        idx_7 = idx0 + 7
        idx_8 = idx0 + 8
        f_out_arr[idx_0] = f_in[idx_0]
        f_out_arr[idx_1] = f_in[idx_3]
        f_out_arr[idx_2] = f_in[idx_4]
        f_out_arr[idx_3] = f_in[idx_1]
        f_out_arr[idx_4] = f_in[idx_2]
        f_out_arr[idx_5] = f_in[idx_7]
        f_out_arr[idx_6] = f_in[idx_8]
        f_out_arr[idx_7] = f_in[idx_5]
        f_out_arr[idx_8] = f_in[idx_6]
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
            uv2 = u * u + v * v + 0.0
            eq0 = rho * w * (1.0 + 3.0 * cu + 4.5 * cu * cu - 1.5 * uv2)
            idx_0 = idx0 + i2
            f0_val = f_in[idx_0]
            f_out_arr[idx_0] = f0_val - omega * (f0_val - eq0)
            i2 += 1

def stream_GPU_1(f_in, f_out_arr, gid):
    idx0 = gid * 9
    N_Y = 50.0
    N_X = 300.0
    x = gid / N_Y
    FLOOR(x)
    y = gid - (x * N_Y)
    y = y + 0.1
    FLOOR(y)

    cx = 0.0
    cy = 0.0
    tx = 0.0
    ty = 0.0

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
        f_out_arr[tmp_dst] = val
        i += 1

n_cells = 15000

MAX_HORIZONTAL_INFLOW_VELOCITY = 0.04
CYLINDER_RADIUS_INDICES = 5.0
REYNOLDS_NUMBER = 80.0

kinematic_viscosity = (MAX_HORIZONTAL_INFLOW_VELOCITY * CYLINDER_RADIUS_INDICES) / REYNOLDS_NUMBER
relaxation_omega = 1.0 / (3.0 * kinematic_viscosity + 0.5)
params[0] = relaxation_omega

LOAD_MEM(f_prev)
LOAD_MEM(f_next)
LOAD_MEM(f_post)
LOAD_MEM(density)
LOAD_MEM(u_vel)
LOAD_MEM(v_vel)
LOAD_MEM(obstacle)
LOAD_MEM(params)
GPU_LOAD(params)

init_obstacle_GPU_1(obstacle, 15000)

# params[1]: 0.0 = first frame (run init_fluid), 1.0 = continuation (skip init).
# Read directly from the host buffer (set by run_lbm_simulation.py in the CSV).
if params[1] < 0.5:
    init_fluid_GPU_1(f_prev, 15000)

# Apply a small persistent perturbation above the cylinder every batch.
# This keeps the asymmetry alive long enough for vortex shedding to grow.
perturb_GPU_1(f_prev, 15000)

# Clean 200-step ping-pong: each pair of steps goes
#   f_prev -> f_post -> f_next  (step A)
#   f_next -> f_post -> f_prev  (step B)
# After 100 pairs the live state is always back in f_prev.
it = 0
while it < 100:
    compute_macro_and_collide_GPU_1(f_prev, f_post, density, u_vel, v_vel, obstacle, params, 15000)
    stream_GPU_1(f_post, f_next, 15000)
    compute_macro_and_collide_GPU_1(f_next, f_post, density, u_vel, v_vel, obstacle, params, 15000)
    stream_GPU_1(f_post, f_prev, 15000)
    it = it + 1

GPU_SYNC(f_prev)
GPU_SYNC(u_vel)
GPU_SYNC(v_vel)
GPU_SYNC(density)
GPU_SYNC(obstacle)

RETURN(f_prev, u_vel, v_vel, density, obstacle)

