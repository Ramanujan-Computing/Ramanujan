# N-Body Orbital Dynamics Simulation with Python Imports

This package provides a multi-file Python simulation designed to test and demonstrate Ramanujan's Python import system.

## Overview

The simulation models an **N-body gravitational system** (a central massive star, two orbiting planets, and an eccentric comet/asteroid) using the **Velocity-Verlet numerical integration** algorithm with softened gravitational potentials.

Key features tested across the modular architecture:
- **Module Imports**: `import math_utils as mu`, `import integrator`
- **Direct Function Imports**: `from physics import compute_kinetic_energy`
- **Function Imports with Aliases**: `import physics as phys`
- **Wildcard Imports**: `from math_utils import *`
- **Transitive Imports**: `integrator.py` imports `physics.py` which in turn imports `math_utils.py`
- **Intra-Module Calls**: `math_utils.hypot2d` internally invokes `square` and `sqrt_approx` within the same module
- **Tuple Return Values**: Functions like `physics.compute_acceleration_on_body` and `diagnostics.compute_system_momentum` returning multiple values unpacked into caller variables
- **Array Mutation**: In-place mutation and updating of simulation state arrays (`pos_x`, `pos_y`, `vel_x`, `vel_y`, `acc_x`, `acc_y`) passed by reference across module boundaries
- **Conservation Diagnostics**: Energy conservation (total energy $E = KE + PE$) and linear momentum conservation checks

## Module Structure

| File | Description | Import Syntax Tested |
|---|---|---|
| `math_utils.py` | Math primitives: Newton-Raphson `sqrt_approx`, `square`, `cube`, `abs_val`, `clamp`, `hypot2d`. | Intra-module function calls |
| `physics.py` | Gravitational acceleration calculation with Plummer softening, kinetic and pairwise potential energy. | `import ... as ...`, `from ... import ...` |
| `integrator.py` | Velocity-Verlet symplectic integrator: position update, acceleration accumulation, velocity update, boundary damping. | Transitive imports (`integrator` -> `physics` -> `math_utils`) |
| `diagnostics.py` | System-level conservation monitoring: total kinetic energy, total potential energy, total momentum, center of mass. | Wildcard `from ... import *`, `import ... as ...` |
| `main.py` | Main execution entry point. Initializes 4 celestial bodies, steps through simulation loop, and verifies energy drift. | Mixed imports from all modules |
| `run_simulation.py` | Standalone Python runner and validator script to print physics diagnostics. | Verification runner |

## Running the Simulation

### Direct Python Execution
```bash
python ramanujan-test-code/python-import/run_simulation.py
```

### Running via Ramanujan Test Suite
```bash
mvn -f middleware/pom.xml test -pl translation -Dtest=PythonMultiFileImportTest
```
