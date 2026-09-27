def clamp(val, low, high):
    if val < low:
        return low
    if val > high:
        return high
    return val

def weighted_blend(v1, v2, alpha):
    res = alpha * v1 + (1.0 - alpha) * v2
    return res
