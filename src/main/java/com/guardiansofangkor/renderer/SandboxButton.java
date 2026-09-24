package com.guardiansofangkor.ui; // Update this to match your actual package!

import javax.swing.JButton;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class SandboxButton extends JButton {

    private boolean hovered = false;

    // Colors pulled straight from your game's Palette!
    private static final Color BG_NORMAL = new Color(0x1E, 0x19, 0x14, 210); // Dark brown/black
    private static final Color BG_HOVER = new Color(0x3A, 0x2A, 0x20, 230);  // Lighter brown
    private static final Color BG_PRESSED = new Color(0x10, 0x0D, 0x0A, 255);
    private static final Color BORDER = new Color(0x7A, 0x66, 0x48);         // Dim gold
    private static final Color TEXT_COLOR = new Color(0xFF, 0xE7, 0xA8);     // Bright gold

    public SandboxButton(String text) {
        super(text);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setForeground(TEXT_COLOR);
        setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Track when the mouse enters and exits for the hover glow
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = false;
                repaint();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 1. Draw the background based on mouse state
            if (getModel().isPressed()) {
                g2.setColor(BG_PRESSED);
            } else if (hovered) {
                g2.setColor(BG_HOVER);
            } else {
                g2.setColor(BG_NORMAL);
            }
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);

            // 2. Draw the sleek border
            g2.setColor(BORDER);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 14, 14);

        } finally {
            g2.dispose();
        }

        // 3. Draw the text normally on top
        super.paintComponent(g);
    }
}