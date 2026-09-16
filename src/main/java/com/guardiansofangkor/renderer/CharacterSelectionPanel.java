package com.guardiansofangkor.renderer;

import com.guardiansofangkor.engine.CharacterType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

public class CharacterSelectionPanel extends JPanel {

    private final CharacterType[] characters = CharacterType.values();

    private int selectedIndex = 0;

    private final JLabel characterImageLabel;
    private final JLabel characterNameLabel;

    private final JButton leftButton;
    private final JButton rightButton;
    private final JButton selectButton;
    private final JButton backButton;

    private final Color backgroundColor = new Color(20, 20, 30);
    private final Color selectedColor = new Color(255, 200, 50);

    public CharacterSelectionPanel() {

        setLayout(new BorderLayout());
        setBackground(backgroundColor);

        // =========================
        // TITLE
        // =========================

        JLabel titleLabel = new JLabel(
                "SELECT YOUR CHARACTER",
                SwingConstants.CENTER
        );

        titleLabel.setFont(new Font("Arial", Font.BOLD, 32));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setBorder(
                BorderFactory.createEmptyBorder(30, 10, 20, 10)
        );

        add(titleLabel, BorderLayout.NORTH);

        // =========================
        // CENTER
        // =========================

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(backgroundColor);

        characterImageLabel = new JLabel();
        characterImageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        characterImageLabel.setVerticalAlignment(SwingConstants.CENTER);

        centerPanel.add(
                characterImageLabel,
                BorderLayout.CENTER
        );

        characterNameLabel = new JLabel(
                "",
                SwingConstants.CENTER
        );

        characterNameLabel.setFont(
                new Font("Arial", Font.BOLD, 28)
        );

        characterNameLabel.setForeground(selectedColor);

        centerPanel.add(
                characterNameLabel,
                BorderLayout.SOUTH
        );

        add(centerPanel, BorderLayout.CENTER);

        // =========================
        // LEFT / RIGHT BUTTONS
        // =========================

        leftButton = createButton("<");
        rightButton = createButton(">");

        JPanel navigationPanel = new JPanel(
                new FlowLayout(
                        FlowLayout.CENTER,
                        50,
                        10
                )
        );

        navigationPanel.setBackground(backgroundColor);

        navigationPanel.add(leftButton);
        navigationPanel.add(rightButton);

        // =========================
        // BOTTOM BUTTONS
        // =========================

        selectButton = createButton("SELECT");
        backButton = createButton("BACK");

        JPanel actionPanel = new JPanel(
                new FlowLayout(
                        FlowLayout.CENTER,
                        30,
                        20
                )
        );

        actionPanel.setBackground(backgroundColor);

        actionPanel.add(backButton);
        actionPanel.add(selectButton);

        JPanel bottomPanel = new JPanel(
                new BorderLayout()
        );

        bottomPanel.setBackground(backgroundColor);

        bottomPanel.add(
                navigationPanel,
                BorderLayout.NORTH
        );

        bottomPanel.add(
                actionPanel,
                BorderLayout.SOUTH
        );

        add(bottomPanel, BorderLayout.SOUTH);

        // =========================
        // EVENTS
        // =========================

        leftButton.addActionListener(this::previousCharacter);
        rightButton.addActionListener(this::nextCharacter);

        selectButton.addActionListener(e -> selectCharacter());
        backButton.addActionListener(e -> goBack());

        // Show first character
        updateCharacter();
    }

    // =====================================================
    // CREATE BUTTON
    // =====================================================

    private JButton createButton(String text) {

        JButton button = new JButton(text);

        button.setFont(
                new Font("Arial", Font.BOLD, 20)
        );

        button.setForeground(Color.WHITE);

        button.setBackground(
                new Color(50, 50, 65)
        );

        button.setFocusPainted(false);

        button.setBorder(
                BorderFactory.createEmptyBorder(
                        10,
                        25,
                        10,
                        25
                )
        );

        return button;
    }

    // =====================================================
    // PREVIOUS CHARACTER
    // =====================================================

    private void previousCharacter(ActionEvent e) {

        selectedIndex--;

        if (selectedIndex < 0) {
            selectedIndex = characters.length - 1;
        }

        updateCharacter();
    }

    // =====================================================
    // NEXT CHARACTER
    // =====================================================

    private void nextCharacter(ActionEvent e) {

        selectedIndex++;

        if (selectedIndex >= characters.length) {
            selectedIndex = 0;
        }

        updateCharacter();
    }

    // =====================================================
    // UPDATE CHARACTER
    // =====================================================

    private void updateCharacter() {

        CharacterType character =
                characters[selectedIndex];

        // Character name
        characterNameLabel.setText(
                character.getDisplayName()
        );

        // Character image
        BufferedImage image =
                loadImage(character.getImagePath());

        if (image != null) {

            Image scaledImage = scaleImage(
                    image,
                    300,
                    300
            );

            characterImageLabel.setIcon(
                    new ImageIcon(scaledImage)
            );

            characterImageLabel.setText("");

        } else {

            characterImageLabel.setIcon(null);

            characterImageLabel.setText(
                    "Image not found"
            );

            characterImageLabel.setForeground(
                    Color.RED
            );
        }
    }

    // =====================================================
    // LOAD IMAGE
    // =====================================================

    private BufferedImage loadImage(String path) {

        try (InputStream input =
                     getClass().getResourceAsStream(path)) {

            if (input == null) {

                System.out.println(
                        "Image not found: " + path
                );

                return null;
            }

            return ImageIO.read(input);

        } catch (IOException e) {

            System.out.println(
                    "Failed to load image: " + path
            );

            return null;
        }
    }

    // =====================================================
    // SCALE IMAGE
    // =====================================================

    private Image scaleImage(
            BufferedImage image,
            int width,
            int height
    ) {

        return image.getScaledInstance(
                width,
                height,
                Image.SCALE_SMOOTH
        );
    }

    private Runnable onSelect;
    private Runnable onBack;

    // =====================================================
    // SELECT CHARACTER
    // =====================================================

    private void selectCharacter() {

        CharacterType selectedCharacter =
                characters[selectedIndex];

        System.out.println(
                "Selected character: "
                        + selectedCharacter.getDisplayName()
        );

        if (onSelect != null) {
            onSelect.run();
        }
    }

    // =====================================================
    // BACK
    // =====================================================

    private void goBack() {

        System.out.println(
                "Back to menu"
        );

        if (onBack != null) {
            onBack.run();
        }
    }

    public void setOnSelect(Runnable onSelect) {
        this.onSelect = onSelect;
    }

    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
    }

    // =====================================================
    // GET SELECTED CHARACTER
    // =====================================================

    public CharacterType getSelectedCharacter() {

        return characters[selectedIndex];
    }

    public int getSelectedCharacterIndex() {

        return selectedIndex;
    }
}