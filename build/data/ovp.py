import requests, time, json, os, sys

EPS = ["https://overpass-api.de/api/interpreter",
       "https://overpass.private.coffee/api/interpreter",
       "https://overpass.kumi.systems/api/interpreter"]
UA = {"User-Agent": "MarketMooSchoolProject/0.1 (student GIS assignment; contact estainmakaudze@gmail.com)"}
HERE = os.path.dirname(os.path.abspath(__file__))


def op(query, cache=None, tries=8, timeout=200):
    path = os.path.join(HERE, "raw", cache) if cache else None
    if path and os.path.exists(path) and os.path.getsize(path) > 50:
        return json.load(open(path, encoding="utf8"))
    os.makedirs(os.path.join(HERE, "raw"), exist_ok=True)
    last = None
    for i in range(tries):
        ep = EPS[i % len(EPS)]
        try:
            r = requests.post(ep, data={"data": query}, headers=UA, timeout=timeout)
            if r.status_code == 200 and r.text.strip().startswith("{"):
                d = r.json()
                if path:
                    json.dump(d, open(path, "w", encoding="utf8"))
                return d
            last = (ep, r.status_code, r.text[:120])
        except Exception as e:
            last = (ep, str(e)[:120])
        print("retry", i, last, file=sys.stderr)
        time.sleep(6 + 4 * i)
    raise RuntimeError(last)
