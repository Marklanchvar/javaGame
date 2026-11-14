# Vehicle Dynamics Sandbox

A dependency-free Java Swing sandbox for testing a simplified four-wheel rigid-body car model. The open proving ground includes a marked distance grid, acceleration/braking straight, slalom cones, two skidpads, and wide parallel surface lanes. Ground geometry, the low-poly car and test fixtures use perspective projection and Java polygon drawing.

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
- **R:** reset vehicle and telemetry

## Test areas

Drive laterally across the labeled lanes to compare **gravel**, **wet asphalt**, **dry asphalt**, **ice**, and **dirt**. Each surface has distinct tire grip and rolling resistance. Use the centerline and distance boards for acceleration and braking runs, the cone slalom for transient handling, and the paired skidpads to explore steady-state cornering and oversteer.

The HUD reports speed, gear, engine RPM, surface grip, tire slip, yaw rate, lateral acceleration, and distance from the start. Physics are intended for interactive experimentation, not validated engineering or safety analysis.
