package com.guardiansofangkor.renderer;

import com.guardiansofangkor.engine.GameState;
import com.guardiansofangkor.entities.EnemyType;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** A BTD6-style clickable sidebar to spawn enemies on demand, with a slide-out animation. */
public class SandboxTray extends JPanel {

    private static final int FULL_WIDTH = 230; // 200 for buttons + 30 for the edge tab
    private static final int EDGE_WIDTH = 30;

    private int currentWidth = FULL_WIDTH;
    private int targetWidth = FULL_WIDTH;

    private final Timer idleTimer;
    private final Timer animTimer;

    // If the player manually clicks it shut, lock it so hover doesn't fight the click
    private boolean pinnedClosed = false;

    public SandboxTray(GameState state) {
        setLayout(new BorderLayout());
        setBackground(new Color(40, 40, 50));
        setPreferredSize(new Dimension(FULL_WIDTH, 0));

        // 1. The Edge Tab (Upgraded to match the gold aesthetic)
        JPanel edgeTab = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                g2.setColor(new Color(0xFF, 0xE7, 0xA8)); // Bright Gold
                g2.setFont(new Font("Monospaced", Font.BOLD, 18));

                String text = targetWidth == FULL_WIDTH ? ">>" : "<<";

                int textW = g2.getFontMetrics().stringWidth(text);
                int textH = g2.getFontMetrics().getAscent();
                g2.drawString(text, (getWidth() - textW) / 2, (getHeight() + textH) / 2 - 2);
            }
        };
        edgeTab.setPreferredSize(new Dimension(EDGE_WIDTH, 0));
        edgeTab.setBackground(new Color(30, 25, 20)); // Darker temple brown
        edgeTab.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // 2. The Button Grid
        JPanel btnGrid = new JPanel(new GridLayout(0, 2, 5, 5));
        btnGrid.setBackground(new Color(40, 40, 50, 0)); // Transparent so tray background shows
        btnGrid.setBorder(new EmptyBorder(10, 10, 10, 10));

        add(edgeTab, BorderLayout.WEST);
        add(btnGrid, BorderLayout.CENTER);

        // 3. The Animation Timer
        animTimer = new Timer(16, e -> {
            if (currentWidth == targetWidth) {
                ((Timer) e.getSource()).stop();
                return;
            }
            int step = 25; // Speed of the slide animation
            if (currentWidth < targetWidth) {
                currentWidth = Math.min(currentWidth + step, targetWidth);
            } else {
                currentWidth = Math.max(currentWidth - step, targetWidth);
            }
            setPreferredSize(new Dimension(currentWidth, 0));
            revalidate();
            repaint();
        });

        // 4. The Idle Timer (Wait 5 seconds, then auto-close)
        idleTimer = new Timer(5000, e -> {
            if (targetWidth == FULL_WIDTH) {
                closeTray();
            }
        });
        idleTimer.setRepeats(false);

        // 5. Manual Click Toggle
        edgeTab.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (targetWidth == FULL_WIDTH) {
                    pinnedClosed = true; // Lock it shut
                    closeTray();
                } else {
                    pinnedClosed = false; // Unlock it
                    openTray();
                }
            }
        });

        // 6. Global Hover Detection
        MouseAdapter hoverHandler = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                // Only slide open on hover if you didn't just manually click it shut
                if (!pinnedClosed && targetWidth == EDGE_WIDTH) {
                    openTray();
                }
                idleTimer.stop();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // Verify the mouse actually left the entire tray, not just moved between buttons
                Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), SandboxTray.this);
                if (!SandboxTray.this.contains(p)) {
                    pinnedClosed = false; // Reset the pin once the mouse completely leaves
                    idleTimer.restart();
                }
            }
        };

        addMouseListener(hoverHandler);
        edgeTab.addMouseListener(hoverHandler);
        btnGrid.addMouseListener(hoverHandler);

        // 7. Generate Sleek Custom Buttons
        for (EnemyType type : EnemyType.values()) {
            SandboxButton btn = new SandboxButton(type.name());
            btn.addMouseListener(hoverHandler); // Keep the tray slide-out working
            btn.addActionListener(e -> {
                if (state != null) state.spawnInSandbox(type);
                pinnedClosed = false;
                idleTimer.restart(); // Reset the 5-second clock after spawning
            });
            btnGrid.add(btn);
        }
    }

    private void openTray() {
        idleTimer.stop();
        if (targetWidth != FULL_WIDTH) {
            targetWidth = FULL_WIDTH;
            animTimer.start();
            repaint();
        }
    }

    private void closeTray() {
        idleTimer.stop();
        if (targetWidth != EDGE_WIDTH) {
            targetWidth = EDGE_WIDTH;
            animTimer.start();
            repaint();
        }
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible == isVisible()) {
            return;
        }
        super.setVisible(visible);
        if (visible) {
            pinnedClosed = false;
            currentWidth = FULL_WIDTH;
            targetWidth = FULL_WIDTH;
            setPreferredSize(new Dimension(currentWidth, 0));
            idleTimer.restart();
        } else {
            idleTimer.stop();
            animTimer.stop();
        }
    }

    // =========================================================================
    // INNER CLASS: The sleek, custom-painted game button
    // =========================================================================
    private static class SandboxButton extends JButton {
        private boolean isHovered = false;

        private static final Color BG_NORMAL = new Color(0x1E, 0x19, 0x14, 255);
        private static final Color BG_HOVER = new Color(0x3A, 0x2A, 0x20, 255);
        private static final Color BG_PRESSED = new Color(0x10, 0x0D, 0x0A, 255);
        private static final Color BORDER = new Color(0x7A, 0x66, 0x48);
        private static final Color TEXT_COLOR = new Color(0xFF, 0xE7, 0xA8);

        public SandboxButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setForeground(TEXT_COLOR);
            setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    isHovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    isHovered = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (getModel().isPressed()) {
                    g2.setColor(BG_PRESSED);
                } else if (isHovered) {
                    g2.setColor(BG_HOVER);
                } else {
                    g2.setColor(BG_NORMAL);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);

                g2.setColor(BORDER);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 10, 10);
            } finally {
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}