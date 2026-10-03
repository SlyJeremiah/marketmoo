import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, Polygon
import os

OUT = os.path.join(os.path.dirname(__file__), "img")
os.makedirs(OUT, exist_ok=True)

GREEN = "#1f7a3a"; DGREEN = "#155c2b"; GOLD = "#ffe08a"; EARTH = "#8b5e34"
GREY = "#2b2b2b"; BLUE = "#1e88e5"; ORANGE = "#e67e22"; CREAM = "#faf7f0"; RED = "#c0392b"

KIND = {
    "start":   dict(fc=GREEN,  ec=DGREEN, tc="white"),
    "end":     dict(fc=GREEN,  ec=DGREEN, tc="white"),
    "screen":  dict(fc="white", ec=GREEN,  tc="#1c1c1c"),
    "action":  dict(fc="#e6f4ea", ec=GREEN, tc="#1c1c1c"),
    "decide":  dict(fc=GOLD,   ec=EARTH,  tc="#1c1c1c"),
    "offline": dict(fc="#fdebd3", ec=ORANGE, tc="#1c1c1c"),
    "server":  dict(fc="#e3f2fd", ec=BLUE,  tc="#1c1c1c"),
    "admin":   dict(fc="#ececec", ec=GREY,  tc="#1c1c1c"),
}

CW, CH = 2.3, 1.45   # grid cell size
BW, BH = 2.12, 0.95   # box size


def flow(name, nodes, edges, cols, rows, title=None, fs=8.5, legend=True, figw=8.2):
    figh = figw * (rows * CH + 0.9) / (cols * CW + 0.3)
    fig, ax = plt.subplots(figsize=(figw, figh))
    ax.set_xlim(-0.2, cols * CW + 0.1)
    ax.set_ylim(-0.3, rows * CH + 0.6)
    ax.axis("off")
    pos = {}
    for nid, label, gx, gy, kind in nodes:
        cx = gx * CW + CW / 2
        cy = (rows - 1 - gy) * CH + CH / 2
        pos[nid] = (cx, cy, kind)
        k = KIND[kind]
        if kind == "decide":
            w, h = BW * 0.58, BH * 0.72
            ax.add_patch(Polygon([(cx, cy + h), (cx + w, cy), (cx, cy - h), (cx - w, cy)],
                                 closed=True, fc=k["fc"], ec=k["ec"], lw=1.4, zorder=3))
        else:
            style = "round,pad=0.02,rounding_size=0.32" if kind in ("start", "end") else "round,pad=0.02,rounding_size=0.08"
            ax.add_patch(FancyBboxPatch((cx - BW / 2, cy - BH / 2), BW, BH, boxstyle=style,
                                        fc=k["fc"], ec=k["ec"], lw=1.4, zorder=3))
        ax.text(cx, cy, label, ha="center", va="center", fontsize=fs, color=k["tc"], zorder=4,
                fontweight="bold" if kind in ("start", "end") else "normal", linespacing=1.15)

    def anchor(a, b):
        ax_, ay_, ak = pos[a]; bx_, by_, bk = pos[b]
        dx, dy = bx_ - ax_, by_ - ay_
        def edge(x, y, k, ddx, ddy):
            hw = BW * 0.58 if k == "decide" else BW / 2
            hh = BH * 0.72 if k == "decide" else BH / 2
            if abs(ddx) * hh >= abs(ddy) * hw:
                return (x + (hw if ddx > 0 else -hw), y)
            return (x, y + (hh if ddy > 0 else -hh))
        return edge(ax_, ay_, ak, dx, dy), edge(bx_, by_, bk, -dx, -dy)

    def side_pt(nid, s):
        x, y, k = pos[nid]
        hw = BW * 0.58 if k == "decide" else BW / 2
        hh = BH * 0.72 if k == "decide" else BH / 2
        return {"l": (x - hw, y), "r": (x + hw, y), "t": (x, y + hh), "b": (x, y - hh)}[s]

    for e in edges:
        a, b = e[0], e[1]
        label = e[2] if len(e) > 2 else ""
        style = e[3] if len(e) > 3 else "-"
        if len(e) > 5:
            (x1, y1), (x2, y2) = side_pt(a, e[5][0]), side_pt(b, e[5][1])
        else:
            (x1, y1), (x2, y2) = anchor(a, b)
        col = ORANGE if style == "--" else "#444444"
        cs = "arc3,rad=0"
        if len(e) > 4: cs = e[4]
        ax.annotate("", xy=(x2, y2), xytext=(x1, y1),
                    arrowprops=dict(arrowstyle="-|>", color=col, lw=1.3, linestyle="--" if style == "--" else "-",
                                    shrinkA=0, shrinkB=0, connectionstyle=cs), zorder=2)
        if label:
            ax.text((x1 + x2) / 2, (y1 + y2) / 2 + 0.1, label, fontsize=fs - 1.5, color=DGREEN,
                    ha="center", va="bottom", zorder=5,
                    bbox=dict(fc="white", ec="none", pad=0.6, alpha=0.9))
    if title:
        ax.text(0, rows * CH + 0.4, title, fontsize=fs + 2, fontweight="bold", color=DGREEN, va="center")
    if legend:
        items = [("screen", "Screen"), ("action", "User action"), ("decide", "Decision"),
                 ("offline", "Offline / local"), ("server", "Server / sync")]
        x = 0.0
        for kd, lb in items:
            k = KIND[kd]
            ax.add_patch(FancyBboxPatch((x, -0.22), 0.28, 0.16, boxstyle="round,pad=0.0,rounding_size=0.03",
                                        fc=k["fc"], ec=k["ec"], lw=1))
            ax.text(x + 0.36, -0.14, lb, fontsize=fs - 1.5, va="center")
            x += 0.5 + len(lb) * 0.075 + 0.5
    fig.savefig(os.path.join(OUT, name + ".png"), dpi=200, bbox_inches="tight", facecolor="white")
    plt.close(fig)


# ------------------------------------------------------------------ F0 sitemap
def sitemap():
    fig, ax = plt.subplots(figsize=(8.2, 6.2))
    ax.set_xlim(-0.2, 10.2); ax.set_ylim(0, 7.6); ax.axis("off")

    def box(x, y, w, h, t, kind="screen", fs=8):
        k = KIND[kind]
        ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.02,rounding_size=0.08",
                                    fc=k["fc"], ec=k["ec"], lw=1.3, zorder=3))
        ax.text(x + w / 2, y + h / 2, t, ha="center", va="center", fontsize=fs, color=k["tc"], zorder=4,
                fontweight="bold" if kind in ("start",) else "normal", linespacing=1.15)

    def line(x1, y1, x2, y2):
        ax.plot([x1, x2], [y1, y2], color="#888", lw=1, zorder=1)

    box(3.8, 6.8, 2.4, 0.6, "Launch / Splash\n(language, data-saver)", "start")
    # level 1
    l1 = [("Home\n(role-aware)", 0.0), ("Market\n(Explore)", 1.7), ("Live Map &\nFarm Insights", 3.4),
          ("Learn\n(Guides + Advisor)", 5.1), ("Help\n(Vets & Experts)", 6.8), ("Account", 8.5)]
    line(5, 6.8, 5, 6.45); line(0.75, 6.45, 9.25, 6.45)
    for t, x in l1:
        line(x + 0.75, 6.45, x + 0.75, 6.2)
        box(x, 5.5, 1.5, 0.7, t, "action", 8)
    # level 2 under each
    cols = {
        0.0: ["Weather &\nalerts", "My Records", "Finance &\nfunding", "Sell livestock"],
        1.7: ["Listing detail", "Shortlist\n(cart)", "Market Hub\n(pools)", "Price\nbenchmarks"],
        3.4: ["Market access", "Vet / service\naccess", "Water & grazing", "Disease risk"],
        5.1: ["Husbandry\nguides", "Pest & disease\nadvisor", "Report\noutbreak", "Offline packs"],
        6.8: ["Nearest expert", "Ask (async)", "Expert profile", ""],
        8.5: ["Profile & farm\npin", "Language &\ndata saver", "Sync status", "Log out / delete\ndata"],
    }
    for x, items in cols.items():
        y = 4.55
        for it in items:
            if it:
                box(x, y, 1.5, 0.72, it, "screen", 7.5)
                line(x + 0.75, y + 0.72, x + 0.75, y + 0.95)
            y -= 0.95
    # admin strip
    ax.add_patch(FancyBboxPatch((0.0, 0.05), 9.99, 0.62, boxstyle="round,pad=0.02,rounding_size=0.08",
                                fc=KIND["admin"]["fc"], ec=KIND["admin"]["ec"], lw=1.3, zorder=3))
    ax.text(5, 0.36, "Admin console (web): verify listings and experts | publish outbreak notices | upload GIS layers | edit content | analytics",
            ha="center", va="center", fontsize=6.9, zorder=4)
    fig.savefig(os.path.join(OUT, "f0_sitemap.png"), dpi=200, bbox_inches="tight", facecolor="white")
    plt.close(fig)


# ------------------------------------------------------------------ Architecture
def architecture():
    fig, ax = plt.subplots(figsize=(8.2, 7.6))
    ax.set_xlim(0, 10); ax.set_ylim(-0.7, 10.1); ax.axis("off")

    def layer(y, h, title, fc, ec):
        ax.add_patch(FancyBboxPatch((0.1, y), 9.8, h, boxstyle="round,pad=0.02,rounding_size=0.1", fc=fc, ec=ec, lw=1.4, zorder=1))
        ax.text(0.25, y + h - 0.1, title, fontsize=8.5, fontweight="bold", color=ec, va="top", zorder=2)

    def box(x, y, w, h, t, kind="screen", fs=7.0):
        k = KIND[kind]
        ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.02,rounding_size=0.07", fc=k["fc"], ec=k["ec"], lw=1.1, zorder=3))
        ax.text(x + w / 2, y + h / 2, t, ha="center", va="center", fontsize=fs, color=k["tc"], zorder=4, linespacing=1.15)

    def arr(x1, y1, x2, y2, col="#555", ls="-"):
        ax.annotate("", xy=(x2, y2), xytext=(x1, y1), arrowprops=dict(arrowstyle="-|>", color=col, lw=1.3, linestyle=ls), zorder=5)

    layer(8.75, 1.25, "1  USERS & DEVICES", "#f4faf5", GREEN)
    for i, t in enumerate(["Farmer / agripreneur\n(low-end Android, 2G/3G)", "Buyer / trader\n(browser or PWA)", "Vet / extension officer", "Admin / project team\n(desktop web)"]):
        box(0.3 + i * 2.4, 8.85, 2.2, 0.6, t, "screen", 7.0)
    layer(5.85, 2.75, "2  ON-DEVICE (OFFLINE-FIRST PWA)", "#fff7ec", ORANGE)
    box(0.3, 7.45, 9.4, 0.5, "UI: Home | Market | Map & Insights | Learn | Help   (mobile-first, data-saver mode, EN / Shona / Ndebele)", "action", 7.2)
    dev = ["App shell\n(Service Worker\ncache)", "IndexedDB:\nrecords, listings,\nKB, layers", "Offline map pack\n(PMTiles per\ndistrict)", "Sync queue\n(op-log + retry)", "Client GIS\n(Turf.js: nearest,\npoint-in-polygon)"]
    for i, t in enumerate(dev):
        box(0.3 + i * 1.92, 6.0, 1.82, 1.2, t, "offline", 6.8)
    layer(4.5, 1.15, "3  CHANNELS (data-light and non-smartphone options)", "#f3f8fd", BLUE)
    for i, t in enumerate(["HTTPS API\n(JSON, gzip)", "WhatsApp deep links\n(buy / enquire)", "SMS / USSD gateway\n(proposed)", "tel: / mailto:"]):
        box(0.3 + i * 2.4, 4.6, 2.2, 0.6, t, "server", 7.0)
    layer(1.9, 2.4, "4  BACKEND (cloud, small footprint)", "#eef6fd", BLUE)
    be = ["Auth\n(phone + OTP)", "REST / sync API", "PostgreSQL +\nPostGIS", "Object storage\n(resized images)", "Notifications\n(push + SMS)"]
    for i, t in enumerate(be):
        box(0.3 + i * 1.92, 2.95, 1.82, 0.85, t, "server", 7.0)
    box(0.3, 2.05, 9.4, 0.6, "Admin console: verify, moderate, publish outbreak notices, upload layers, content CMS, analytics", "admin", 7.2)
    layer(-0.5, 2.1, "5  GIS PROCESSING & DATA (batch, run by the team, not on the phone)", "#f6f6f6", GREY)
    gi = ["Source data\nOSM, CHIRPS,\nSentinel-2 NDVI,\nSRTM, WorldCover,\nGLW4, DVS lists", "QGIS / Python\n(GeoPandas, OSMnx,\nrasterio, pgRouting)",
          "Four analyses:\nMarket access,\nService access,\nWater & grazing,\nDisease risk", "Precomputed\noutputs: ward\npolygons, 1 km\ngrid, GeoJSON,\nvector tiles", "Packaging:\nper-district packs\n(versioned)"]
    for i, t in enumerate(gi):
        box(0.3 + i * 1.92, -0.4, 1.82, 1.3, t, "admin", 6.6)
    for x in (1.4, 3.8, 6.2, 8.6):
        arr(x, 8.85, x, 8.6)
    arr(5.0, 5.85, 5.0, 5.65, ORANGE, "--")
    arr(5.0, 4.5, 5.0, 4.3)
    arr(3.0, 1.9, 3.0, 1.6)
    arr(8.8, 1.6, 8.8, 1.9)
    fig.savefig(os.path.join(OUT, "architecture.png"), dpi=200, bbox_inches="tight", facecolor="white")
    plt.close(fig)


# ------------------------------------------------------------------ Offline sync sequence
def sync_flow():
    nodes = [
        ("a", "User saves record /\nlisting / outbreak report", 0, 0, "start"),
        ("b", "Write to IndexedDB\n(UUID, status = pending)", 1, 0, "offline"),
        ("c", "Show \"Pending\" badge\n+ confirm to user", 2, 0, "offline"),
        ("d", "Online?\n(navigator + ping)", 2, 1, "decide"),
        ("e", "Wait: retry on app open,\nBackground Sync, manual", 1, 1, "offline"),
        ("f", "Upload op-log batch\n(gzip, images last)", 3, 1, "server"),
        ("g", "Server validates +\nmerges (conflict rules)", 3, 2, "server"),
        ("h", "Conflict?", 2, 2, "decide"),
        ("i", "Auto-merge: field-level\nlast-write-wins", 1, 2, "server"),
        ("j", "Flag for user: keep mine /\nkeep server's", 2, 3, "action"),
        ("k", "Return new cursor +\nserver changes (delta)", 3, 3, "server"),
        ("l", "Badge -> \"Synced\"\nUpdate local cache", 0, 3, "end"),
    ]
    edges = [("a", "b"), ("b", "c"), ("c", "d"), ("d", "e", "No", "--"), ("e", "d", "", "--", "arc3,rad=0.45"),
             ("d", "f", "Yes"), ("f", "g"), ("g", "h"), ("h", "i", "No"), ("h", "j", "Yes"),
             ("i", "k"), ("j", "k"), ("k", "l")]
    # reposition for clearer layout
    flow("sync_flow", nodes, edges, cols=4, rows=4, title="Offline write and sync lifecycle")


def onboarding():
    nodes = [
        ("a", "Open app\n(first launch)", 0, 0, "start"),
        ("b", "Choose language\nEN / Shona / Ndebele", 1, 0, "screen"),
        ("c", "Pick district\n(3 pilot districts)\n+ optional GPS", 2, 0, "screen"),
        ("d", "Offer district\ndata pack\n(size shown, Wi-Fi hint)", 3, 0, "action"),
        ("e", "Browse as guest\n(Market, Learn, Map)", 0, 1, "screen"),
        ("f", "Wants to sell / pool /\nkeep records?", 1, 1, "decide"),
        ("g", "Enter phone number", 2, 1, "screen"),
        ("h", "OTP via SMS\n(works on 2G)", 3, 1, "server"),
        ("i", "OTP failed /\nno signal?", 3, 2, "decide"),
        ("j", "Retry, resend or\nvoice call OTP;\nprogress kept locally", 2, 2, "offline"),
        ("k", "Role + farm profile\n(livestock, ward,\nmap pin)", 1, 2, "screen"),
        ("l", "Home dashboard\n(farmer view)", 0, 2, "end"),
    ]
    edges = [("a", "b"), ("b", "c"), ("c", "d"), ("d", "e", "Skip / done", "-", "arc3,rad=0"),
             ("e", "f", ""), ("f", "g", "Yes"), ("g", "h"), ("h", "i"), ("i", "j", "Yes", "--"),
             ("j", "g", "", "--"), ("i", "k", "No"), ("k", "l")]
    # fix: i->k crosses; reorder
    flow("f1_onboarding", nodes, edges, cols=4, rows=3, title="F1  First launch and onboarding")


def buy():
    nodes = [
        ("a", "Market (Explore)\nlisting cards, cached", 0, 0, "start"),
        ("b", "Search + filter\nspecies, district,\nprice, distance", 1, 0, "action"),
        ("c", "Listing detail\nphoto, price, ward,\nbadges", 2, 0, "screen"),
        ("d", "Listing in disease-\nrestricted zone?", 3, 0, "decide"),
        ("e", "Show warning:\n\"Confirm movement\npermit with DVS\"", 3, 1, "action"),
        ("f", "Choose contact", 2, 1, "decide"),
        ("g", "Call / SMS\nfarmer", 1, 1, "action"),
        ("h", "WhatsApp message\n(pre-filled order)", 0, 1, "action"),
        ("i", "Save to shortlist\n(local, offline)", 1, 2, "offline"),
        ("j", "Deal agreed\noff-platform;\nrate seller (later)", 2, 2, "end"),
    ]
    edges = [("a", "b"), ("b", "c"), ("c", "d"), ("d", "e", "Yes"), ("d", "f", "No", "-", "arc3,rad=0"),
             ("e", "f"), ("f", "g", "Call/SMS"), ("f", "h", "WhatsApp"), ("f", "i", "Later"),
             ("g", "j", "", "-", "arc3,rad=-0.2"), ("i", "j")]
    flow("f2_buy", nodes, edges, cols=4, rows=3, title="F2  Browse and buy")


def sell():
    nodes = [
        ("a", "Sell livestock\n(logged in)", 0, 0, "start"),
        ("b", "Form: species, breed,\nsex, age, qty, price,\npayment, transport", 1, 0, "screen"),
        ("c", "Confirm location:\nfarm pin (fuzzed to\n~1 km publicly)", 2, 0, "screen"),
        ("d", "Add photo\n(resized on device\n<= 60 KB)", 3, 0, "action"),
        ("e", "Valid?", 3, 1, "decide"),
        ("f", "Inline errors,\nkeep entries", 2, 1, "action"),
        ("g", "Save locally\n(pending)", 1, 1, "offline"),
        ("h", "Sync to server", 0, 1, "server"),
        ("i", "Auto-checks +\nadmin moderation\n(new sellers)", 0, 2, "admin"),
        ("j", "Listing live on\nMarket + Map", 1, 2, "end"),
        ("k", "Offer to join a\nMarket Hub pool", 2, 2, "action"),
    ]
    edges = [("a", "b"), ("b", "c"), ("c", "d"), ("d", "e"), ("e", "f", "No"), ("f", "e", "", "--", "arc3,rad=0.4"),
             ("e", "g", "Yes"), ("g", "h"), ("h", "i"), ("i", "j"), ("j", "k")]
    flow("f3_sell", nodes, edges, cols=4, rows=3, title="F3  Sell livestock (offline-capable)")


def pool():
    nodes = [
        ("a", "Market Hub\n(pools near me)", 0, 0, "start"),
        ("b", "Pool detail:\ntarget, deadline,\nprogress, buyer", 1, 0, "screen"),
        ("c", "Eligible?\nspecies, district,\nverified", 2, 0, "decide"),
        ("d", "Explain why;\nsuggest other\npool or listing", 2, 1, "action"),
        ("e", "Commit: qty, age,\nweight, ready date", 3, 0, "screen"),
        ("f", "Queue + sync\ncommitment", 3, 1, "server"),
        ("g", "Progress bar\nupdates for all", 3, 2, "server"),
        ("h", "Target met\nby deadline?", 2, 2, "decide"),
        ("i", "Buyer offer\nposted; members\nnotified (SMS)", 1, 2, "action"),
        ("j", "Aggregation point\n+ date on map", 0, 2, "screen"),
        ("k", "Pool extended or\nlapses; members told", 2, 3, "admin"),
    ]
    edges = [("a", "b"), ("b", "c"), ("c", "d", "No"), ("c", "e", "Yes"), ("e", "f"), ("f", "g"),
             ("g", "h"), ("h", "i", "Yes"), ("h", "k", "No"), ("i", "j")]
    flow("f4_pool", nodes, edges, cols=4, rows=4, title="F4  Join a Market Hub pool")


def records():
    nodes = [
        ("a", "My Records\n(works offline)", 0, 0, "start"),
        ("b", "Add record:\nhealth, breeding, feed,\nsale, expense, loss", 1, 0, "screen"),
        ("c", "Pick animal / group;\nvoice-free quick\npickers", 2, 0, "action"),
        ("d", "Saved on device\n+ Pending badge", 3, 0, "offline"),
        ("e", "Summary: costs,\nincome, vaccination\ndue dates", 3, 1, "screen"),
        ("f", "Reminder due?\n(local notification)", 2, 1, "decide"),
        ("g", "Export CSV / PDF\nshare via WhatsApp", 1, 1, "action"),
        ("h", "Signal back ->\nbackground sync", 0, 1, "server"),
        ("i", "Badge = Synced;\nloan-ready report\navailable", 0, 2, "end"),
    ]
    edges = [("a", "b"), ("b", "c"), ("c", "d"), ("d", "e"), ("e", "f"), ("f", "g", "Open"),
             ("g", "h", "", "--"), ("h", "i")]
    flow("f5_records", nodes, edges, cols=4, rows=3, title="F5  Farm records (offline-first)")


def insights():
    nodes = [
        ("a", "Live Map &\nFarm Insights", 0, 0, "start"),
        ("b", "Offline basemap +\nfacility layers load\nfrom device pack", 1, 0, "offline"),
        ("c", "Set or confirm\nfarm pin\n(GPS or tap map)", 2, 0, "screen"),
        ("e", "Choose\nanalysis", 1.5, 1, "decide"),
        ("f", "A  Market access\nnearest markets,\ntravel time, index", 0, 2, "screen"),
        ("g", "B  Vet / service\nnearest vet, dip tank,\ngap warning", 1, 2, "screen"),
        ("h", "C  Water & grazing\nnearest water,\nsuitability class", 2, 2, "screen"),
        ("i", "D  Disease risk\nzone status,\nactive notices", 3, 2, "screen"),
        ("j", "Result card + map overlay\n(computed on device from\ncached layers)", 1.5, 3, "offline"),
        ("k", "Act: call vet, open\nlisting, set reminder,\nshare", 3, 3, "end"),
    ]
    edges = [("a", "b"), ("b", "c"), ("c", "e"), ("e", "f"), ("e", "g"), ("e", "h"), ("e", "i"),
             ("f", "j"), ("g", "j"), ("h", "j"), ("i", "j"), ("j", "k")]
    flow("f6_insights", nodes, edges, cols=4, rows=4, title="F6  Farm Insights (the four spatial analyses)")


def disease():
    nodes = [
        ("a", "Learn -> Pest &\ndisease advisor", 0, 0, "start"),
        ("b", "Search or tap\ncommon problem", 1, 0, "action"),
        ("c", "Offline answer:\nsigns, first actions,\nsources", 2, 0, "offline"),
        ("d", "Notifiable disease\n(ASF, anthrax, FMD)?", 3, 0, "decide"),
        ("e", "Show \"Call DVS / vet\nnow\" + nearest vet\ntime", 3, 1, "action"),
        ("f", "Report outbreak?\n(GPS, species,\nphoto, count)", 2, 1, "decide"),
        ("g", "Queue report\n(pending)", 1, 1, "offline"),
        ("h", "Admin verifies\nwith DVS", 0, 1, "admin"),
        ("i", "Publish notice:\nzone + buffer on map", 0, 2, "server"),
        ("j", "Geofenced push / SMS\nto farmers in zone;\nlistings flagged", 1, 2, "end"),
    ]
    edges = [("a", "b"), ("b", "c"), ("c", "d"), ("d", "e", "Yes"), ("e", "f"), ("d", "f", "No", "-", "arc3,rad=0.3"),
             ("f", "g", "Yes"), ("g", "h", "sync"), ("h", "i"), ("i", "j")]
    flow("f7_disease", nodes, edges, cols=4, rows=3, title="F7  Pest and disease advice and outbreak reporting")


def pipelines():
    fig, ax = plt.subplots(figsize=(8.2, 5.6))
    ax.set_xlim(0, 10); ax.set_ylim(0, 6.4); ax.axis("off")
    heads = ["Inputs", "Processing", "Output layer", "In the app"]
    xs = [0.1, 2.65, 5.2, 7.75]
    for h, x in zip(heads, xs):
        ax.text(x + 1.1, 6.15, h, ha="center", fontsize=9, fontweight="bold", color=DGREEN)
    rows = [
        ("1  Market accessibility", ["OSM roads + places\nmarkets, abattoirs,\npool hubs", "Road speeds by class;\nshortest travel time\n(Dijkstra); gravity index", "Isochrones 30/60/120;\nmarket access index\nper ward + 1 km grid", "Nearest 3 markets,\ntravel time, cost;\nheat map"]),
        ("2  Service accessibility", ["Vets, dip tanks, AHT,\nAGRITEX, agrovets;\nlivestock density", "Service areas by\ntravel time; 2SFCA\ncoverage ratio", "Coverage class per\nward; underserved\nwards; MCLP sites", "Nearest vet / dip\ntank; \"remote advice\"\nwhen outside 60 min"]),
        ("3  Water & grazing", ["Water points, rivers,\ndams, JRC water;\nCHIRPS, NDVI, DEM,\nWorldCover", "Distance rasters;\nreclass 1-5; AHP\nweighted overlay;\nmask exclusions", "Suitability raster\n(5 classes) + water\ndistance, dry and\nwet season", "Nearest water,\nsuitability for pin;\nherd-move hints"]),
        ("4  Disease risk", ["DVS notices, verified\nfarmer reports,\nmovement zones,\nrainfall", "Buffers by disease;\nkernel density +\nGetis-Ord Gi*; seasonal\nrainfall rule", "Restricted zones;\nhotspot surface;\nalert polygons", "Zone status for pin;\nlisting warnings;\ngeofenced alerts"]),
    ]
    y = 4.55
    for title, cells in rows:
        ax.text(0.1, y + 1.18, title, fontsize=8.3, fontweight="bold", color=EARTH)
        for i, (x, c) in enumerate(zip(xs, cells)):
            kind = ["admin", "action", "server", "offline"][i]
            k = KIND[kind]
            ax.add_patch(FancyBboxPatch((x, y), 2.2, 1.05, boxstyle="round,pad=0.02,rounding_size=0.07",
                                        fc=k["fc"], ec=k["ec"], lw=1.1, zorder=3))
            ax.text(x + 1.1, y + 0.52, c, ha="center", va="center", fontsize=6.6, zorder=4, linespacing=1.15)
            if i < 3:
                ax.annotate("", xy=(xs[i + 1] - 0.02, y + 0.52), xytext=(x + 2.22, y + 0.52),
                            arrowprops=dict(arrowstyle="-|>", color="#555", lw=1.2), zorder=5)
        y -= 1.5
    fig.savefig(os.path.join(OUT, "pipelines.png"), dpi=200, bbox_inches="tight", facecolor="white")
    plt.close(fig)


def flows_v2():
    D = "decide"
    # Sync lifecycle
    flow("sync_flow", [
        ("a", "User saves record,\nlisting or report", 0, 0, "start"),
        ("b", "Write to local DB\n(Room + SQLCipher),\nstatus=pending", 1, 0, "offline"),
        ("c", "Show Pending badge\n+ confirmation", 2, 0, "offline"),
        ("d", "Online?", 2, 1, D),
        ("e", "Wait: WorkManager\retries on signal", 1, 1, "offline"),
        ("f", "Upload op-log batch\n(gzip, images last)", 3, 1, "server"),
        ("g", "Server validates\nand merges", 3, 2, "server"),
        ("h", "Conflict?", 2, 2, D),
        ("i", "Auto-merge:\nfield last-write-wins", 1, 2, "server"),
        ("j", "User picks: keep\nmine or server's", 2, 3, "action"),
        ("k", "Return delta +\nnew sync cursor", 1, 3, "server"),
        ("l", "Badge = Synced;\ncache updated", 0, 3, "end"),
    ], [("a", "b"), ("b", "c"), ("c", "d"), ("d", "e", "No"), ("e", "b", "retry", "--"), ("d", "f", "Yes"),
        ("f", "g"), ("g", "h"), ("h", "i", "No"), ("h", "j", "Yes"), ("i", "k"), ("j", "k"), ("k", "l")],
        cols=4, rows=4, title="Offline write and sync lifecycle", fs=8)
    # F1
    flow("f1_onboarding", [
        ("a", "Open app\n(first launch)", 0, 0, "start"),
        ("b", "Choose language\nEN / Shona / Ndebele", 1, 0, "screen"),
        ("c", "Pick district\n+ optional GPS", 2, 0, "screen"),
        ("d", "Offer district data\npack (size shown)", 3, 0, "action"),
        ("f", "Needs to sell, pool\nor keep records?", 3, 1, D),
        ("g", "Enter phone\nnumber", 2, 1, "screen"),
        ("h", "OTP sent by SMS\n(works on 2G)", 1, 1, "server"),
        ("i", "OTP received?", 1, 2, D),
        ("j", "Resend / voice OTP;\nentries kept locally", 2, 2, "offline"),
        ("e", "Browse as guest:\nMarket, Learn, Map", 3, 2, "end"),
        ("k", "Role + farm profile\n(livestock, ward)", 0, 2, "screen"),
        ("l", "Farmer home\ndashboard", 0, 3, "end"),
    ], [("a", "b"), ("b", "c"), ("c", "d"), ("d", "f"), ("f", "g", "Yes"), ("f", "e", "No"), ("g", "h"), ("h", "i"),
        ("i", "k", "Yes"), ("i", "j", "No"), ("j", "g", "retry", "--"), ("k", "l")],
        cols=4, rows=4, title="F1  First launch and onboarding", fs=8)
    # F2
    flow("f2_buy", [
        ("a", "Market (Explore)\ncached listing cards", 0, 0, "start"),
        ("b", "Search + filter: species,\ndistrict, price, distance", 1, 0, "action"),
        ("c", "Listing detail: photo,\nprice, ward; zone\nwarning if restricted", 2, 0, "screen"),
        ("f", "Contact how?", 2, 1, D),
        ("g", "Call or SMS\nthe farmer", 1, 1, "end"),
        ("h", "WhatsApp opens with\npre-filled enquiry", 3, 1, "end"),
        ("i", "Save to shortlist\n(local, offline)", 2, 2, "offline"),
        ("j", "Deal agreed off-\nplatform; rate seller\n(when online)", 1, 2, "action"),
    ], [("a", "b"), ("b", "c"), ("c", "f"), ("f", "g", "Call/SMS"), ("f", "h", "WhatsApp"), ("f", "i", "Later"), ("g", "j"), ("i", "j")],
        cols=4, rows=3, title="F2  Browse and buy", fs=8)
    # F3
    flow("f3_sell", [
        ("a", "Sell livestock\n(logged in)", 0, 0, "start"),
        ("b", "Form: species, breed,\nsex, age, qty, price", 1, 0, "screen"),
        ("c", "Confirm location:\nboundary centre (public\nview blurred ~1 km)", 2, 0, "screen"),
        ("d", "Add photo (resized\non device, <=60 KB)", 3, 0, "action"),
        ("e", "Valid?", 3, 1, D),
        ("f", "Inline errors;\nentries kept", 2, 1, "action"),
        ("g", "Save locally\n(pending)", 3, 2, "offline"),
        ("h", "Sync to server", 2, 2, "server"),
        ("i", "Auto-checks + admin\nmoderation (new sellers)", 1, 2, "admin"),
        ("j", "Listing live on\nMarket + Map", 0, 2, "end"),
        ("k", "Offer to join a\nMarket Hub pool", 0, 3, "action"),
    ], [("a", "b"), ("b", "c"), ("c", "d"), ("d", "e"), ("e", "f", "No"), ("f", "c", "fix", "--"), ("e", "g", "Yes"),
        ("g", "h"), ("h", "i"), ("i", "j"), ("j", "k")],
        cols=4, rows=4, title="F3  Sell livestock (offline-capable)", fs=8)
    # F4
    flow("f4_pool", [
        ("a", "Market Hub\n(pools near me)", 0, 0, "start"),
        ("b", "Pool detail: target,\ndeadline, progress", 1, 0, "screen"),
        ("c", "Eligible?", 2, 0, D),
        ("d", "Explain why; suggest\nanother pool", 2, 1, "action"),
        ("e", "Commit: qty, age,\nweight, ready date", 3, 0, "screen"),
        ("f", "Queue + sync\ncommitment", 3, 1, "server"),
        ("g", "Progress bar\nupdates for all", 3, 2, "server"),
        ("h", "Target met?", 2, 2, D),
        ("i", "Buyer offer posted;\nmembers get SMS", 1, 2, "action"),
        ("j", "Aggregation point\n+ date on map", 0, 2, "end"),
        ("k", "Extend or lapse;\nmembers told", 2, 3, "admin"),
    ], [("a", "b"), ("b", "c"), ("c", "d", "No"), ("c", "e", "Yes"), ("e", "f"), ("f", "g"), ("g", "h"),
        ("h", "i", "Yes"), ("h", "k", "No"), ("i", "j")],
        cols=4, rows=4, title="F4  Join a Market Hub pool", fs=8)
    # F5
    flow("f5_records", [
        ("a", "My Records\n(works offline)", 0, 0, "start"),
        ("b", "Add record: health,\nbreeding, feed, sale,\nexpense, loss", 1, 0, "screen"),
        ("c", "Pick animal / group\nfrom saved list", 2, 0, "action"),
        ("d", "Saved on device;\nPending badge", 3, 0, "offline"),
        ("e", "Summary: costs, income,\nvaccination reminders", 3, 1, "screen"),
        ("f", "Export CSV / PDF;\nshare via WhatsApp", 2, 1, "action"),
        ("g", "Back online:\nbackground sync", 1, 1, "server"),
        ("h", "Badge = Synced; loan-\nready report available", 0, 1, "end"),
    ], [("a", "b"), ("b", "c"), ("c", "d"), ("d", "e"), ("e", "f"), ("f", "g", "", "--"), ("g", "h")],
        cols=4, rows=2, title="F5  Farm records (offline-first)", fs=8)
    # F6
    flow("f6_insights", [
        ("a", "Live Map &\nFarm Insights", 0, 0, "start"),
        ("b", "Basemap + layers load\nfrom device pack", 1, 0, "offline"),
        ("c", "Mark farm boundary\n(draw or shapefile)", 2, 0, "screen"),
        ("e", "Insights menu\n(choose A, B, C or D)", 1.5, 1, "screen"),
        ("f", "A  Market access:\nnearest markets,\ntravel time", 0, 2, "screen"),
        ("g", "B  Service access:\nnearest vet / dip\ntank, gaps", 1, 2, "screen"),
        ("h", "C  Water + grazing:\nnearest water,\nsuitability", 2, 2, "screen"),
        ("i", "D  Disease risk:\nzone status,\nactive notices", 3, 2, "screen"),
        ("j", "Result card + map overlay\n(computed on device)", 1.5, 3, "offline"),
        ("k", "Act: call vet, open\nlisting, set reminder", 3, 3, "end"),
    ], [("a", "b"), ("b", "c"), ("c", "e", "", "-", "arc3,rad=0", "bt"),
        ("e", "f", "", "-", "arc3,rad=0", "bt"), ("e", "g", "", "-", "arc3,rad=0", "bt"),
        ("e", "h", "", "-", "arc3,rad=0", "bt"), ("e", "i", "", "-", "arc3,rad=0", "bt"),
        ("f", "j", "", "-", "arc3,rad=0", "bt"), ("g", "j", "", "-", "arc3,rad=0", "bt"),
        ("h", "j", "", "-", "arc3,rad=0", "bt"), ("i", "j", "", "-", "arc3,rad=0", "bt"),
        ("j", "k")],
        cols=4, rows=4, title="F6  Farm Insights: the four spatial analyses", fs=8)
    # F7
    flow("f7_disease", [
        ("a", "Learn: Pest &\ndisease advisor", 0, 0, "start"),
        ("b", "Search or tap a\ncommon problem", 1, 0, "action"),
        ("c", "Offline answer: signs,\nfirst actions, sources", 2, 0, "offline"),
        ("d", "Notifiable (ASF, anthrax,\nFMD)? Red alert + nearest\nvet travel time", 3, 0, "screen"),
        ("e", "Call DVS / vet\nnow", 3, 1, "action"),
        ("f", "Report form: GPS,\nspecies, photo, count", 2, 1, "screen"),
        ("g", "Queue report\n(pending) + sync", 1, 1, "offline"),
        ("h", "Admin verifies\nwith DVS", 0, 1, "admin"),
        ("i", "Publish notice:\nzone + buffer on map", 0, 2, "server"),
        ("j", "Geofenced push / SMS;\nlistings flagged", 1, 2, "end"),
    ], [("a", "b"), ("b", "c"), ("c", "d"), ("d", "e"), ("e", "f"), ("f", "g"), ("g", "h", "sync"), ("h", "i"), ("i", "j")],
        cols=4, rows=3, title="F7  Pest and disease advice, and outbreak reporting", fs=8)


if __name__ == "__main__":
    sitemap(); pipelines(); flows_v2()
    print("done", os.listdir(OUT))
