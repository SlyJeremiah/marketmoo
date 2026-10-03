import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch
import os
from diagrams import KIND, GREEN, DGREEN, ORANGE, BLUE, GREY, OUT


def architecture_native():
    fig, ax = plt.subplots(figsize=(8.2, 8.0))
    ax.set_xlim(0, 10); ax.set_ylim(-0.7, 11.2); ax.axis("off")

    def layer(y, h, title, fc, ec):
        ax.add_patch(FancyBboxPatch((0.1, y), 9.8, h, boxstyle="round,pad=0.02,rounding_size=0.1", fc=fc, ec=ec, lw=1.4, zorder=1))
        ax.text(0.25, y + h - 0.1, title, fontsize=8.5, fontweight="bold", color=ec, va="top", zorder=2)

    def box(x, y, w, h, t, kind="screen", fs=6.9):
        k = KIND[kind]
        ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.02,rounding_size=0.07", fc=k["fc"], ec=k["ec"], lw=1.1, zorder=3))
        ax.text(x + w / 2, y + h / 2, t, ha="center", va="center", fontsize=fs, color=k["tc"], zorder=4, linespacing=1.15)

    def arr(x1, y1, x2, y2, col="#555", ls="-", both=False):
        ax.annotate("", xy=(x2, y2), xytext=(x1, y1), arrowprops=dict(arrowstyle="<|-|>" if both else "-|>", color=col, lw=1.3, linestyle=ls), zorder=5)

    # 1 field app
    layer(7.45, 3.7, "1  MARKETMOO FIELD APP (Android APK, offline-first)", "#fff7ec", ORANGE)
    box(0.3, 9.7, 9.4, 0.5, "Kotlin + Jetpack Compose UI:  Home | Market | Map | Learn | Help   (English, Shona, Ndebele; Data Saver)", "action", 7.0)
    dev = ["Room + SQLCipher\n(encrypted local DB:\nrecords, listings,\nsync queue)", "MapLibre Native\n+ PMTiles district\nbasemap", "District pack\n(facilities, 1 km\ngrid lookups,\nzones) + Turf-style\nlookups in Kotlin",
           "WorkManager sync\n(op-log, retry,\nidempotent UUIDs)", "TFLite (optional)\non-device pest-photo\nhint; Android\nKeystore keys"]
    for i, t in enumerate(dev):
        box(0.3 + i * 1.92, 7.6, 1.82, 1.95, t, "offline", 6.6)
    # 2 backend
    layer(4.65, 2.5, "2  BACKEND API (Django REST Framework on Render, free tier)", "#eef6fd", BLUE)
    be = ["Django 5 + DRF\nPython 3.12\n(REST + sync API)", "Bounding box +\nhaversine queries\n(nearest listings,\nzone check)", "Celery workers\n(planned: alerts,\npack builds, SMS)", "Phone + OTP sign-in;\nstaff password\nlogin (dashboard)", "Admin APIs\n(verify, publish\nnotices, layers)"]
    for i, t in enumerate(be):
        box(0.3 + i * 1.92, 4.8, 1.82, 1.55, t, "server", 6.6)
    # 3 data
    layer(2.35, 2.0, "3  DATA LAYER (Neon PostgreSQL + Backblaze B2)", "#f3f8fd", BLUE)
    da = ["Neon PostgreSQL\n(serverless)", "Permissions in DRF\n+ object-level\nchecks", "Backblaze B2:\nphotos (presigned\nupload)", "Backblaze B2:\nversioned district\npacks (range GET)"]
    for i, t in enumerate(da):
        box(0.3 + i * 2.4, 2.5, 2.2, 1.35, t, "server", 6.8)
    # 4 dashboard
    layer(-0.5, 2.5, "4  MANAGER DASHBOARD (web, Vercel) and GIS PIPELINE (batch)", "#f6f6f6", GREY)
    ds = ["React 18 + Recharts\n+ MapLibre GL JS\n(moderation, outbreak\nnotices, analytics)", "GIS pipeline (QGIS,\nPython, scipy):\nOSM, CHIRPS, NDVI,\nDEM, WorldCover", "Four analyses:\nmarket access,\nservice access,\nwater + grazing,\ndisease risk", "Pack builder:\nPMTiles + lookup\ntables, versioned,\nchecksummed"]
    for i, t in enumerate(ds):
        box(0.3 + i * 2.4, -0.35, 2.2, 1.75, t, "admin", 6.7)
    arr(5.0, 7.6, 5.0, 7.15, both=True)
    ax.text(5.15, 7.33, "HTTPS / TLS 1.3, sync when connected", fontsize=6.5, color="#333")
    arr(5.0, 4.65, 5.0, 4.35, both=True)
    ax.text(5.15, 4.48, "PostgreSQL connection pool", fontsize=6.5, color="#333")
    arr(5.0, 2.35, 5.0, 1.45, both=True)
    ax.text(5.15, 1.85, "REST API (session auth, IP-allowlisted)", fontsize=6.5, color="#333")
    fig.savefig(os.path.join(OUT, "architecture.png"), dpi=200, bbox_inches="tight", facecolor="white")
    plt.close(fig)


if __name__ == "__main__":
    architecture_native()
