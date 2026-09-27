from math_utils import *
import physics as phys

def compute_total_kinetic_energy(mass, vel_x, vel_y, n_bodies):
    total_ke = 0.0
    i = 0
    while i < n_bodies:
        m = mass[i]
        vx = vel_x[i]
        vy = vel_y[i]
        ke = phys.compute_kinetic_energy(m, vx, vy)
        total_ke = total_ke + ke
        i = i + 1
    return total_ke

def compute_total_potential_energy(pos_x, pos_y, mass, n_bodies, G, eps):
    total_pe = 0.0
    i = 0
    while i < n_bodies:
        j = 0
        while j < n_bodies:
            if j > i:
                m_i = mass[i]
                m_j = mass[j]
                x_i = pos_x[i]
                y_i = pos_y[i]
                x_j = pos_x[j]
                y_j = pos_y[j]
                dx = x_j - x_i
                dy = y_j - y_i
                pe = phys.compute_potential_energy(m_i, m_j, dx, dy, G, eps)
                total_pe = total_pe + pe
            j = j + 1
        i = i + 1
    return total_pe

def compute_system_momentum(mass, vel_x, vel_y, n_bodies):
    tot_px = 0.0
    tot_py = 0.0
    i = 0
    while i < n_bodies:
        m = mass[i]
        vx = vel_x[i]
        vy = vel_y[i]
        tot_px = tot_px + m * vx
        tot_py = tot_py + m * vy
        i = i + 1
    return tot_px, tot_py

def compute_center_of_mass(mass, pos_x, pos_y, n_bodies):
    tot_m = 0.0
    sum_mx = 0.0
    sum_my = 0.0
    i = 0
    while i < n_bodies:
        m = mass[i]
        px = pos_x[i]
        py = pos_y[i]
        tot_m = tot_m + m
        sum_mx = sum_mx + m * px
        sum_my = sum_my + m * py
        i = i + 1
    cm_x = sum_mx / tot_m
    cm_y = sum_my / tot_m
    return cm_x, cm_y
