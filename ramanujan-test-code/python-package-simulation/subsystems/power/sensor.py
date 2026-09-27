def read_voltage(raw_val, load_factor):
    effective_volts = raw_val - (load_factor * 0.5)
    return effective_volts
