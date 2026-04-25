# NOTICE

This repository (Ramanujan) is independent work, but several test programs
under `ramanujan-test-codes/` are adapted from, inspired by, or interoperate
with third-party projects. This file records those acknowledgments.

---

## Newton — GPU-accelerated physics simulation

The example under [`ramanujan-test-codes/mpm_anymal/`](ramanujan-test-codes/mpm_anymal/)
is a scaled-down adaptation of the
[`mpm_anymal`](https://github.com/newton-physics/newton/blob/main/newton/examples/mpm/example_mpm_anymal.py)
example from the **Newton** physics engine, and it imports `newton` itself
(notably `newton.ModelBuilder` and `newton.viewer.ViewerGL`) for visualization.

Our deepest thanks to the Newton project and the people behind it for making
high-quality, GPU-accelerated robotics simulation freely available, and for
the example code that this directory builds on.

* **Project:** Newton — <https://github.com/newton-physics/newton>
* **License:** Apache License, Version 2.0
* **Documentation:** Creative Commons Attribution 4.0 (CC-BY-4.0)
* **Copyright:** Copyright (c) 2025 The Newton Developers
* **Linux Foundation project page:**
  <https://www.linuxfoundation.org/press/announcing-newton>

### Founding organizations

Newton was initiated and continues to be developed in collaboration with:

* **Disney Research** — <https://www.disneyresearch.com/>
* **Google DeepMind** — <https://deepmind.google/>
* **NVIDIA** — <https://www.nvidia.com/>

Newton is hosted as a community-built and community-maintained
[Linux Foundation](https://www.linuxfoundation.org/) project. Contributions
come from many individual developers across these organizations and the
wider robotics-simulation community; the canonical list of contributors is
maintained in the upstream Newton repository's commit history and governance
records (see <https://github.com/newton-physics/newton-governance>).

### Upstream components Newton builds on, which this example transitively benefits from

* **NVIDIA Warp** — <https://github.com/NVIDIA/warp>
* **MuJoCo Warp** — <https://github.com/google-deepmind/mujoco_warp>
* **OpenUSD** — <https://openusd.org/>

We thank these upstream projects as well; please refer to their respective
license and notice files for full attribution.

### Apache-2.0 attribution

In accordance with Section 4 of the Apache License, Version 2.0, this NOTICE
preserves and forwards Newton's attribution. The full license text is
available at <http://www.apache.org/licenses/LICENSE-2.0> and in the upstream
[Newton LICENSE](https://github.com/newton-physics/newton/blob/main/LICENSE.md).

### Scope of attribution

Only the example under `ramanujan-test-codes/mpm_anymal/` depends on Newton.
The Ramanujan platform itself (language, translator, rule engine, native
runtime, OpenCL backend) is independent work and is not derived from Newton.

---

For runtime/build dependencies of the Ramanujan platform itself, see
[`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md).
