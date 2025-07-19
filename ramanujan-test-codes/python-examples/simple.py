# Simple Python function equivalent to Ramanujan test case
def square_diff(x_pow, y_pow):
    if x_pow < y_pow:
        ans = y_pow - x_pow
    else:
        ans = x_pow - y_pow
    return ans

# Variable assignment
i = 0
j = 0

# While loop
while i < 10:
    result = square_diff(i, 5)
    j = j + result
    i = i + 1