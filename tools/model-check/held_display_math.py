"""Vanilla XYZ display rotations, composed about the *local* handle axis.

Changing Euler Y alone is not a handle twist when a pose already has Z roll.
Geometry, translations, scales and non-hand display contexts stay unchanged.
"""
import copy
import math


def matrix_xyz(degrees):
    x, y, z = map(math.radians, degrees)
    a, b, c, d, e, f = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    return [[c * e, -c * f, d], [b * d * e + a * f, -b * d * f + a * e, -b * c],
        [-a * d * e + b * f, a * d * f + b * e, a * c]]


def multiply(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def euler_xyz(matrix):
    y = math.asin(max(-1, min(1, matrix[0][2])))
    if abs(matrix[0][2]) < 1 - 1e-12:
        x, z = math.atan2(-matrix[1][2], matrix[2][2]), math.atan2(-matrix[0][1], matrix[0][0])
    else:
        x, z = math.atan2(matrix[2][1], matrix[1][1]), 0
    result = [round(math.degrees(v), 8) for v in (x, y, z)]
    result = [0 if abs(v) < 1e-7 else v for v in result]
    rebuilt = matrix_xyz(result)
    assert max(abs(matrix[i][j] - rebuilt[i][j]) for i in range(3) for j in range(3)) < 1e-8
    return result


def twist_handle(rotation, degrees):
    original = matrix_xyz(rotation)
    result = euler_xyz(multiply(original, matrix_xyz([0, degrees, 0])))
    actual = matrix_xyz(result)
    # Shaft direction must not move; this is a twist, not a different attack pose.
    assert max(abs(original[i][1] - actual[i][1]) for i in range(3)) < 1e-8
    return result


def blade_edge_forward(display):
    corrected = copy.deepcopy(display)
    for slot, transform in corrected.items():
        if slot.startswith(("firstperson_", "thirdperson_")):
            # Vanilla negates Y/Z for left hands. Counter-twist the stored left
            # pose so the same physical cutting edge points forwards, not back.
            degrees = -90 if slot.endswith("lefthand") else 90
            transform["rotation"] = twist_handle(transform.get("rotation", [0, 0, 0]), degrees)
    return corrected
