import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** A small, dependency-free 3D driving and vehicle-dynamics sandbox. */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                JFrame frame = new JFrame("Open Road — 3D Car Simulator");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setResizable(false);
                GamePanel panel = new GamePanel();
                frame.setContentPane(panel);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                panel.requestFocusInWindow();
            }
        });
    }
}

class GamePanel extends JPanel implements ActionListener, KeyListener {
    private static final int WIDTH = 1000, HEIGHT = 700;
    private static final double DT = 1.0 / 120.0;
    private final Timer timer = new Timer(8, this);
    private final Set<Integer> keys = new HashSet<Integer>();
    private final Car car = new Car();
    private long previousNanos = System.nanoTime();
    private double accumulator;
    private double steeringVisual;

    GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setFocusable(true);
        addKeyListener(this);
        timer.start();
    }

    @Override public void actionPerformed(ActionEvent event) {
        long now = System.nanoTime();
        accumulator += Math.min(0.05, (now - previousNanos) / 1.0e9);
        previousNanos = now;
        while (accumulator >= DT) {
            double steerInput = (down(KeyEvent.VK_A, KeyEvent.VK_LEFT) ? 1 : 0)
                    - (down(KeyEvent.VK_D, KeyEvent.VK_RIGHT) ? 1 : 0);
            double throttle = down(KeyEvent.VK_W, KeyEvent.VK_UP) ? 1 : 0;
            double brake = down(KeyEvent.VK_S, KeyEvent.VK_DOWN) ? 1 : 0;
            car.update(DT, throttle, brake, down(KeyEvent.VK_SPACE), steerInput);
            steeringVisual += (car.steerAngle - steeringVisual) * Math.min(1, DT * 8);
            accumulator -= DT;
        }
        repaint();
    }

    private boolean down(int a, int b) { return keys.contains(a) || keys.contains(b); }
    private boolean down(int key) { return keys.contains(key); }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        Camera camera = new Camera(car);
        drawEnvironment(g, camera);
        drawRoad(g, camera);
        drawCar(g, camera);
        drawHud(g);
        g.dispose();
    }

    private void drawEnvironment(Graphics2D g, Camera c) {
        g.setPaint(new java.awt.GradientPaint(0, 0, new Color(58, 135, 190), 0, HEIGHT * .58f, new Color(206, 225, 228)));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setColor(new Color(105, 142, 106));
        g.fillRect(0, HEIGHT / 2, WIDTH, HEIGHT / 2);
        // Low distant hills give the flat-shaded road a useful sense of scale.
        for (int i = 0; i < 13; i++) {
            int x = i * 95 - 40;
            int y = HEIGHT / 2 - 25 - (i * 37 % 55);
            int[] xs = {x - 90, x, x + 110};
            int[] ys = {HEIGHT / 2 + 5, y, HEIGHT / 2 + 8};
            g.setColor(i % 2 == 0 ? new Color(104, 139, 131) : new Color(120, 151, 139));
            g.fillPolygon(xs, ys, 3);
        }
    }

    private void drawRoad(Graphics2D g, Camera c) {
        double start = Math.floor((car.z + 290) / 7.0) * 7.0;
        // Far-to-near painter's order. Each road slab and marking is a projected 3D polygon.
        for (double z = start; z >= car.z - 45; z -= 7.0) {
            double z2 = z - 7.0;
            double c1 = roadCenter(z), c2 = roadCenter(z2);
            Vec3 l1 = new Vec3(c1 - 7.0, 0, z), r1 = new Vec3(c1 + 7.0, 0, z);
            Vec3 l2 = new Vec3(c2 - 7.0, 0, z2), r2 = new Vec3(c2 + 7.0, 0, z2);
            fillWorld(g, c, new Vec3[] {l1, r1, r2, l2}, ((int)Math.floor(z / 7) & 1) == 0
                    ? new Color(57, 60, 62) : new Color(61, 64, 66));
            fillWorld(g, c, new Vec3[] {
                new Vec3(c1 - 7.35, .015, z), new Vec3(c1 - 7.0, .015, z),
                new Vec3(c2 - 7.0, .015, z2), new Vec3(c2 - 7.35, .015, z2)
            }, new Color(224, 218, 183));
            fillWorld(g, c, new Vec3[] {
                new Vec3(c1 + 7.0, .015, z), new Vec3(c1 + 7.35, .015, z),
                new Vec3(c2 + 7.35, .015, z2), new Vec3(c2 + 7.0, .015, z2)
            }, new Color(224, 218, 183));
            if (((int)Math.floor(z / 18) & 1) == 0) {
                for (int lane = -1; lane <= 1; lane += 2) {
                    double offset = lane * 3.5;
                    fillWorld(g, c, new Vec3[] {
                        new Vec3(c1 + offset - .055, .025, z), new Vec3(c1 + offset + .055, .025, z),
                        new Vec3(c2 + offset + .055, .025, z2), new Vec3(c2 + offset - .055, .025, z2)
                    }, new Color(220, 220, 205));
                }
            }
            // Gravel shoulder beyond both road edges.
            fillWorld(g, c, new Vec3[] {new Vec3(c1 - 11, -.03, z), l1,
                    l2, new Vec3(c2 - 11, -.03, z2)}, new Color(126, 125, 103));
            fillWorld(g, c, new Vec3[] {r1, new Vec3(c1 + 11, -.03, z),
                    new Vec3(c2 + 11, -.03, z2), r2}, new Color(126, 125, 103));
        }
        drawRoadsidePosts(g, c);
    }

    private void drawRoadsidePosts(Graphics2D g, Camera c) {
        int first = (int)Math.floor((car.z + 180) / 24.0);
        for (int i = first; i >= (int)Math.floor((car.z - 25) / 24.0); i--) {
            double z = i * 24.0, center = roadCenter(z);
            for (int side : new int[] {-1, 1}) {
                double x = center + side * 8.6;
                Vec3 base = new Vec3(x, 0, z), top = new Vec3(x, 1.1, z);
                Point2D.Double b = c.project(base), t = c.project(top);
                if (b != null && t != null && b.y > 0 && b.y < HEIGHT + 40) {
                    g.setColor(new Color(45, 50, 48));
                    g.setStroke(new BasicStroke(3));
                    g.drawLine((int)b.x, (int)b.y, (int)t.x, (int)t.y);
                    g.setColor(new Color(236, 232, 214));
                    g.fillRect((int)t.x - 3, (int)t.y - 2, 6, 4);
                }
            }
        }
    }

    private double roadCenter(double z) {
        return 8.0 * Math.sin(z * .0032) + 3.0 * Math.sin(z * .008);
    }

    private void fillWorld(Graphics2D g, Camera c, Vec3[] vertices, Color color) {
        Polygon p = projectPolygon(c, vertices);
        if (p != null) { g.setColor(color); g.fillPolygon(p); }
    }

    private Polygon projectPolygon(Camera c, Vec3[] vertices) {
        int[] xs = new int[vertices.length], ys = new int[vertices.length];
        for (int i = 0; i < vertices.length; i++) {
            Point2D.Double p = c.project(vertices[i]);
            if (p == null) return null;
            xs[i] = (int)Math.round(p.x); ys[i] = (int)Math.round(p.y);
        }
        return new Polygon(xs, ys, vertices.length);
    }

    private void drawCar(Graphics2D g, Camera c) {
        // Wheels are separate 3D box meshes; the front pair follows the steering angle.
        double[] wx = {-0.82, 0.82, -0.82, 0.82};
        double[] wz = {1.18, 1.18, -1.18, -1.18};
        for (int i = 0; i < 4; i++) {
            double steer = i < 2 ? steeringVisual : 0;
            drawBox(g, c, wx[i], .39, wz[i], .27, .42, .62, new Color(24, 27, 29), steer);
            drawBox(g, c, wx[i] + (wx[i] < 0 ? .14 : -.14), .39, wz[i], .075, .19, .28,
                    new Color(112, 119, 120), steer);
        }
        // Low-poly body, cabin and contrasting faces, all projected from actual 3D vertices.
        drawMeshFace(g, c, new double[][] {{-.96,.42,-1.7},{.96,.42,-1.7},{.90,.66,1.52},{-.90,.66,1.52}}, new Color(172, 37, 31));
        drawMeshFace(g, c, new double[][] {{-.96,.42,-1.7},{-.90,.66,1.52},{-.72,1.08,.98},{-.66,1.02,-.88}}, new Color(203, 48, 39));
        drawMeshFace(g, c, new double[][] {{.96,.42,-1.7},{.66,1.02,-.88},{.72,1.08,.98},{.90,.66,1.52}}, new Color(141, 28, 27));
        drawMeshFace(g, c, new double[][] {{-.66,1.02,-.88},{.66,1.02,-.88},{.56,1.38,.67},{-.56,1.38,.67}}, new Color(34, 54, 64));
        drawMeshFace(g, c, new double[][] {{-.56,1.38,.67},{.56,1.38,.67},{.72,1.08,.98},{-.72,1.08,.98}}, new Color(54, 82, 91));
        drawMeshFace(g, c, new double[][] {{-.66,1.02,-.88},{-.56,1.38,.67},{.56,1.38,.67},{.66,1.02,-.88}}, new Color(42, 63, 72));
        drawMeshFace(g, c, new double[][] {{-.90,.66,1.52},{-.72,1.08,.98},{.72,1.08,.98},{.90,.66,1.52}}, new Color(223, 218, 197));
        drawMeshFace(g, c, new double[][] {{-.96,.42,-1.7},{-.86,.42,-1.86},{.86,.42,-1.86},{.96,.42,-1.7}}, new Color(35, 38, 40));
        drawMeshFace(g, c, new double[][] {{-.85,.55,1.49},{-.65,.55,1.49},{-.65,.72,1.46},{-.85,.72,1.46}}, new Color(255, 242, 190));
        drawMeshFace(g, c, new double[][] {{.65,.55,1.49},{.85,.55,1.49},{.85,.72,1.46},{.65,.72,1.46}}, new Color(255, 242, 190));
        drawMeshFace(g, c, new double[][] {{-.83,.53,-1.72},{-.58,.53,-1.72},{-.58,.7,-1.68},{-.83,.7,-1.68}}, new Color(230, 34, 27));
        drawMeshFace(g, c, new double[][] {{.58,.53,-1.72},{.83,.53,-1.72},{.83,.7,-1.68},{.58,.7,-1.68}}, new Color(230, 34, 27));
    }

    private void drawMeshFace(Graphics2D g, Camera c, double[][] local, Color color) {
        Vec3[] points = new Vec3[local.length];
        for (int i = 0; i < local.length; i++) points[i] = car.localToWorld(local[i][0], local[i][1], local[i][2]);
        Polygon p = projectPolygon(c, points);
        if (p != null) { g.setColor(color); g.fillPolygon(p); g.setColor(color.darker()); g.drawPolygon(p); }
    }

    private void drawBox(Graphics2D g, Camera c, double x, double y, double z,
                         double width, double height, double length, Color color, double steer) {
        double[][] corners = {{-width/2,-height/2,-length/2},{width/2,-height/2,-length/2},
            {width/2,height/2,-length/2},{-width/2,height/2,-length/2},
            {-width/2,-height/2,length/2},{width/2,-height/2,length/2},
            {width/2,height/2,length/2},{-width/2,height/2,length/2}};
        Vec3[] v = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            double lx = corners[i][0] * Math.cos(steer) + corners[i][2] * Math.sin(steer);
            double lz = -corners[i][0] * Math.sin(steer) + corners[i][2] * Math.cos(steer);
            v[i] = car.localToWorld(x + lx, y + corners[i][1], z + lz);
        }
        int[][] faces = {{0,1,2,3},{4,7,6,5},{0,4,5,1},{3,2,6,7},{0,3,7,4},{1,5,6,2}};
        Color[] shades = {color.darker(), color, color.darker(), color.brighter(), color.darker(), color.darker()};
        for (int i = 0; i < faces.length; i++) {
            Vec3[] face = new Vec3[4];
            for (int j = 0; j < 4; j++) face[j] = v[faces[i][j]];
            fillWorld(g, c, face, shades[i]);
        }
    }

    private void drawHud(Graphics2D g) {
        g.setColor(new Color(10, 17, 22, 210));
        g.fillRoundRect(22, 20, 290, 128, 16, 16);
        g.setColor(new Color(220, 239, 239));
        g.setFont(new Font("SansSerif", Font.BOLD, 17));
        g.drawString("OPEN ROAD  /  VEHICLE TEST", 40, 48);
        g.setFont(new Font("Monospaced", Font.BOLD, 24));
        g.drawString(String.format("%3d km/h", (int)Math.round(Math.abs(car.speed()) * 3.6)), 40, 84);
        g.setFont(new Font("Monospaced", Font.PLAIN, 15));
        g.drawString("GEAR " + car.gearLabel() + "    " + String.format("%4d RPM", (int)car.rpm), 40, 113);
        g.setColor(new Color(13, 19, 23, 190));
        g.fillRoundRect(22, HEIGHT - 67, 550, 45, 12, 12);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.drawString("W / ↑ throttle   S / ↓ brake   A,D / ←,→ steer   SPACE handbrake   R reset", 37, HEIGHT - 39);
        // Small steering indicator.
        int cx = WIDTH - 90, cy = 84;
        g.setColor(new Color(15, 20, 23, 185)); g.fillOval(cx - 36, cy - 36, 72, 72);
        g.setColor(new Color(226, 232, 224)); g.setStroke(new BasicStroke(5));
        g.drawArc(cx - 25, cy - 25, 50, 50, 25, 130);
        g.drawLine(cx, cy, cx + (int)(22 * Math.sin(steeringVisual * 1.8)), cy - (int)(22 * Math.cos(steeringVisual * 1.8)));
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        String label = "STEER"; FontMetrics fm = g.getFontMetrics();
        g.drawString(label, cx - fm.stringWidth(label)/2, cy + 56);
    }

    @Override public void keyPressed(KeyEvent e) {
        keys.add(e.getKeyCode());
        if (e.getKeyCode() == KeyEvent.VK_R) car.reset();
    }
    @Override public void keyReleased(KeyEvent e) { keys.remove(e.getKeyCode()); }
    @Override public void keyTyped(KeyEvent e) { }
}

class Car {
    // SI units: metres, seconds, kilograms, newtons and radians.
    double x, z, yaw, vx, vz, yawRate;
    double steerAngle, rpm = 900;
    int gear = 1;
    double engineForce;
    private static final double MASS = 1320, IZZ = 1850, G = 9.81;
    private static final double HALF_TRACK = .79, FRONT_Z = 1.22, REAR_Z = -1.28;
    private static final double WHEEL_RADIUS = .32, MU = 1.08;
    private static final double[] RATIOS = {0, 3.25, 2.10, 1.45, 1.10, .86};
    private static final double FINAL_DRIVE = 3.7;

    void reset() { x = 0; z = 0; yaw = 0; vx = vz = yawRate = 0; steerAngle = 0; rpm = 900; gear = 1; }
    double speed() { return Math.sqrt(vx * vx + vz * vz); }
    String gearLabel() { return speed() < .25 && vz < 0 ? "R" : Integer.toString(gear); }

    Vec3 localToWorld(double lx, double ly, double lz) {
        double c = Math.cos(yaw), s = Math.sin(yaw);
        return new Vec3(x + lx*c + lz*s, ly, z - lx*s + lz*c);
    }

    void update(double dt, double throttle, double brake, boolean handbrake, double steerInput) {
        double speed = speed();
        double steerLimit = .52 / (1 + speed * .035);
        double targetSteer = -steerInput * steerLimit;
        steerAngle += (targetSteer - steerAngle) * Math.min(1, dt * (speed > 15 ? 5 : 9));

        double wheelRpm = Math.abs(vz) / (2 * Math.PI * WHEEL_RADIUS) * 60 * RATIOS[gear] * FINAL_DRIVE;
        rpm = Math.max(850, 900 + throttle * 4700 + wheelRpm * .72);
        if (rpm > 6200 && gear < RATIOS.length - 1) gear++;
        else if (rpm < 2100 && gear > 1 && throttle < .85) gear--;
        double torque = engineTorque(rpm) * throttle * RATIOS[gear] * FINAL_DRIVE * .86;
        engineForce = torque / WHEEL_RADIUS;
        double driveForce = Math.min(6200, engineForce);

        double fxTotal = 0, fzTotal = 0, torqueZ = 0;
        double forwardSpeed = Math.max(0, vz);
        // Approximate longitudinal/lateral load transfer while preserving total normal force.
        double longitudinalTransfer = Math.max(-1800, Math.min(1800, brake * 3100 - throttle * 1050));
        double lateralTransfer = Math.max(-1100, Math.min(1100, vx * Math.abs(vz) * .75));
        for (int i = 0; i < 4; i++) {
            boolean front = i < 2;
            boolean left = (i % 2) == 0;
            double wx = left ? -HALF_TRACK : HALF_TRACK;
            double wz = front ? FRONT_Z : REAR_Z;
            double load = MASS * G / 4 + (front ? longitudinalTransfer : -longitudinalTransfer) / 2
                    + (left ? -lateralTransfer : lateralTransfer) / 2;
            load = Math.max(350, load);
            double steer = front ? steerAngle : 0;
            double contactVx = vx - yawRate * wz;
            double contactVz = vz + yawRate * wx;
            double c = Math.cos(steer), s = Math.sin(steer);
            double tireLongSpeed = contactVx * s + contactVz * c;
            double tireSideSpeed = contactVx * c - contactVz * s;
            double slipAngle = Math.atan2(tireSideSpeed, Math.abs(tireLongSpeed) + 1.2);
            double tireFy = -62000 * slipAngle;
            double normalGrip = MU * load;
            tireFy = clamp(tireFy, -normalGrip, normalGrip);

            double tireFx = (!front ? driveForce / 2 : 0);
            if (tireLongSpeed < -.2 && !front) tireFx = -driveForce / 2;
            double wheelBrake = brake * 5200 / 4 + (handbrake && !front ? 3300 : 0);
            if (Math.abs(tireLongSpeed) > .12) tireFx -= Math.signum(tireLongSpeed) * wheelBrake;
            else if (brake > 0 || handbrake) tireFx = 0;
            tireFx -= Math.signum(tireLongSpeed) * Math.min(90, Math.abs(tireLongSpeed) * 16);
            double combined = Math.sqrt(tireFx*tireFx + tireFy*tireFy);
            if (combined > normalGrip) { double scale = normalGrip / combined; tireFx *= scale; tireFy *= scale; }

            double bodyFx = tireFx * s + tireFy * c;
            double bodyFz = tireFx * c - tireFy * s;
            fxTotal += bodyFx; fzTotal += bodyFz;
            torqueZ += wx * bodyFz - wz * bodyFx;
        }
        // Aerodynamic drag, rolling resistance, and a mild off-road penalty.
        double drag = .5 * 1.225 * .72 * speed * speed;
        fxTotal -= vx * 38;
        fzTotal -= Math.signum(vz) * (drag + 125);
        double roadOffset = Math.abs(x - (8 * Math.sin(z * .0032) + 3 * Math.sin(z * .008)));
        if (roadOffset > 7) {
            double excess = Math.min(1, (roadOffset - 7) / 5);
            fxTotal -= vx * (220 + 400 * excess);
            fzTotal -= vz * (220 + 500 * excess);
            torqueZ -= yawRate * (260 + 300 * excess);
        }
        // Body-frame equations include the yaw-rate coupling terms.
        double ax = fxTotal / MASS + yawRate * vz;
        double az = fzTotal / MASS - yawRate * vx;
        vx += ax * dt; vz += az * dt;
        yawRate += torqueZ / IZZ * dt;
        yawRate *= 1.0 - Math.min(.25, dt * (Math.abs(vz) < .5 ? 2.5 : .10));
        yaw += yawRate * dt;
        double cs = Math.cos(yaw), sn = Math.sin(yaw);
        x += (vx * cs + vz * sn) * dt;
        z += (-vx * sn + vz * cs) * dt;
        if (Math.abs(vx) < .015) vx = 0;
        if (Math.abs(vz) < .015) vz = 0;
    }

    private double engineTorque(double r) {
        // Broad naturally aspirated torque curve with a soft limiter.
        double curve = 245 + 75 * Math.exp(-Math.pow((r - 3600) / 1900, 2)) - Math.max(0, r - 5700) * .09;
        return clamp(curve, 90, 315);
    }
    private double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}

class Camera {
    private final double x, y, z, yaw, pitch;
    private static final double FOCAL = 680;
    Camera(Car car) {
        yaw = car.yaw;
        pitch = Math.toRadians(9.5);
        x = car.x - Math.sin(yaw) * 8.2;
        z = car.z - Math.cos(yaw) * 8.2;
        y = 3.0;
    }
    Point2D.Double project(Vec3 p) {
        double dx = p.x - x, dy = p.y - y, dz = p.z - z;
        double side = dx * Math.cos(yaw) - dz * Math.sin(yaw);
        double forward = dx * Math.sin(yaw) + dz * Math.cos(yaw);
        double depth = forward * Math.cos(pitch) + dy * Math.sin(pitch);
        double up = dy * Math.cos(pitch) - forward * Math.sin(pitch);
        if (depth < .45) return null;
        return new Point2D.Double(500 + FOCAL * side / depth, 355 - FOCAL * up / depth);
    }
}

class Vec3 {
    final double x, y, z;
    Vec3(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
}
