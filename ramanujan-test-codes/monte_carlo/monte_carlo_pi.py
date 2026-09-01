points = [0 for _ in range(4)]
total_iterations_per_thread = 2000000.0

threadStart(t0) {
    points_in_circle_0 = 0.0
    i_0 = 0.0
    while i_0 < total_iterations_per_thread:
        x_0 = 0.0
        y_0 = 0.0
        RAND(x_0)
        RAND(y_0)
        dist_sq_0 = x_0 * x_0 + y_0 * y_0
        if dist_sq_0 <= 1.0:
            points_in_circle_0 = points_in_circle_0 + 1.0
        i_0 = i_0 + 1.0
    zero_0 = 0
    points[zero_0] = points_in_circle_0
}

threadStart(t1) {
    points_in_circle_1 = 0.0
    i_1 = 0.0
    while i_1 < total_iterations_per_thread:
        x_1 = 0.0
        y_1 = 0.0
        RAND(x_1)
        RAND(y_1)
        dist_sq_1 = x_1 * x_1 + y_1 * y_1
        if dist_sq_1 <= 1.0:
            points_in_circle_1 = points_in_circle_1 + 1.0
        i_1 = i_1 + 1.0
    one_1 = 1
    points[one_1] = points_in_circle_1
}

threadStart(t2) {
    points_in_circle_2 = 0.0
    i_2 = 0.0
    while i_2 < total_iterations_per_thread:
        x_2 = 0.0
        y_2 = 0.0
        RAND(x_2)
        RAND(y_2)
        dist_sq_2 = x_2 * x_2 + y_2 * y_2
        if dist_sq_2 <= 1.0:
            points_in_circle_2 = points_in_circle_2 + 1.0
        i_2 = i_2 + 1.0
    two_2 = 2
    points[two_2] = points_in_circle_2
}

threadStart(t3) {
    points_in_circle_3 = 0.0
    i_3 = 0.0
    while i_3 < total_iterations_per_thread:
        x_3 = 0.0
        y_3 = 0.0
        RAND(x_3)
        RAND(y_3)
        dist_sq_3 = x_3 * x_3 + y_3 * y_3
        if dist_sq_3 <= 1.0:
            points_in_circle_3 = points_in_circle_3 + 1.0
        i_3 = i_3 + 1.0
    three_3 = 3
    points[three_3] = points_in_circle_3
}

estimated_pi = 0.0
total_points_in_circle = 0.0
total_iterations = 0.0

threadOnEnd(t0, t1, t2, t3, 1) {
    zero_f = 0
    one_f = 1
    two_f = 2
    three_f = 3
    
    total_points_in_circle = points[zero_f]
    total_points_in_circle = total_points_in_circle + points[one_f]
    total_points_in_circle = total_points_in_circle + points[two_f]
    total_points_in_circle = total_points_in_circle + points[three_f]
    
    total_iterations = 8000000.0
    estimated_pi = 4.0 * total_points_in_circle / total_iterations
}
