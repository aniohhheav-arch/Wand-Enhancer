// First-person controller: walking, jumping, swimming, gravity zones (incl. inverted gravity), collision against
// interior regions / world colliders / the TARDIS body, plus input handling.
import { THREE, clamp, damp, bus } from './util.js';
import { G } from './state.js';

export class Player {
  constructor(camera, canvas) {
    this.camera = camera; this.canvas = canvas;
    this.pos = new THREE.Vector3(); this.vel = new THREE.Vector3(); this.yaw = 0; this.pitch = 0;
    this.onGround = true; this.inside = true; this.up = 1; this.upVis = 1; this.swimming = false; this.eye = 1.7; this.bob = 0; this.moving = 0;
    this.keys = {}; this.locked = false; this.frozen = false; this.pressed = new Set(); this.mouseDown = [false, false, false]; this.clicks = [];
    this.lookDelta = { x: 0, y: 0 }; this.flipLock = 0; this.stepSound = 0;
    window.addEventListener('keydown', (e) => { if (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT' || e.target.tagName === 'TEXTAREA') return; if (!e.repeat) this.pressed.add(e.code); this.keys[e.code] = true; if (['Space', 'Tab', 'ArrowUp', 'ArrowDown'].includes(e.code)) e.preventDefault(); });
    window.addEventListener('keyup', (e) => { this.keys[e.code] = false; });
    window.addEventListener('blur', () => { this.keys = {}; });
    document.addEventListener('pointerlockchange', () => { this.locked = document.pointerLockElement === canvas; bus.emit('pointerlock', this.locked); });
    document.addEventListener('mousemove', (e) => { if (this.locked) this.look(e.movementX, e.movementY); else if (this.dragging) { this.look(e.movementX, e.movementY); } });
    canvas.addEventListener('mousedown', (e) => { this.mouseDown[e.button] = true; if (!this.locked) { this.dragging = true; } this.clicks.push(e.button); });
    window.addEventListener('mouseup', (e) => { this.mouseDown[e.button] = false; this.dragging = false; });
    canvas.addEventListener('contextmenu', (e) => e.preventDefault());
  }
  look(dx, dy) {
    if (this.frozen) return; const s = 0.0022 * G.settings.sens; this.cinematicCancel && this.cinematicCancel();
    this.yaw -= dx * s * this.up; this.pitch -= dy * s * this.up * (G.settings.invertY ? -1 : 1); this.pitch = clamp(this.pitch, -1.5, 1.5);
  }
  requestLock() { try { const p = this.canvas.requestPointerLock(); if (p && p.catch) p.catch(() => {}); } catch { /* ignore */ } }
  wasPressed(code) { return this.pressed.has(code); }
  endFrame() { this.pressed.clear(); this.clicks.length = 0; }

  setPose(pos, yaw, pitch = 0) { this.pos.copy(pos); this.yaw = yaw; this.pitch = pitch; this.vel.set(0, 0, 0); }

  // apply a rigid transform (portal crossing); keeps camera direction continuous
  applyMatrix(M) {
    this.pos.applyMatrix4(M);
    const phi = Math.atan2(M.elements[8], M.elements[0]); this.yaw += phi; this.vel.applyMatrix4(new THREE.Matrix4().extractRotation(M));
  }

  dirs() {
    const f = new THREE.Vector3(-Math.sin(this.yaw), 0, -Math.cos(this.yaw)), r = new THREE.Vector3(Math.cos(this.yaw), 0, -Math.sin(this.yaw)).multiplyScalar(this.up);
    return { f, r };
  }

  // area: { groundY(x,z,y), ok(x,z,y), gravity, swimY (water surface or null), floorY, flip: {ceilY,pads}, up }
  update(dt, area, canMove = true) {
    const k = this.keys, { f, r } = this.dirs();
    let ix = 0, iz = 0;
    if (canMove && !this.frozen) { if (k.KeyW || k.ArrowUp) iz += 1; if (k.KeyS || k.ArrowDown) iz -= 1; if (k.KeyD || k.ArrowRight) ix += 1; if (k.KeyA || k.ArrowLeft) ix -= 1; }
    const sprint = k.ShiftLeft || k.ShiftRight; const base = this.inside ? 4.2 : 3.6; const speed = base * (sprint ? 1.9 : 1) * (this.swimming ? 0.55 : 1) * (area.slow || 1);
    const wish = f.clone().multiplyScalar(iz).add(r.clone().multiplyScalar(ix)); if (wish.lengthSq() > 1) wish.normalize();
    this.vel.x = damp(this.vel.x, wish.x * speed, this.onGround || this.swimming ? 14 : 3, dt); this.vel.z = damp(this.vel.z, wish.z * speed, this.onGround || this.swimming ? 14 : 3, dt);
    this.moving = Math.hypot(this.vel.x, this.vel.z) / 4;
    // inverted gravity pads (mirror dimension)
    if (area.flip && this.onGround) {
      this.flipLock -= dt;
      for (const p of area.flip.pads) if (Math.hypot(this.pos.x - p.x, this.pos.z - p.z) < p.r && this.flipLock <= 0 && this.up === 1) { this.up = -1; this.flipLock = 1.5; this.onGround = false; this.vel.y = 2; bus.emit('flip', -1); }
      for (const p of area.flip.pads) if (Math.hypot(this.pos.x - p.x, this.pos.z - p.z) < p.r && this.flipLock <= 0 && this.up === -1) { this.up = 1; this.flipLock = 1.5; this.onGround = false; this.vel.y = -2; bus.emit('flip', 1); }
    }
    if (!area.flip) this.up = 1;
    this.upVis = damp(this.upVis, this.up, 5, dt);

    // horizontal move with sliding collision
    const p = this.pos, nx = p.x + this.vel.x * dt, nz = p.z + this.vel.z * dt;
    const can = (x, z) => area.ok(x, z, p.y, this.onGround);
    if (can(nx, nz)) { p.x = nx; p.z = nz; } else if (can(nx, p.z)) { p.x = nx; this.vel.z = 0; } else if (can(p.x, nz)) { p.z = nz; this.vel.x = 0; } else { this.vel.x = 0; this.vel.z = 0; }

    // vertical
    const g = 9.8 * (area.gravity ?? 1);
    this.swimming = !!(area.waterY != null && p.y + 1.2 < area.waterY);
    const gy = this.up === 1 ? area.groundY(p.x, p.z, p.y) : area.ceilY;
    if (this.swimming) {
      this.vel.y = damp(this.vel.y, ((k.Space ? 3 : 0) - (k.ControlLeft || k.KeyC ? 3 : 0)) * 1 + -0.4, 3, dt); p.y += this.vel.y * dt; if (p.y < gy) { p.y = gy; this.vel.y = 0; } this.onGround = false;
    } else if (this.up === 1) {
      if (this.onGround) {
        if (gy < p.y - 1.25 && !area.canDrop) { /* step-down is prevented by ok() */ }
        if (gy < p.y - 0.01) { this.onGround = false; } else p.y = Math.max(gy, p.y);
        if (this.onGround && k.Space && canMove && !this.frozen) { this.vel.y = 4.2 * Math.sqrt(Math.max(0.3, area.gravity ?? 1)) + 0.6; this.onGround = false; }
      }
      if (!this.onGround) {
        this.vel.y -= g * dt; p.y += this.vel.y * dt;
        if (p.y <= gy && this.vel.y <= 0) { p.y = gy; this.vel.y = 0; this.onGround = true; if (-this.vel.y > 4) bus.emit('land'); }
      }
      if (area.waterY != null && p.y <= area.waterY - 1.0 && !this.swimming) this.swimming = true;
    } else { // inverted: stand on the ceiling plane
      this.vel.y += g * dt; p.y += this.vel.y * dt; if (p.y >= gy) { p.y = gy; this.vel.y = 0; this.onGround = true; } else this.onGround = false;
      if (this.onGround && k.Space && canMove) { this.vel.y = -4.2; this.onGround = false; }
    }
    if (this.onGround && this.moving > 0.2) { this.bob += dt * (sprint ? 11 : 7.5) * this.moving; this.stepSound += dt * this.moving * (sprint ? 2.2 : 1.6); if (this.stepSound > 1) { this.stepSound = 0; bus.emit('step'); } } else this.bob *= 0.9;
    if (!isFinite(p.x + p.y + p.z)) bus.emit('stuck');
  }

  applyCamera(shake = 0, fov = 75) {
    const c = this.camera, bobY = Math.sin(this.bob) * 0.035 * (this.moving > 0.1 ? 1 : 0);
    c.position.set(this.pos.x, this.pos.y + this.upVis * this.eye + bobY, this.pos.z);
    if (shake > 0) c.position.add(new THREE.Vector3((Math.random() - 0.5) * shake, (Math.random() - 0.5) * shake, (Math.random() - 0.5) * shake));
    c.rotation.order = 'YXZ'; c.rotation.set(this.pitch * (this.up >= 0 ? 1 : 1), this.yaw, Math.PI * (1 - this.upVis) / 2);
    if (Math.abs(c.fov - fov) > 0.01) { c.fov = fov; c.updateProjectionMatrix(); }
    c.updateMatrixWorld(true);
  }
}
