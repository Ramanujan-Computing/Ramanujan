def read_temperature(raw_val, noise_offset):
    temp = raw_val + noise_offset
    return temp
