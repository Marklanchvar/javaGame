# Open Road — 3D Car Simulator

A dependency-free Java Swing driving sandbox with a perspective 3D view and a simplified four-wheel rigid-body vehicle model. The scene and low-poly car are projected from 3D coordinates and drawn with Java AWT `drawPolygon` / `fillPolygon` APIs.

## Run

Requires Java 8 or newer:

```sh
javac src/Main.java
java -cp src Main
```

## Controls

- **W / Up:** throttle
- **S / Down:** brake
- **A,D / Left,Right:** steer
- **Space:** handbrake (rear wheels)
- **R:** reset car

The model includes individual tire forces and slip angles, a friction limit, engine gearing and torque curve, braking, load-transfer approximations, yaw inertia, aerodynamic drag, rolling resistance, and reduced grip off the road. It is a real-time game/sandbox model, not a validated engineering or safety simulator.
