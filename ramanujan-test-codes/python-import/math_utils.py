def abs_val(x):
    ans = x
    if x < 0.0:
        ans = 0.0 - x
    return ans

def clamp(val, min_v, max_v):
    res = val
    if val < min_v:
        res = min_v
    else:
        if val > max_v:
            res = max_v
    return res

def square(x):
    res = x * x
    return res

def cube(x):
    res = x * x * x
    return res

def sqrt_approx(val):
    if val <= 0.0000001:
        res = 0.0
        return res
    guess = val
    if val > 1.0:
        guess = val / 2.0
    else:
        guess = 0.5
    it = 0
    while it < 15:
        div = val / guess
        add_term = guess + div
        guess = 0.5 * add_term
        it = it + 1
    return guess

def hypot2d(dx, dy):
    sq_x = square(dx)
    sq_y = square(dy)
    sum_sq = sq_x + sq_y
    dist = sqrt_approx(sum_sq)
    return dist
