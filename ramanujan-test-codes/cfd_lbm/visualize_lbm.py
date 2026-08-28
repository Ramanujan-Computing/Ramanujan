import os
import sys
import numpy as np
import matplotlib.pyplot as plt
import matplotlib.animation as animation
try:
    import cmasher as cmr
except ImportError:
    print("Warning: cmasher not found, using fallback colormaps.")
    class cmr:
        amber    = "inferno"
        redshift = "RdBu"

def read_flat_csv(path):
    with open(path) as f:
        text = f.read().strip()
    if not text:
        return []
    return [float(t) for t in text.split(",") if t]

def main():
    out_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "lbm_output")

    u_csv = os.path.join(out_dir, "u_frames.csv")
    v_csv = os.path.join(out_dir, "v_frames.csv")
    if not os.path.exists(u_csv):
        print("Error: u_frames.csv not found. Run the simulation first.")
        sys.exit(1)

    print("Loading frames ...")
    u_flat = read_flat_csv(u_csv)
    v_flat = read_flat_csv(v_csv)

    N_X, N_Y  = 300, 50
    CELLS     = N_X * N_Y
    n_frames  = len(u_flat) // CELLS
    print(f"  {n_frames} frames, {CELLS} cells each.")

    u_all = np.array(u_flat[: n_frames * CELLS]).reshape(n_frames, N_X, N_Y)
    v_all = np.array(v_flat[: n_frames * CELLS]).reshape(n_frames, N_X, N_Y)

    # Singleton: 100 frames covering steps 5000-14900 (every 100 steps).
    u_frames = u_all
    v_frames = v_all
    step_offset    = 5000
    steps_per_frame = 100

    X, Y = np.meshgrid(np.arange(N_X), np.arange(N_Y), indexing="ij")
    CX, CY, CR = 60, 25, 5

    plt.style.use("dark_background")
    fig, (ax_vel, ax_curl) = plt.subplots(2, 1, figsize=(15, 6), dpi=100)
    fig.tight_layout(pad=2.0)

    def animate(i):
        ax_vel.clear()
        ax_curl.clear()

        u    = u_frames[i]
        v    = v_frames[i]
        step = step_offset + i * steps_per_frame

        speed = np.sqrt(u ** 2 + v ** 2)
        curl  = np.gradient(u, axis=1) - np.gradient(v, axis=0)

        ax_vel.contourf(X, Y, speed, levels=50, cmap=cmr.amber)
        ax_vel.add_patch(plt.Circle((CX, CY), CR, color="darkgreen"))
        ax_vel.set_title(f"Velocity magnitude  (step {step})", color="white")
        ax_vel.set_aspect("equal")

        ax_curl.contourf(X, Y, curl, levels=50, cmap=cmr.redshift, vmin=-0.02, vmax=0.02)
        ax_curl.add_patch(plt.Circle((CX, CY), CR, color="darkgreen"))
        ax_curl.set_title(f"Vorticity  (step {step})", color="white")
        ax_curl.set_aspect("equal")

    anim = animation.FuncAnimation(fig, animate, frames=len(u_frames), interval=80)

    out_gif = os.path.join(out_dir, "simulation_video.gif")
    print(f"Saving {out_gif} ({len(u_frames)} frames @ 12 fps) ...")
    anim.save(out_gif, writer="pillow", fps=12)
    print("Done!")

if __name__ == "__main__":
    main()
