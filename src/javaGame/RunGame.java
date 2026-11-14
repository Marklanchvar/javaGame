package javaGame;

import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public class RunGame {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createAndShowWindow();
            }
        });
    }

    private static void createAndShowWindow() {
        JFrame window = new JFrame("Java Game");
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setSize(800, 600);
        window.setLocationRelativeTo(null);

        JLabel message = new JLabel("Welcome to Java Game", SwingConstants.CENTER);
        message.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        window.add(message, BorderLayout.CENTER);

        window.setVisible(true);
    }
}
