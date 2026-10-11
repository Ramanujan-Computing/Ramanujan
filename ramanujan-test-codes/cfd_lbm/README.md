# Lattice Boltzmann Method (LBM) CFD Simulation

This directory contains a highly optimized 2D fluid dynamics simulation written for the Ramanujan homelab platform. It simulates **fluid flow past a cylinder** to observe the classical [von Kármán vortex street](https://en.wikipedia.org/wiki/K%C3%A1rm%C3%A1n_vortex_street).

The simulation is implemented using the **D2Q9 Lattice Boltzmann Method (LBM)** with a BGK collision operator.

## Architecture & Design

To maximize performance and maintain state coherence, the simulation uses a **singleton kernel architecture**:
- **Zero Host-Device Overheads:** The entire simulation (15,000 time steps) runs inside a single GPU kernel invocation (`lbm_singleton.py`).
- **Live Memory:** All distribution functions (f_prev, f_next, f_post) stay alive in GPU memory for the entire run. This is critical because the tiny perturbations required to trigger asymmetric vortex shedding at this Reynolds number get destroyed if the state is round-tripped through CSV files between batches.
- **In-Kernel Frame Capture:** A dedicated GPU kernel (`copy_frame_GPU_1`) captures velocity slices every 100 steps into a pre-allocated mega-buffer, which is dumped to the host only once at the very end.

## Physics Parameters

- **Grid Size**: 300 x 50 cells
- **Reynolds Number (Re)**: 80
- **Time Steps**: 15,000 total (Frames are captured from steps 5,000 to 14,900 after the flow is fully established).
- **Collision Operator**: BGK (Bhatnagar-Gross-Krook)
- **Obstacle**: Bounce-back (no-slip) circular cylinder of radius 5, centered at (60, 25).
- **Boundary Conditions**: 
  - Inlet (x=0): Zou/He velocity Dirichlet (u=0.04, v=0)
  - Outlet (x=299): Zero-gradient outflow
  - Walls (y=0, y=49): Periodic wrapping
- **Symmetry Breaking**: A tiny upward velocity perturbation is applied above the cylinder during initialization to seed the instability and trigger vortex shedding, as GPU `float32` rounding noise alone is too perfectly symmetric.

## Files

- **`lbm_singleton.py`**: The Ramanujan Python kernel. It strictly adheres to Ramanujan's GPU compilation semantics (e.g., all array indices must be pre-computed into single variables, loops must be `while` constructs, no recursion).
- **`run_lbm_singleton.py`**: The orchestrator script. It submits the singleton kernel to the homelab worker, waits for it to finish, and dumps the velocity fields.
- **`visualize_lbm.py`**: Python script using `matplotlib` to render the velocity magnitude and vorticity (curl) fields into an animated GIF.
- **`lbm_output/`**: Directory where the raw CSV outputs and the final `simulation_video.gif` are saved.

*(Note: `lbm_ramanujan.py` and `run_lbm_simulation.py` are legacy files from an earlier sharded implementation that suffered from state-loss between batches).*

## How to Run

### CPU execution through the localhost server

`lbm_cpu.py` runs all 15,000 steps on the same 300 x 50 grid without GPU
intrinsics. It captures 40 full-resolution frames at steps 5,000 through 14,750,
every 250 steps. The velocity frames are split into seven buffers per component
so each array is at most 90,000 values. Cloud workers upload results directly
to object storage rather than passing the large payload through Cloud Run.

Start the middleware server and Prayog with Prayog's `orchestrator.url` set to
`http://localhost:8888`. Submit using developer-console:

```bash
java -Dserver.url=http://localhost:8888 \
  -jar ../../developer-console/target/developer-console-1.0-SNAPSHOT-fat.jar \
  execute lbm_cpu.py
```

In the query console, dump `u_frames_0` through `u_frames_6`, `v_frames_0`
through `v_frames_6`, and `frame_steps` to identically named CSV files in
`lbm_output/`. Run `python3 visualize_lbm.py` to render the GIF, or use
`--output-dir` to keep a new run separate from older results.

The queue consumer waits for subscription readiness and does not restart the
server merely because result persistence takes more than a minute. Processing
tasks are requeued before their polling messages are acknowledged; failed
republication leaves the original message unacknowledged for redelivery.
Consumers do not need machine affinity: task status and results are read from
shared MySQL and object storage. A shared Pub/Sub subscription distributes work across
consumers; receiving a poll before computation commits is normal, not proof of
a MySQL race. Separate development and production data/queues if isolation is
required rather than relying on a Cloud Run minimum instance count of zero.
On Cloud Run, keep the private Cloud SQL address in the server configuration.
`RAMANUJAN_DB_POOL_SIZE` sets the JDBC pool limit (the deployed service uses 8);
without an override, the existing CPU-based default is preserved. Startup
verifies a database connection, allowing up to 120 seconds for initial pool
connection attempts while Cloud Run VPC networking becomes available. Configure
the Cloud Run startup probe with a 240-second budget; initialization still fails
explicitly rather than retrying forever.
Disable Cloud Run CPU throttling so queue consumption and persistence continue
between requests. HTTP body reception runs on the event loop; translation and
result completion handlers run on worker threads.

The `arrayMapping` unique key must be `(asyncId, arrayId, indexStr)`, not
`(asyncId, arrayName, indexStr)`: result persistence can omit display names,
and different arrays must never overwrite each other's values. Existing
deployments need `db-layer/src/main/resources/arrayMapping-arrayId-migration.sql`
applied once, after checking for duplicate array identities. Adapt the legacy
index name as noted in that file. This migration preserves existing rows but
cannot restore values overwritten before the fix; rerun affected computations.
For large cloud outputs, export the persisted values directly from MySQL:
the expanded `/status` JSON can exceed Cloud Run's HTTP/1 response limit even
when the worker uploads directly to object storage.

### Signed result uploads

For object-storage-backed execution, orchestrator `StorageDao.createResultUploadTarget`
provides a V4 signed PUT URL valid for 24 hours in the worker assignment.
Each target is scoped to one task, worker and upload attempt in the
`dag_element_result` namespace at `uploads/<taskId>/<hostId>/<attemptId>`. The worker
sends the JSON `HostResult` snapshot directly to that URL using the supplied
headers, then calls `/task/complete` with `uuid`, `hostId`, `resultObject`,
`resultSize` and the base64 `resultMd5`, without inline `data`.

Completion checks the current worker assignment, verifies the object's size,
MD5 and content type, and promotes it to the existing task result location
using a server-side copy. The upload and accepted result cannot overwrite
an existing object; repeating completion is idempotent. Heartbeats continue
during computation, upload and completion retries.

Middleware still converts the native snapshot into `DagVertexOutput` and
compacts parent vertex outputs from object storage for dependent vertices. This preserves
the existing compaction contract without making workers depend on middleware
classes. Local storage and older workers retain the inline completion protocol.
Deploy the server before the worker so both old and new clients are supported
during rollout.

Signing requires service-account credentials that implement
`ServiceAccountSigner`. Cloud Run application-default credentials use the
IAM Credentials API and need `iam.serviceAccounts.signBlob` on the signing
service account (normally a self-scoped Service Account Token Creator grant).
Workers do not need storage credentials. Signed URLs are bearer credentials and
must not be logged. URL expiry does not delete objects: configure a bucket
lifecycle rule limited to the `uploads/` prefix to remove staging objects
after the upload/retry retention window (for example, two days).

See [DAG Result Storage](../../docs/architecture.md#dag-result-storage) for the
object layout, middleware persistence, parent compaction, and cleanup lifecycle.

### GPU singleton execution

1. **Start the simulation:**
   ```bash
   python3 run_lbm_singleton.py --homelab-url http://localhost:8888
   ```
   *This submits the job to the homelab worker. It will take a few minutes to complete all 15,000 steps. Once done, it will download `u_frames.csv` and `v_frames.csv` to the `lbm_output/` directory.*

2. **Generate the animation:**
   ```bash
   python3 visualize_lbm.py
   ```
   *This reads the dumped frames and renders `lbm_output/simulation_video.gif` showing the velocity and vorticity fields.*
