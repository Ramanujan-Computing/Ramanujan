import math_utils as mu
from math_utils import square, sqrt_approx

def softened_dist(dx, dy, eps):
    sq_x = square(dx)
    sq_y = square(dy)
    sq_eps = square(eps)
    sum_all = sq_x + sq_y + sq_eps
    d = sqrt_approx(sum_all)
    return d

def compute_force_scalar(m1, m2, dist, G):
    d_sq = square(dist)
    f = G * m1 * m2 / d_sq
    return f

def compute_acceleration_on_body(m_source, dx, dy, G, eps):
    r = softened_dist(dx, dy, eps)
    r_sq = square(r)
    r_cb = r_sq * r
    scale = G * m_source / r_cb
    ax = dx * scale
    ay = dy * scale
    return ax, ay

def compute_kinetic_energy(mass, vx, vy):
    v_sq_x = square(vx)
    v_sq_y = square(vy)
    v_sq = v_sq_x + v_sq_y
    ke = 0.5 * mass * v_sq
    return ke

def compute_potential_energy(m1, m2, dx, dy, G, eps):
    r = softened_dist(dx, dy, eps)
    pe = 0.0 - (G * m1 * m2 / r)
    return pe
