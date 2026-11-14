import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Neon Drift");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);

            GamePanel panel = new GamePanel();
            frame.add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            panel.requestFocusInWindow();
        });
    }
}

class GamePanel extends JPanel implements ActionListener, KeyListener {
    private static final int WIDTH = 900;
    private static final int HEIGHT = 700;
    private static final int HORIZON = 140;
    private static final int LANE_COUNT = 3;

    private final Timer timer = new Timer(16, this);
    private final List<Obstacle> obstacles = new ArrayList<Obstacle>();
    private final Random random = new Random();

    private int playerLane = 1;
    private int score = 0;
    private boolean gameOver = false;
    private double spawnTimer = 0.0;

    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(new Color(8, 12, 20));
        setFocusable(true);
        addKeyListener(this);
        timer.start();
    }

    private void resetGame() {
        obstacles.clear();
        score = 0;
        playerLane = 1;
        gameOver = false;
        spawnTimer = 0.0;
    }

    private void spawnObstacle() {
        int lane = random.nextInt(LANE_COUNT);
        obstacles.add(new Obstacle(lane, 0.0f));
    }

    private void updateGame() {
        if (gameOver) {
            return;
        }

        spawnTimer += 0.03;
        if (spawnTimer >= 1.0) {
            spawnObstacle();
            spawnTimer = 0.0;
        }

        for (int i = obstacles.size() - 1; i >= 0; i--) {
            Obstacle obstacle = obstacles.get(i);
            obstacle.depth += 0.016f + Math.min(0.015f, score * 0.00002f);

            if (obstacle.depth >= 1.0f) {
                if (obstacle.lane == playerLane) {
                    gameOver = true;
                }
                obstacles.remove(i);
                if (!gameOver) {
                    score += 25;
                }
                continue;
            }

            if (obstacle.depth > 0.83f && obstacle.lane == playerLane) {
                gameOver = true;
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        updateGame();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawSky(g2);
        drawRoad(g2);
        drawTraffic(g2);
        drawPlayerCar(g2);
        drawHud(g2);

        if (gameOver) {
            drawGameOver(g2);
        }

        g2.dispose();
    }

    private void drawSky(Graphics2D g2) {
        g2.setColor(new Color(8, 12, 20));
        g2.fillRect(0, 0, WIDTH, HEIGHT);

        g2.setColor(new Color(35, 80, 120, 120));
        for (int i = 0; i < 35; i++) {
            int x = (i * 49 + 23) % WIDTH;
            int y = (i * 67) % (HORIZON + 80);
            g2.fillOval(x, y, 4, 4);
        }

        g2.setColor(new Color(14, 30, 55));
        g2.fillRect(0, 0, WIDTH, HORIZON + 10);
    }

    private void drawRoad(Graphics2D g2) {
        Polygon road = new Polygon();
        road.addPoint(WIDTH / 2 - 120, HORIZON);
        road.addPoint(WIDTH / 2 + 120, HORIZON);
        road.addPoint(WIDTH / 2 + 420, HEIGHT);
        road.addPoint(WIDTH / 2 - 420, HEIGHT);

        g2.setColor(new Color(31, 34, 40));
        g2.fillPolygon(road);

        g2.setColor(new Color(160, 160, 160, 160));
        for (int i = 0; i < 16; i++) {
            float t = i / 15.0f;
            int y = (int)lerp(HORIZON, HEIGHT - 40, t);
            int half = (int)lerp(70, 340, t);
            g2.drawLine(WIDTH / 2 - half, y, WIDTH / 2 - half + 28, y + 12);
            g2.drawLine(WIDTH / 2 + half - 28, y, WIDTH / 2 + half, y + 12);
        }

        g2.setColor(new Color(255, 255, 255, 110));
        for (int i = 1; i < 3; i++) {
            int laneX = WIDTH / 2 - 210 + i * 210;
            for (int j = 0; j < 12; j++) {
                int y = HORIZON + j * 52 + (int)(score % 22);
                int y2 = y + 20;
                if (y2 > HEIGHT) {
                    break;
                }
                g2.drawLine(laneX, y, laneX, y2);
            }
        }
    }

    private void drawTraffic(Graphics2D g2) {
        for (Obstacle obstacle : obstacles) {
            float depth = obstacle.depth;
            int x = laneX(obstacle.lane, depth);
            int y = (int)lerp(HORIZON, HEIGHT - 120, depth);
            int width = (int)lerp(30f, 110f, depth);
            int height = (int)lerp(60f, 180f, depth);
            drawCar(g2, x, y, width, height, new Color(255, 90, 90), false);
        }
    }

    private void drawPlayerCar(Graphics2D g2) {
        int x = laneX(playerLane, 1.0f);
        int y = HEIGHT - 110;
        drawCar(g2, x, y, 118, 190, new Color(70, 200, 255), true);
    }

    private void drawCar(Graphics2D g2, int centerX, int centerY, int width, int height, Color color, boolean isPlayer) {
        g2.setColor(color);
        Shape body = new RoundRectangle2D.Double(centerX - width / 2, centerY - height / 2, width, height, 26, 26);
        g2.fill(body);

        g2.setColor(new Color(30, 35, 45));
        int windowWidth = (int)(width * 0.58);
        int windowHeight = (int)(height * 0.22);
        g2.fillRoundRect(centerX - windowWidth / 2, centerY - height / 2 + 16, windowWidth, windowHeight, 18, 18);
        g2.fillRoundRect(centerX - windowWidth / 2, centerY + height / 2 - 16 - windowHeight, windowWidth, windowHeight, 18, 18);

        g2.setColor(new Color(20, 20, 22));
        int wheelSize = Math.max(12, width / 6);
        int wheelOffsetX = width / 2 + 6;
        int wheelOffsetY = height / 2 + 10;
        g2.fillOval(centerX - wheelOffsetX, centerY - wheelOffsetY, wheelSize, wheelSize);
        g2.fillOval(centerX + wheelOffsetX - wheelSize, centerY - wheelOffsetY, wheelSize, wheelSize);
        g2.fillOval(centerX - wheelOffsetX, centerY + wheelOffsetY - wheelSize, wheelSize, wheelSize);
        g2.fillOval(centerX + wheelOffsetX - wheelSize, centerY + wheelOffsetY - wheelSize, wheelSize, wheelSize);

        if (isPlayer) {
            g2.setColor(new Color(255, 255, 255, 180));
            g2.fillOval(centerX - 5, centerY - 12, 10, 10);
        }
    }

    private int laneX(int lane, float depth) {
        float roadHalf = lerp(80f, 300f, depth);
        return WIDTH / 2 + (lane - 1) * (int)(roadHalf * 0.85f);
    }

    private float lerp(float start, float end, float t) {
        return start + (end - start) * t;
    }

    private void drawHud(Graphics2D g2) {
        g2.setColor(new Color(255, 255, 255, 200));
        g2.setFont(new Font("SansSerif", Font.BOLD, 28));
        g2.drawString("Score: " + score, 30, 45);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 18));
        g2.drawString("A / D or Left / Right", 30, HEIGHT - 25);
    }

    private void drawGameOver(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 140));
        g2.fillRoundRect(220, 210, 460, 180, 30, 30);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 52));
        g2.drawString("CRASH!", 335, 300);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 24));
        g2.drawString("Score: " + score + "   Press R to restart", 255, 350);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (gameOver && e.getKeyCode() == KeyEvent.VK_R) {
            resetGame();
            return;
        }

        if (e.getKeyCode() == KeyEvent.VK_LEFT || e.getKeyCode() == KeyEvent.VK_A) {
            if (playerLane > 0) {
                playerLane--;
            }
        }

        if (e.getKeyCode() == KeyEvent.VK_RIGHT || e.getKeyCode() == KeyEvent.VK_D) {
            if (playerLane < 2) {
                playerLane++;
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }
}

class Obstacle {
    public int lane;
    public float depth;

    public Obstacle(int lane, float depth) {
        this.lane = lane;
        this.depth = depth;
    }
}
