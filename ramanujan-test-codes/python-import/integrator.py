import physics
import math_utils as mu

def compute_accelerations(pos_x, pos_y, mass, acc_x, acc_y, n_bodies, G, eps):
    i = 0
    while i < n_bodies:
        acc_x[i] = 0.0
        acc_y[i] = 0.0
        i = i + 1

    i = 0
    while i < n_bodies:
        j = 0
        while j < n_bodies:
            if i != j:
                x_i = pos_x[i]
                y_i = pos_y[i]
                x_j = pos_x[j]
                y_j = pos_y[j]
                dx = x_j - x_i
                dy = y_j - y_i
                m_j = mass[j]

                ax, ay = physics.compute_acceleration_on_body(m_j, dx, dy, G, eps)

                curr_ax = acc_x[i]
                curr_ay = acc_y[i]
                acc_x[i] = curr_ax + ax
                acc_y[i] = curr_ay + ay
            j = j + 1
        i = i + 1

def update_positions(pos_x, pos_y, vel_x, vel_y, acc_x, acc_y, n_bodies, dt):
    half_dt_sq = 0.5 * dt * dt
    i = 0
    while i < n_bodies:
        px = pos_x[i]
        py = pos_y[i]
        vx = vel_x[i]
        vy = vel_y[i]
        ax = acc_x[i]
        ay = acc_y[i]

        new_px = px + vx * dt + ax * half_dt_sq
        new_py = py + vy * dt + ay * half_dt_sq

        pos_x[i] = new_px
        pos_y[i] = new_py
        i = i + 1

def update_velocities(vel_x, vel_y, old_acc_x, old_acc_y, new_acc_x, new_acc_y, n_bodies, dt):
    half_dt = 0.5 * dt
    i = 0
    while i < n_bodies:
        vx = vel_x[i]
        vy = vel_y[i]
        oa_x = old_acc_x[i]
        oa_y = old_acc_y[i]
        na_x = new_acc_x[i]
        na_y = new_acc_y[i]

        sum_ax = oa_x + na_x
        sum_ay = oa_y + na_y

        vel_x[i] = vx + sum_ax * half_dt
        vel_y[i] = vy + sum_ay * half_dt
        i = i + 1

def apply_boundary_damping(pos_x, pos_y, vel_x, vel_y, n_bodies, bound, damping):
    neg_bound = 0.0 - bound
    i = 0
    while i < n_bodies:
        px = pos_x[i]
        py = pos_y[i]
        vx = vel_x[i]
        vy = vel_y[i]

        if px > bound:
            pos_x[i] = bound
            vel_x[i] = 0.0 - vx * damping
        if px < neg_bound:
            pos_x[i] = neg_bound
            vel_x[i] = 0.0 - vx * damping

        if py > bound:
            pos_y[i] = bound
            vel_y[i] = 0.0 - vy * damping
        if py < neg_bound:
            pos_y[i] = neg_bound
            vel_y[i] = 0.0 - vy * damping

        i = i + 1
