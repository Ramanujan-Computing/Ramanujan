# Spacecraft Subsystems Simulation (Package-Style Imports & Custom Entrypoint)

A modular Python simulation modeling coupled spacecraft thermal and power management subsystems.

## Key Features Tested

1. **Non-Standard Entrypoint (`app.py`)**:
   - The simulation entrypoint is named `app.py` instead of the traditional `main.py`.
   - Validates that Ramanujan runtime and REST / Homelab server correctly resolve arbitrary entrypoint files rather than hardcoding `main.py`.

2. **Package Structure with Duplicate Basenames**:
   - `subsystems/thermal/controller.py` vs. `subsystems/power/controller.py` (duplicate basename `controller.py`).
   - `subsystems/thermal/sensor.py` vs. `subsystems/power/sensor.py` (duplicate basename `sensor.py`).
   - Validates deterministic, stable package-qualified module resolution (`from subsystems.thermal.controller import ...` vs. `from subsystems.power.controller import ...`) regardless of dictionary iteration order.

3. **Transitive Inter-Package Imports**:
   - Both `thermal/controller.py` and `power/controller.py` import `from common.math_ops import clamp`.

4. **Coupled Subsystem Dynamics**:
   - Temperature affects battery efficiency and voltage derating.
   - Electrical power dissipation introduces heat flux into the thermal loop.
   - History vectors recorded in `temp_history` and `power_history`.

## Directory Structure

```
python-package-simulation/
├── app.py                            # Custom entrypoint
├── run_simulation.py                 # Runner with standard python & homelab support
├── README.md                         # Documentation
├── common/
│   └── math_ops.py                   # Common math utilities (clamp, blend)
└── subsystems/
    ├── thermal/
    │   ├── sensor.py                 # Thermal sensor logic
    │   └── controller.py             # Thermal heating/cooling loop
    └── power/
        ├── sensor.py                 # Power/voltage sensor logic
        └── controller.py             # Power shunt/boost control loop
```

## Running the Simulation

### 1. Direct Python Execution
```bash
python ramanujan-test-code/python-package-simulation/run_simulation.py
```

### 2. Running via Ramanujan Homelab Server and Worker

Start the Homelab server in Terminal 1:
```bash
java -jar developer-console/target/developer-console-1.0-SNAPSHOT-fat.jar homelab 8888
```

Start the local worker in Terminal 2:
```bash
java -jar developer-console/target/developer-console-1.0-SNAPSHOT-fat.jar worker http://localhost:8888 2
```

Submit the simulation and verify execution in Terminal 3:
```bash
python ramanujan-test-code/python-package-simulation/run_simulation.py --homelab
```
