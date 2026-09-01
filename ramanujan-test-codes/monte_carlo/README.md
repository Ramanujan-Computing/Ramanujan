# Monte Carlo Pi

`monte_carlo_pi.py` estimates pi using four parallel Ramanujan threads. Each
thread samples 2,000,000 points, and the final DAG node combines all four
results.

## Run on Homelab

Run all commands from the repository root.

Start the homelab server in one terminal:

```sh
rj homelab
```

The server prints its address and opens an interactive prompt:

```text
HOMELAB_READY
HOMELAB_ADDRESS http://<server-ip>:8888
```

Start a worker in another terminal:

```sh
rj worker
```

Then enter this command in the homelab server terminal:

```text
run ramanujan-test-codes/monte_carlo/monte_carlo_pi.py
```

Wait for `KERNEL_DONE`, then query the results in the same server terminal:

```text
var estimated_pi
var total_points_in_circle
```

`estimated_pi` should be close to `3.14159`. Because this is a randomized
simulation, the exact value changes on every run.

## Direct JAR Commands

The equivalent commands without the `rj` helper are:

```sh
java -Xms64m -Xmx512m \
  -jar developer-console/target/developer-console-1.0-SNAPSHOT-fat.jar \
  homelab 8888
```

In another terminal, start four worker threads and point them at the server:

```sh
java -Xms64m -Xmx512m \
  -jar developer-console/target/developer-console-1.0-SNAPSHOT-fat.jar \
  worker http://localhost:8888 4
```

Replace `localhost` with the address printed by `HOMELAB_ADDRESS` when the
worker is running on another machine.