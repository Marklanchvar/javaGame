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
import java.util.HashSet;
import java.util.Set;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** A dependency-free, polygon-rendered vehicle dynamics proving ground. */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                JFrame frame = new JFrame("Vehicle Dynamics Sandbox");
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
    private double accumulator, steeringVisual;

    GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setFocusable(true);
        addKeyListener(this);
        timer.start();
    }

    @Override public void actionPerformed(ActionEvent event) {
        long now = System.nanoTime();
        accumulator += Math.min(.05, (now - previousNanos) / 1.0e9);
        previousNanos = now;
        while (accumulator >= DT) {
            double steer = (down(KeyEvent.VK_A, KeyEvent.VK_LEFT) ? 1 : 0)
                    - (down(KeyEvent.VK_D, KeyEvent.VK_RIGHT) ? 1 : 0);
            car.update(DT, down(KeyEvent.VK_W, KeyEvent.VK_UP) ? 1 : 0,
                    down(KeyEvent.VK_S, KeyEvent.VK_DOWN) ? 1 : 0,
                    down(KeyEvent.VK_SPACE), steer);
            steeringVisual += (car.steerAngle - steeringVisual) * Math.min(1, DT * 8);
            accumulator -= DT;
        }
        repaint();
    }

    private boolean down(int a, int b) { return keys.contains(a) || keys.contains(b); }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        Camera camera = new Camera(car);
        drawSky(g);
        drawSandbox(g, camera);
        drawTestFixtures(g, camera);
        drawCar(g, camera);
        drawHud(g);
        g.dispose();
    }

    private void drawSky(Graphics2D g) {
        g.setPaint(new java.awt.GradientPaint(0, 0, new Color(67, 144, 191), 0, 410, new Color(208, 225, 227)));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setColor(new Color(147, 174, 170));
        int[] xs = {-40, 100, 220, 370, 520, 670, 830, 1040};
        int[] ys = {405, 350, 384, 333, 390, 345, 378, 350};
        g.fillPolygon(xs, ys, xs.length);
    }

    /** Broad, open test pad with a grid and side-by-side surface comparison lanes. */
    private void drawSandbox(Graphics2D g, Camera c) {
        double near = Math.floor((car.z - 35) / 10) * 10;
        double far = near + 300;
        fillWorld(g, c, new Vec3[] {new Vec3(-180, 0, near), new Vec3(180, 0, near),
                new Vec3(180, 0, far), new Vec3(-180, 0, far)}, new Color(76, 80, 78));

        // The colored strips are real surface regions: driving onto them changes tire grip.
        drawSurfaceLane(g, c, -34, -20, new Color(125, 121, 101), "GRAVEL");
        drawSurfaceLane(g, c, -20, -7, new Color(72, 91, 94), "WET");
        drawSurfaceLane(g, c, -7, 7, new Color(77, 80, 80), "ASPHALT");
        drawSurfaceLane(g, c, 7, 20, new Color(151, 155, 158), "ICE");
        drawSurfaceLane(g, c, 20, 34, new Color(117, 105, 83), "DIRT");

        // Fine 10 m grid makes distance, yaw drift and lateral acceleration easy to judge.
        int x0 = (int)Math.floor((car.x - 100) / 10) * 10;
        int x1 = x0 + 200;
        for (int x = x0; x <= x1; x += 10) {
            fillWorld(g, c, new Vec3[] {new Vec3(x, .012, near), new Vec3(x + .035, .012, near),
                    new Vec3(x + .035, .012, far), new Vec3(x, .012, far)},
                    x % 50 == 0 ? new Color(182, 183, 166) : new Color(111, 114, 108));
        }
        int z0 = (int)Math.floor(near / 10) * 10;
        for (int z = z0; z <= far; z += 10) {
            fillWorld(g, c, new Vec3[] {new Vec3(-100, .014, z), new Vec3(100, .014, z),
                    new Vec3(100, .014, z + .035), new Vec3(-100, .014, z + .035)},
                    z % 50 == 0 ? new Color(182, 183, 166) : new Color(111, 114, 108));
            if (z % 50 == 0) drawDistanceMarker(g, c, z);
        }
        // Painted centerline for straight-line acceleration and braking runs.
        for (int z = z0; z < far; z += 12) {
            fillWorld(g, c, new Vec3[] {new Vec3(-.09,.025,z), new Vec3(.09,.025,z),
                    new Vec3(.09,.025,z+6), new Vec3(-.09,.025,z+6)}, new Color(238, 224, 169));
        }
    }

    private void drawSurfaceLane(Graphics2D g, Camera c, double x1, double x2, Color color, String label) {
        double near = Math.floor((car.z - 35) / 10) * 10, far = near + 300;
        fillWorld(g, c, new Vec3[] {new Vec3(x1, .005, near), new Vec3(x2, .005, near),
                new Vec3(x2, .005, far), new Vec3(x1, .005, far)}, color);
        // Longitudinal boundary stripes.
        for (double x : new double[] {x1, x2}) {
            fillWorld(g, c, new Vec3[] {new Vec3(x-.08,.02,near), new Vec3(x+.08,.02,near),
                    new Vec3(x+.08,.02,far), new Vec3(x-.08,.02,far)}, new Color(225, 222, 194));
        }
        Point2D.Double p = c.project(new Vec3((x1+x2)/2, .1, car.z + 48));
        if (p != null && p.x > 0 && p.x < WIDTH && p.y > 0 && p.y < HEIGHT) {
            g.setColor(new Color(238, 237, 220));
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.drawString(label, (int)p.x - 28, (int)p.y);
        }
    }

    private void drawDistanceMarker(Graphics2D g, Camera c, double z) {
        for (int side : new int[] {-1, 1}) {
            double x = side * 36;
            fillWorld(g, c, new Vec3[] {new Vec3(x-.13,.02,z), new Vec3(x+.13,.02,z),
                    new Vec3(x+.13,.02,z+2), new Vec3(x-.13,.02,z+2)}, new Color(242, 235, 204));
        }
        Point2D.Double p = c.project(new Vec3(-37, .1, z));
        if (p != null && p.x > 0 && p.x < WIDTH && p.y > 0 && p.y < HEIGHT) {
            g.setColor(new Color(240, 240, 225));
            g.setFont(new Font("Monospaced", Font.BOLD, 12));
            g.drawString(Integer.toString((int)z) + " m", (int)p.x - 35, (int)p.y - 5);
        }
    }

    /** Skidpad rings, slalom cones, and brake-test distance boards. */
    private void drawTestFixtures(Graphics2D g, Camera c) {
        drawSkidpad(g, c, -49, 42, 16);
        drawSkidpad(g, c, 49, 42, 16);
        // Repeating cone slalom down the center-right of the proving ground.
        for (int i = 0; i < 10; i++) {
            double z = 26 + i * 11;
            double x = (i % 2 == 0 ? -1 : 1) * 5.2;
            drawCone(g, c, x, z);
        }
        // Brake/acceleration lane markers and start line.
        for (int z = 28; z <= 238; z += 20) {
            for (int i = -3; i <= 3; i++) drawCone(g, c, i * 1.45, z);
        }
        for (int x = -6; x <= 6; x++) {
            fillWorld(g, c, new Vec3[] {new Vec3(x-.12,.04,18), new Vec3(x+.12,.04,18),
                    new Vec3(x+.12,.04,19), new Vec3(x-.12,.04,19)}, Color.WHITE);
        }
        drawBoard(g, c, "SKIDPAD", -49, 64);
        drawBoard(g, c, "SLALOM", 13, 30);
        drawBoard(g, c, "BRAKE TEST", 13, 54);
    }

    private void drawSkidpad(Graphics2D g, Camera c, double cx, double cz, double radius) {
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI * 2 / 64, b = (i+1) * Math.PI * 2 / 64;
            double x1 = cx + radius * Math.cos(a), z1 = cz + radius * Math.sin(a);
            double x2 = cx + radius * Math.cos(b), z2 = cz + radius * Math.sin(b);
            fillWorld(g, c, new Vec3[] {new Vec3(x1-.12,.035,z1), new Vec3(x1+.12,.035,z1),
                    new Vec3(x2+.12,.035,z2), new Vec3(x2-.12,.035,z2)}, new Color(245, 220, 161));
        }
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            drawCone(g, c, cx + radius * Math.cos(a), cz + radius * Math.sin(a));
        }
    }

    private void drawCone(Graphics2D g, Camera c, double x, double z) {
        Vec3 baseL = new Vec3(x-.22, .03, z), baseR = new Vec3(x+.22, .03, z);
        Vec3 tip = new Vec3(x, .72, z);
        Vec3[] tri = {baseL, baseR, tip};
        fillWorld(g, c, tri, new Color(237, 105, 37));
        Point2D.Double l = c.project(baseL), r = c.project(baseR), t = c.project(tip);
        if (l != null && r != null && t != null) {
            g.setColor(new Color(255, 222, 179));
            int[] px = {(int)l.x,(int)r.x,(int)t.x};
            int[] py = {(int)l.y,(int)r.y,(int)t.y};
            g.drawPolygon(px, py, 3);
        }
    }

    private void drawBoard(Graphics2D g, Camera c, String text, double x, double z) {
        Point2D.Double p = c.project(new Vec3(x, 2.2, z));
        if (p == null || p.x < -100 || p.x > WIDTH+100 || p.y < 0 || p.y > HEIGHT) return;
        g.setColor(new Color(25, 38, 42)); g.fillRoundRect((int)p.x-47,(int)p.y-17,94,28,5,5);
        g.setColor(new Color(235, 225, 188)); g.setFont(new Font("SansSerif", Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics(); g.drawString(text,(int)p.x-fm.stringWidth(text)/2,(int)p.y+2);
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
        double[] wx = {-.82,.82,-.82,.82}, wz = {1.18,1.18,-1.18,-1.18};
        for (int i = 0; i < 4; i++) {
            double steer = i < 2 ? steeringVisual : 0;
            drawBox(g,c,wx[i],.39,wz[i],.27,.42,.62,new Color(24,27,29),steer);
            drawBox(g,c,wx[i]+(wx[i]<0?.14:-.14),.39,wz[i],.075,.19,.28,new Color(112,119,120),steer);
        }
        drawFace(g,c,new double[][]{{-.96,.42,-1.7},{.96,.42,-1.7},{.90,.66,1.52},{-.90,.66,1.52}},new Color(172,37,31));
        drawFace(g,c,new double[][]{{-.96,.42,-1.7},{-.90,.66,1.52},{-.72,1.08,.98},{-.66,1.02,-.88}},new Color(203,48,39));
        drawFace(g,c,new double[][]{{.96,.42,-1.7},{.66,1.02,-.88},{.72,1.08,.98},{.90,.66,1.52}},new Color(141,28,27));
        drawFace(g,c,new double[][]{{-.66,1.02,-.88},{.66,1.02,-.88},{.56,1.38,.67},{-.56,1.38,.67}},new Color(34,54,64));
        drawFace(g,c,new double[][]{{-.56,1.38,.67},{.56,1.38,.67},{.72,1.08,.98},{-.72,1.08,.98}},new Color(54,82,91));
        drawFace(g,c,new double[][]{{-.66,1.02,-.88},{-.56,1.38,.67},{.56,1.38,.67},{.66,1.02,-.88}},new Color(42,63,72));
        drawFace(g,c,new double[][]{{-.90,.66,1.52},{-.72,1.08,.98},{.72,1.08,.98},{.90,.66,1.52}},new Color(223,218,197));
        drawFace(g,c,new double[][]{{-.85,.55,1.49},{-.65,.55,1.49},{-.65,.72,1.46},{-.85,.72,1.46}},new Color(255,242,190));
        drawFace(g,c,new double[][]{{.65,.55,1.49},{.85,.55,1.49},{.85,.72,1.46},{.65,.72,1.46}},new Color(255,242,190));
        drawFace(g,c,new double[][]{{-.83,.53,-1.72},{-.58,.53,-1.72},{-.58,.7,-1.68},{-.83,.7,-1.68}},new Color(230,34,27));
        drawFace(g,c,new double[][]{{.58,.53,-1.72},{.83,.53,-1.72},{.83,.7,-1.68},{.58,.7,-1.68}},new Color(230,34,27));
    }

    private void drawFace(Graphics2D g, Camera c, double[][] local, Color color) {
        Vec3[] points = new Vec3[local.length];
        for (int i=0;i<local.length;i++) points[i]=car.localToWorld(local[i][0],local[i][1],local[i][2]);
        Polygon p=projectPolygon(c,points);
        if(p!=null){g.setColor(color);g.fillPolygon(p);g.setColor(color.darker());g.drawPolygon(p);}
    }

    private void drawBox(Graphics2D g, Camera c, double x,double y,double z,double w,double h,double l,Color color,double steer) {
        double[][] q={{-w/2,-h/2,-l/2},{w/2,-h/2,-l/2},{w/2,h/2,-l/2},{-w/2,h/2,-l/2},
            {-w/2,-h/2,l/2},{w/2,-h/2,l/2},{w/2,h/2,l/2},{-w/2,h/2,l/2}};
        Vec3[] v=new Vec3[8];
        for(int i=0;i<8;i++){double lx=q[i][0]*Math.cos(steer)+q[i][2]*Math.sin(steer);
            double lz=-q[i][0]*Math.sin(steer)+q[i][2]*Math.cos(steer);v[i]=car.localToWorld(x+lx,y+q[i][1],z+lz);}
        int[][] faces={{0,1,2,3},{4,7,6,5},{0,4,5,1},{3,2,6,7},{0,3,7,4},{1,5,6,2}};
        Color[] shades={color.darker(),color,color.darker(),color.brighter(),color.darker(),color.darker()};
        for(int[] f:faces)fillWorld(g,c,new Vec3[]{v[f[0]],v[f[1]],v[f[2]],v[f[3]]},shades[0]);
    }

    private void drawHud(Graphics2D g) {
        g.setColor(new Color(10,17,22,220)); g.fillRoundRect(20,18,350,164,14,14);
        g.setColor(new Color(220,239,239)); g.setFont(new Font("SansSerif",Font.BOLD,17));
        g.drawString("VEHICLE DYNAMICS  /  PROVING GROUND",36,44);
        g.setFont(new Font("Monospaced",Font.BOLD,24));
        g.drawString(String.format("%3d km/h",(int)Math.round(car.speed()*3.6)),36,77);
        g.setFont(new Font("Monospaced",Font.PLAIN,14));
        g.drawString(String.format("GEAR %s   %4d RPM",car.gearLabel(),(int)car.rpm),36,101);
        g.drawString(String.format("SURFACE %-8s  GRIP %.2f",car.surface.name,car.surface.mu),36,123);
        g.drawString(String.format("SLIP %5.1f°   YAW %5.1f°/s",Math.toDegrees(car.slipAngle),Math.toDegrees(car.yawRate)),36,145);
        g.drawString(String.format("LAT G %+.2f   DIST %.0f m",car.lateralG,car.distance()),36,167);
        g.setColor(new Color(13,19,23,210)); g.fillRoundRect(20,HEIGHT-68,688,46,12,12);
        g.setColor(Color.WHITE);g.setFont(new Font("SansSerif",Font.PLAIN,13));
        g.drawString("W/↑ throttle   S/↓ brake   A,D/←,→ steer   SPACE handbrake   R reset",34,HEIGHT-40);
        g.setColor(new Color(235,236,215));g.setFont(new Font("SansSerif",Font.BOLD,14));
        g.drawString("OPEN PAD   •   SKIDPADS   •   SLALOM   •   BRAKE TEST   •   SURFACE LANES",WIDTH-510,30);
        int cx=WIDTH-73,cy=93;
        g.setColor(new Color(15,20,23,190));g.fillOval(cx-32,cy-32,64,64);
        g.setColor(new Color(226,232,224));g.setStroke(new BasicStroke(5));g.drawArc(cx-23,cy-23,46,46,25,130);
        g.drawLine(cx,cy,cx+(int)(20*Math.sin(steeringVisual*1.8)),cy-(int)(20*Math.cos(steeringVisual*1.8)));
    }

    @Override public void keyPressed(KeyEvent e) { keys.add(e.getKeyCode()); if(e.getKeyCode()==KeyEvent.VK_R)car.reset(); }
    @Override public void keyReleased(KeyEvent e) { keys.remove(e.getKeyCode()); }
    @Override public void keyTyped(KeyEvent e) { }
}

class Car {
    // Body-frame SI dynamics; x is lateral and positive z is forward.
    double x,z,yaw,vx,vz,yawRate,steerAngle,rpm=900,slipAngle,lateralG;
    int gear=1;
    Surface surface=Surface.ASPHALT;
    private static final double MASS=1320,IZZ=1850,G=9.81,HALF_TRACK=.79,FRONT_Z=1.22,REAR_Z=-1.28,WHEEL_RADIUS=.32;
    private static final double[] RATIOS={0,3.25,2.10,1.45,1.10,.86};
    private static final double FINAL_DRIVE=3.7;
    void reset(){x=z=yaw=vx=vz=yawRate=steerAngle=0;rpm=900;gear=1;}
    double speed(){return Math.sqrt(vx*vx+vz*vz);}
    double distance(){return Math.sqrt(x*x+z*z);}
    String gearLabel(){return speed()<.25&&vz<0?"R":Integer.toString(gear);}
    Vec3 localToWorld(double lx,double ly,double lz){double c=Math.cos(yaw),s=Math.sin(yaw);return new Vec3(x+lx*c+lz*s,ly,z-lx*s+lz*c);}

    void update(double dt,double throttle,double brake,boolean handbrake,double steerInput){
        double v=speed(), steerLimit=.52/(1+v*.035), target=-steerInput*steerLimit;
        steerAngle+=(target-steerAngle)*Math.min(1,dt*(v>15?5:9));
        surface=Surface.at(x);
        double wheelRpm=Math.abs(vz)/(2*Math.PI*WHEEL_RADIUS)*60*RATIOS[gear]*FINAL_DRIVE;
        rpm=Math.max(850,900+throttle*4700+wheelRpm*.72);
        if(rpm>6200&&gear<RATIOS.length-1)gear++;else if(rpm<2100&&gear>1&&throttle<.85)gear--;
        double torque=engineTorque(rpm)*throttle*RATIOS[gear]*FINAL_DRIVE*.86;
        double driveForce=Math.min(6200,torque/WHEEL_RADIUS);
        double fxTotal=0,fzTotal=0,moment=0;
        double longTransfer=clamp(brake*3100-throttle*1050,-1800,1800);
        double latTransfer=clamp(vx*Math.abs(vz)*.75,-1100,1100);
        double weightedSlip=0;
        for(int i=0;i<4;i++){
            boolean front=i<2,left=i%2==0;
            double wx=left?-HALF_TRACK:HALF_TRACK,wz=front?FRONT_Z:REAR_Z;
            double load=Math.max(350,MASS*G/4+(front?longTransfer:-longTransfer)/2+(left?-latTransfer:latTransfer)/2);
            double steer=front?steerAngle:0;
            double cvx=vx-yawRate*wz,cvz=vz+yawRate*wx,c=Math.cos(steer),s=Math.sin(steer);
            double tireLong=cvx*s+cvz*c,tireSide=cvx*c-cvz*s;
            double slip=Math.atan2(tireSide,Math.abs(tireLong)+1.2);
            weightedSlip+=slip*.25;
            double grip=surface.mu*load;
            double fy=clamp(-62000*slip,-grip,grip);
            double fx=front?0:driveForce/2;
            if(tireLong<-.2&&!front)fx=-driveForce/2;
            double wheelBrake=brake*5200/4+(handbrake&&!front?3300:0);
            if(Math.abs(tireLong)>.12)fx-=Math.signum(tireLong)*wheelBrake;else if(brake>0||handbrake)fx=0;
            fx-=Math.signum(tireLong)*Math.min(surface.rolling,Math.abs(tireLong)*16);
            double combined=Math.sqrt(fx*fx+fy*fy);
            if(combined>grip){double scale=grip/combined;fx*=scale;fy*=scale;}
            double bodyFx=fx*s+fy*c,bodyFz=fx*c-fy*s;
            fxTotal+=bodyFx;fzTotal+=bodyFz;moment+=wx*bodyFz-wz*bodyFx;
        }
        slipAngle=weightedSlip;
        double drag=.5*1.225*.72*v*v;
        fxTotal-=vx*38;fzTotal-=Math.signum(vz)*(drag+surface.rolling*4);
        double ax=fxTotal/MASS+yawRate*vz,az=fzTotal/MASS-yawRate*vx;
        lateralG=ax/G;
        vx+=ax*dt;vz+=az*dt;yawRate+=moment/IZZ*dt;
        yawRate*=1-Math.min(.25,dt*(Math.abs(vz)<.5?2.5:.10));
        yaw+=yawRate*dt;
        double cs=Math.cos(yaw),sn=Math.sin(yaw);
        x+=(vx*cs+vz*sn)*dt;z+=(-vx*sn+vz*cs)*dt;
        if(Math.abs(vx)<.015)vx=0;if(Math.abs(vz)<.015)vz=0;
    }
    private double engineTorque(double r){return clamp(245+75*Math.exp(-Math.pow((r-3600)/1900,2))-Math.max(0,r-5700)*.09,90,315);}
    private double clamp(double a,double lo,double hi){return Math.max(lo,Math.min(hi,a));}
}

enum Surface {
    GRAVEL("GRAVEL",.66,135), WET("WET",.78,90), ASPHALT("ASPHALT",1.08,70), ICE("ICE",.16,28), DIRT("DIRT",.52,150);
    final String name;final double mu,rolling;
    Surface(String name,double mu,double rolling){this.name=name;this.mu=mu;this.rolling=rolling;}
    static Surface at(double x){if(x < -20)return GRAVEL;if(x < -7)return WET;if(x <= 7)return ASPHALT;if(x <= 20)return ICE;return DIRT;}
}

class Camera {
    private final double x,y,z,yaw,pitch;
    private static final double FOCAL=680;
    Camera(Car car){yaw=car.yaw;pitch=Math.toRadians(10);x=car.x-Math.sin(yaw)*8.2;z=car.z-Math.cos(yaw)*8.2;y=3.3;}
    Point2D.Double project(Vec3 p){double dx=p.x-x,dy=p.y-y,dz=p.z-z;
        double side=dx*Math.cos(yaw)-dz*Math.sin(yaw),forward=dx*Math.sin(yaw)+dz*Math.cos(yaw);
        double depth=forward*Math.cos(pitch)+dy*Math.sin(pitch),up=dy*Math.cos(pitch)-forward*Math.sin(pitch);
        if(depth<.45)return null;return new Point2D.Double(500+FOCAL*side/depth,355-FOCAL*up/depth);}
}

class Vec3 {
    final double x,y,z;
    Vec3(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
}
