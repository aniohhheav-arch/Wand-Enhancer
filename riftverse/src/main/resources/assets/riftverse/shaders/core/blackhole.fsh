#version 150

#moj_import <riftverse:rv_common.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform mat4 InvViewProj;
uniform mat4 ViewProj;
uniform float RvTime;
uniform vec3 Center;
uniform vec3 DiskNormal;
uniform vec3 ColorHot;
uniform vec3 ColorCool;
// x horizon radius, y bloom 0..1, z style, w disk brightness
uniform vec4 HoleParams;

in vec2 texCoord;
out vec4 fragColor;

const int STEPS = 96;
const float INFLUENCE = 13.0;

vec3 diskColor(vec3 hitN, float r, vec3 rayDir, float rIn, float rOut, out float alpha) {
    vec3 n = normalize(DiskNormal);
    vec3 u = normalize(cross(abs(n.y) > 0.9 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0), n));
    vec3 v = cross(n, u);
    float phi = atan(dot(hitN, v), dot(hitN, u));
    float omega = 1.6 / pow(r, 1.5);
    float a = phi - RvTime * omega;
    float logR = log(r);
    float turb = rvFbm3(vec3(cos(a) * 0.95, sin(a) * 0.95, logR * 7.0), 5);
    float streaks = rvFbm3(vec3(cos(a) * 3.8, sin(a) * 3.8, logR * 2.0 + 3.0), 3);
    float t = clamp(pow(rIn / r, 0.75), 0.0, 1.0);
    float edgeIn = smoothstep(rIn, rIn * 1.25, r);
    float edgeOut = 1.0 - smoothstep(rOut * 0.6, rOut, r);
    vec3 orbit = normalize(cross(n, hitN));
    float beta = 0.55 / sqrt(r);
    float doppler = clamp(1.0 / (1.0 - beta * dot(orbit, -rayDir)), 0.35, 2.2);
    float boost = pow(doppler, 2.5);
    vec3 col = mix(ColorCool, ColorHot, t * t) * (0.5 + turb * 1.0) * (0.7 + streaks * 0.6);
    col += vec3(1.0, 0.95, 0.9) * pow(t, 4.0) * 0.6;
    col = mix(col, col * vec3(0.75, 0.85, 1.25), clamp(doppler - 1.0, 0.0, 1.0));
    alpha = clamp(edgeIn * edgeOut * (0.55 + turb * 0.6), 0.0, 1.0);
    return col * boost * HoleParams.w * edgeIn * edgeOut;
}

void main() {
    float rs = HoleParams.x * max(HoleParams.y, 0.05);
    vec3 rd = rvRayDir(InvViewProj, texCoord);
    vec3 rel = -Center / rs;
    float b = dot(rel, rd);
    float c = dot(rel, rel) - INFLUENCE * INFLUENCE;
    float disc = b * b - c;
    vec4 sceneSample = texture(Sampler0, texCoord);
    if (disc < 0.0) {
        discard;
    }
    float tEnter = max(0.0, -b - sqrt(disc));
    float depth = texture(Sampler1, texCoord).r;
    float sceneDist = length(rvWorldPos(InvViewProj, texCoord, depth)) / rs;
    if (depth < 1.0 && sceneDist < tEnter) {
        discard;
    }

    int style = int(HoleParams.z + 0.5);
    vec3 pos = rel + rd * tEnter;
    vec3 vel = rd;
    vec3 h = cross(pos, vel);
    float h2 = dot(h, h);
    vec3 n = normalize(DiskNormal);
    float rIn = 2.6;
    float rOut = style == 2 ? 3.2 : 9.5;

    vec3 accum = vec3(0.0);
    float accumA = 0.0;
    float glow = 0.0;
    bool captured = false;
    float minR = 1e9;
    for (int i = 0; i < STEPS; i++) {
        float r = length(pos);
        minR = min(minR, r);
        if (r < 1.0) {
            captured = true;
            break;
        }
        if (r > INFLUENCE * 1.02 && dot(pos, vel) > 0.0) break;
        float dt = clamp(0.06 * r, 0.03, 0.9);
        vec3 acc = -1.5 * h2 * pos / pow(r, 5.0);
        vec3 nvel = vel + acc * dt;
        vec3 npos = pos + nvel * dt;
        float s0 = dot(pos, n);
        float s1 = dot(npos, n);
        if (s0 * s1 < 0.0 && style != 2 && accumA < 0.99) {
            vec3 hit = mix(pos, npos, s0 / (s0 - s1));
            float hr = length(hit);
            if (hr > rIn && hr < rOut) {
                float a;
                vec3 dc = diskColor(hit / hr, hr, normalize(nvel), rIn, rOut, a);
                accum += (1.0 - accumA) * dc * a;
                accumA += (1.0 - accumA) * a;
            }
        }
        glow += exp(-abs(r - 1.5) * 6.0) * dt * 0.12;
        pos = npos;
        vel = normalize(nvel);
    }

    vec3 background;
    if (captured) {
        background = vec3(0.0);
    } else {
        vec3 farPoint = Center + (pos + vel * 400.0) * rs;
        vec3 proj = rvProject(ViewProj, farPoint);
        if (proj.z > 0.0 && proj.x > 0.0 && proj.x < 1.0 && proj.y > 0.0 && proj.y < 1.0) {
            background = texture(Sampler0, proj.xy).rgb;
        } else {
            vec2 clamped = clamp(proj.z > 0.0 ? proj.xy : vec2(1.0) - proj.xy, 0.002, 0.998);
            background = texture(Sampler0, clamped).rgb * 0.55;
            background += vec3(rvStars(vel, 160.0, 0.12, RvTime)) * 0.8;
        }
        float bend = clamp(1.0 - (minR - 1.0) / (INFLUENCE - 1.0), 0.0, 1.0);
        background *= 1.0 - smoothstep(0.75, 1.0, bend) * 0.25;
    }

    vec3 ringColor = style == 1 ? vec3(1.0, 0.85, 0.55) : style == 2 ? vec3(0.45, 0.85, 1.0) : style == 3 ? vec3(1.0, 0.75, 0.35) : vec3(1.0, 0.9, 0.8);
    vec3 light = accum * 0.6 + ringColor * glow * (style == 2 ? 2.5 : 1.4);
    light = vec3(1.0) - exp(-light * 1.25);
    vec3 col = background * (1.0 - accumA) + light;

    if (style == 2) {
        float ripple = sin(minR * 6.0 - RvTime * 8.0) * exp(-minR * 0.35);
        col += vec3(0.3, 0.75, 1.0) * max(ripple, 0.0) * 0.35;
    }

    float edgeFade = smoothstep(INFLUENCE, INFLUENCE * 0.82, minR);
    vec3 outCol = mix(sceneSample.rgb, col, edgeFade);
    fragColor = vec4(outCol, 1.0);
}
