# Project Description:
This project aims to utilize the untapped computation power of digital devices. Any device that has a CPU which can do
basic arithmetic operations and can connect to the internet contribute to the computation power of the network.

## The inspiration behind this project:
Apollo guidance computer had a CPU which was only as powerful as a modern scientific calculator. Modern smartphones and
smart TVs are at-least million times more powerful than the Apollo guidance computer, and most of the time they are
idle. This project aims to utilize the idle computation power of these devices.

## What all devices can be supported on the network:
Any device which has a CPU that can do basic arithmetic operations and can connect to the internet can be added on the 
network. This includes smartphones, smart TVs, smart-watches, smart speakers, smart refrigerators, smart washing machines,
and so on. Currently, the network supports only Android devices, linux devices, Windows devices, and macOS devices.

## How is this different from BOINC:
BOINC is an amazing platform that allows users to donate their computation power to various scientific projects. However,
for adding a new kind of computation on the network, the project owner has to write a new client for the BOINC network, 
and the devices on the network that can contribute to the computation have to download and install the new client.

In this platform, for any new kind of computation, the project owner doesn't have to write a new client. The owner just
have to give the computation code, and the platform would convert it to an intermediate code that the device can just
run. The devices on the network don't have to download and install a new client for every new kind of computation.
They just have to download and install the `ramanujan` client one-time.

> **Note:** The custom `ramanujan` language (described in [docs/ramanujan-language.md](docs/ramanujan-language.md)) is
> now **deprecated**. All active development is focused on writing computations directly in **Python** — see
> [docs/python-support.md](docs/python-support.md) for details.

## Python is the front-end, Ramanujan is the runtime:
Researchers and simulation developers just need to write their simulation in Python and run it on the cluster. The
cluster nodes don't need Python installed on them — each node runs the native **Ramanujan interpreter**, which executes
the compiled simulation directly. See [docs/architecture.md](docs/architecture.md) for more on how Python code is
compiled down to the Ramanujan runtime.


## Documentation
This README covers only the high-level project description. The rest of the documentation has been split into
focused pages under [docs/](docs/):

| Document | Description |
|---|---|
| [docs/python-support.md](docs/python-support.md) | Writing computations in Python (actively developed) — supported/unsupported features, examples. |
| [docs/gpu-acceleration.md](docs/gpu-acceleration.md) | GPU acceleration support via OpenCL — kernel generation, built-ins, memory management. |
| [docs/ramanujan-language.md](docs/ramanujan-language.md) | The original `ramanujan` language (**deprecated**) — variables, arrays, functions, loops, threads. |
| [docs/architecture.md](docs/architecture.md) | Code-flow across dev-console, middleware, orchestrator, and the native interpreter. |
| [docs/build-and-usage.md](docs/build-and-usage.md) | Build and usage strategy — Maven build, native build, Docker, configuration. |
| [docs/contributor-guide.md](docs/contributor-guide.md) | Contributor quick start — running a Monte Carlo example on a computer and Android phone. |
| [docs/roadmap.md](docs/roadmap.md) | Future of the language and platform. |
| [docs/csv-data-usage.md](docs/csv-data-usage.md) | Direct CSV data loading, the `dump` command, and the Phi-3 inference example. |

