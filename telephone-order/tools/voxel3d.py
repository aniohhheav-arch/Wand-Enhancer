"""Turns a 32x32 sprite into a true 3D element model: every opaque pixel becomes part of a cuboid whose thickness grows towards the
middle of the silhouette (rounded 'pillow' depth), greedy-merged by depth level; the sprite is used as the texture."""
W = 32
PX = 0.5          # model units per pixel (32px -> 16 units)
STEP = 0.85

def levels(img):
    px = img.load()
    solid = [[px[x, y][3] >= 128 for x in range(W)] for y in range(W)]
    dist = [[0] * W for _ in range(W)]
    frontier = []
    for y in range(W):
        for x in range(W):
            if solid[y][x]:
                edge = False
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if not (0 <= nx < W and 0 <= ny < W) or not solid[ny][nx]:
                        edge = True
                if edge:
                    dist[y][x] = 1; frontier.append((x, y))
    while frontier:
        nxt = []
        for x, y in frontier:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < W and 0 <= ny < W and solid[ny][nx] and dist[ny][nx] == 0:
                    dist[ny][nx] = dist[y][x] + 1; nxt.append((nx, ny))
        frontier = nxt
    lv = [[0] * W for _ in range(W)]
    for y in range(W):
        for x in range(W):
            if solid[y][x]:
                lv[y][x] = 1 + min(max(dist[y][x] - 1, 0), 3)
    return lv

def rects(lv, level):
    used = [[False] * W for _ in range(W)]
    out = []
    for y in range(W):
        for x in range(W):
            if lv[y][x] == level and not used[y][x]:
                w = 1
                while x + w < W and lv[y][x + w] == level and not used[y][x + w]: w += 1
                h = 1
                while y + h < W and all(lv[y + h][x + i] == level and not used[y + h][x + i] for i in range(w)): h += 1
                for j in range(h):
                    for i in range(w): used[y + j][x + i] = True
                out.append((x, y, w, h))
    return out

def elements(img):
    lv = levels(img)
    els = []
    def cov(x0, y0, x1, y1, level):
        for yy in range(y0, y1):
            for xx in range(x0, x1):
                if not (0 <= xx < W and 0 <= yy < W) or lv[yy][xx] < level: return False
        return True
    for level in (1, 2, 3, 4):
        d = 1.0 + (level - 1) * STEP
        for (x, y, w, h) in rects(lv, level):
            u1, u2 = x * PX, (x + w) * PX
            v1, v2 = y * PX, (y + h) * PX
            f = {"north": {"uv": [u2, v1, u1, v2], "texture": "#t"}, "south": {"uv": [u1, v1, u2, v2], "texture": "#t"}}
            if not cov(x, y - 1, x + w, y, level): f["up"] = {"uv": [u1, v1, u2, v1 + PX], "texture": "#t"}
            if not cov(x, y + h, x + w, y + h + 1, level): f["down"] = {"uv": [u1, v2 - PX, u2, v2], "texture": "#t"}
            if not cov(x + w, y, x + w + 1, y + h, level): f["east"] = {"uv": [u2 - PX, v1, u2, v2], "texture": "#t"}
            if not cov(x - 1, y, x, y + h, level): f["west"] = {"uv": [u1, v1, u1 + PX, v2], "texture": "#t"}
            els.append({"from": [round(x * PX, 3), round(16 - (y + h) * PX, 3), round(8 - d / 2, 3)],
                        "to": [round((x + w) * PX, 3), round(16 - y * PX, 3), round(8 + d / 2, 3)], "faces": f})
    return els
