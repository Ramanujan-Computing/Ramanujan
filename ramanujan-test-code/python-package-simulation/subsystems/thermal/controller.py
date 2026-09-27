from common.math_ops import clamp

def init_thermal_state():
    return 295.0

def compute_thermal_control(current_temp, target_temp, external_flux):
    error = target_temp - current_temp
    cooling_effort = 0.0
    if error < 0.0:
        cooling_effort = -0.5 * error
    heating_effort = 0.0
    if error > 0.0:
        heating_effort = 0.8 * error
    net_thermal_cmd = heating_effort - cooling_effort + external_flux * 0.1
    clamped_cmd = clamp(net_thermal_cmd, -50.0, 50.0)
    return clamped_cmd
