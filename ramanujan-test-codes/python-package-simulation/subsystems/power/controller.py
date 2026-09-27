from common.math_ops import clamp

def init_power_state():
    return 28.0

def compute_power_control(current_volts, target_volts, thermal_derating):
    error = target_volts - current_volts
    shunt_action = 0.0
    if error < 0.0:
        shunt_action = -1.2 * error
    boost_action = 0.0
    if error > 0.0:
        boost_action = 1.5 * error
    net_power_cmd = (boost_action - shunt_action) * thermal_derating
    clamped_cmd = clamp(net_power_cmd, -20.0, 20.0)
    return clamped_cmd
