from common.math_ops import clamp, weighted_blend
from subsystems.thermal.sensor import read_temperature
from subsystems.thermal.controller import init_thermal_state, compute_thermal_control
from subsystems.power.sensor import read_voltage
from subsystems.power.controller import init_power_state, compute_power_control

# State arrays
temp_history = [0 for _ in range(10)]
power_history = [0 for _ in range(10)]

# Initialize states
curr_temp = init_thermal_state()
curr_volts = init_power_state()

target_temp = 300.0
target_volts = 28.0

step = 0
while step < 10:
    # Read sensor values
    s_temp = read_temperature(curr_temp, 0.1)
    s_volts = read_voltage(curr_volts, 0.2)
    
    # Power derating based on temperature deviation from nominal 295K
    t_diff = s_temp - 295.0
    derating = 1.0 - (t_diff * 0.01)
    derating = clamp(derating, 0.7, 1.3)
    
    # Compute subsystem controls
    p_cmd = compute_power_control(s_volts, target_volts, derating)
    ext_flux = p_cmd * 0.1
    t_cmd = compute_thermal_control(s_temp, target_temp, ext_flux)
    
    # State update
    curr_temp = curr_temp + (t_cmd * 0.2)
    curr_volts = curr_volts + (p_cmd * 0.15)
    
    temp_history[step] = curr_temp
    power_history[step] = curr_volts
    step = step + 1

simulation_done = 1.0
final_temp = curr_temp
final_volts = curr_volts
temp_drift = target_temp - curr_temp
volts_drift = target_volts - curr_volts
