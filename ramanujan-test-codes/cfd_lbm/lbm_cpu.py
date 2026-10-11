# lbm_cpu.py
# Pure CPU D2Q9 Lattice-Boltzmann simulation — flow past a cylinder.
# 300 x 50 grid for CPU execution on Ramanujan platform.

def init_obstacle(obs_arr, n):
    N_Y = 50.0
    gid = 0
    while gid < n:
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
        gid = gid + 1

def init_fluid(f_arr, n):
    N_Y = 50.0
    gid = 0
    while gid < n:
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
            i = i + 1
        gid = gid + 1

def compute_macro_and_collide(f_in, f_out, den_arr, u_arr, v_arr, obs_arr, omega, n):
    idx0 = 0
    N_Y = 50.0
    gid = 0
    while gid < n:
        idx0 = gid * 9
        x = gid / N_Y
        FLOOR(x)
        y = gid - (x * N_Y)
        y = y + 0.1
        FLOOR(y)

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

        rho = 0.0
        u = 0.0
        v = 0.0
        i = 0
        while i < 9:
            tmp_idx = idx0 + i
            val = f_in[tmp_idx]
            rho = rho + val
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
            u = u + val * cx
            v = v + val * cy
            i = i + 1
        u = u / rho
        v = v / rho

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
                i2 = i2 + 1
        gid = gid + 1

def stream(f_in, f_out, n):
    N_Y = 50.0
    N_X = 300.0
    idx0 = 0
    gid = 0
    while gid < n:
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
            i = i + 1
        gid = gid + 1

def copy_frame(src, dst, offset, n):
    gid = 0
    while gid < n:
        dst_idx = offset + gid
        dst[dst_idx] = src[gid]
        gid = gid + 1

# ── Array declarations ────────────────────────────────────────────────────────
f_prev   = [0 for _ in range(135000)]
f_next   = [0 for _ in range(135000)]
f_post   = [0 for _ in range(135000)]
density  = [0 for _ in range(15000)]
u_vel    = [0 for _ in range(15000)]
v_vel    = [0 for _ in range(15000)]
obstacle = [0 for _ in range(15000)]
# Six full-resolution frames per buffer keep each returned array below 100000.
u_frames_0 = [0 for _ in range(90000)]
v_frames_0 = [0 for _ in range(90000)]
u_frames_1 = [0 for _ in range(90000)]
v_frames_1 = [0 for _ in range(90000)]
u_frames_2 = [0 for _ in range(90000)]
v_frames_2 = [0 for _ in range(90000)]
u_frames_3 = [0 for _ in range(90000)]
v_frames_3 = [0 for _ in range(90000)]
u_frames_4 = [0 for _ in range(90000)]
v_frames_4 = [0 for _ in range(90000)]
u_frames_5 = [0 for _ in range(90000)]
v_frames_5 = [0 for _ in range(90000)]
u_frames_6 = [0 for _ in range(60000)]
v_frames_6 = [0 for _ in range(60000)]
frame_steps = [0 for _ in range(40)]

# ── Physics parameters ────────────────────────────────────────────────────────
omega = 1.9704433

# ── Initialise geometry and flow field ───────────────────────────────────────
init_obstacle(obstacle, 15000)
init_fluid(f_prev, 15000)

# 15000 steps, saving 40 frames from step 5000 through 14750.
N_PAIRS = 7500
frame_count = 0
save_countdown = 2500
it = 0
while it < N_PAIRS:
    compute_macro_and_collide(f_prev, f_post, density, u_vel, v_vel, obstacle, omega, 15000)
    if save_countdown == 0:
        group = frame_count / 6.0
        FLOOR(group)
        offset = (frame_count - group * 6.0) * 15000.0
        if group == 0:
            copy_frame(u_vel, u_frames_0, offset, 15000)
            copy_frame(v_vel, v_frames_0, offset, 15000)
        if group == 1:
            copy_frame(u_vel, u_frames_1, offset, 15000)
            copy_frame(v_vel, v_frames_1, offset, 15000)
        if group == 2:
            copy_frame(u_vel, u_frames_2, offset, 15000)
            copy_frame(v_vel, v_frames_2, offset, 15000)
        if group == 3:
            copy_frame(u_vel, u_frames_3, offset, 15000)
            copy_frame(v_vel, v_frames_3, offset, 15000)
        if group == 4:
            copy_frame(u_vel, u_frames_4, offset, 15000)
            copy_frame(v_vel, v_frames_4, offset, 15000)
        if group == 5:
            copy_frame(u_vel, u_frames_5, offset, 15000)
            copy_frame(v_vel, v_frames_5, offset, 15000)
        if group == 6:
            copy_frame(u_vel, u_frames_6, offset, 15000)
            copy_frame(v_vel, v_frames_6, offset, 15000)
        saved_step = it * 2
        frame_steps[frame_count] = saved_step
        frame_count = frame_count + 1
        save_countdown = 125
    stream(f_post, f_next, 15000)
    compute_macro_and_collide(f_next, f_post, density, u_vel, v_vel, obstacle, omega, 15000)
    stream(f_post, f_prev, 15000)
    it = it + 1
    save_countdown = save_countdown - 1
