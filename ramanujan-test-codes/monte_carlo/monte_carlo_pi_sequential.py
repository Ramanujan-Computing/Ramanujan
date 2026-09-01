total_iterations = 8000000.0
total_points_in_circle = 0.0
i = 0.0

while i < total_iterations:
    x = 0.0
    y = 0.0
    RAND(x)
    RAND(y)
    dist_sq = x * x + y * y
    if dist_sq <= 1.0:
        total_points_in_circle = total_points_in_circle + 1.0
    i = i + 1.0

estimated_pi = 4.0 * total_points_in_circle / total_iterations
