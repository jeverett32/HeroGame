import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class TitlePanel extends JPanel {
    private HeroGame game;
    private BufferedImage backgroundImage;

    public TitlePanel(HeroGame game) {
        this.game = game;

        // Load the background image
        try {
            backgroundImage = ImageIO.read(getClass().getResource("/resources/titlepage_background.png"));
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Could not load title background image!");
    
        }
        setLayout(new GridBagLayout()); // Use a flexible layout
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.insets = new Insets(10, 10, 10, 10);

        // Game Title
        JLabel titleLabel = new JLabel("Donkey");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 48));
        titleLabel.setForeground(Color.WHITE); // Set text color to white
        add(titleLabel, gbc);

        // --- Create Buttons ---
        JButton newGameButton = new JButton("New Game");
        JButton loadGameButton = new JButton("Load Game");
        JButton exitButton = new JButton("Exit");

        // Disable the focus rectangle around buttons
        newGameButton.setFocusPainted(false);
        loadGameButton.setFocusPainted(false);
        exitButton.setFocusPainted(false);

        // --- Set a uniform size for all buttons ---
        Dimension buttonSize = new Dimension(200, 30); // Width, Height
        newGameButton.setPreferredSize(buttonSize);
        loadGameButton.setPreferredSize(buttonSize);
        exitButton.setPreferredSize(buttonSize);

        // --- Set a new color for all buttons ---
        Color buttonColor = Color.LIGHT_GRAY;
        newGameButton.setBackground(buttonColor);
        loadGameButton.setBackground(buttonColor);
        exitButton.setBackground(buttonColor);

        // --- Add Listeners and Add to Panel ---
        newGameButton.addActionListener(e -> game.startGame());
        loadGameButton.addActionListener(e -> game.loadGame());
        exitButton.addActionListener(e -> System.exit(0));

        add(newGameButton, gbc);
        add(loadGameButton, gbc);
        add(exitButton, gbc);
    }

    // Override paintComponent to draw the background ---
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g); // Always call the superclass method first
        
        // Draw the background image, scaling it to fill the entire panel
        if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, this.getWidth(), this.getHeight(), null);
        }

        // Create a white color with an alpha value (0-255) for transparency.
        // A lower number is more transparent.
        Color overlay = new Color(255, 255, 255, 50);
        g.setColor(overlay);

        // Fill the entire panel with this transparent color
        g.fillRect(0, 0, this.getWidth(), this.getHeight());
    }
    
    @Override
    public Dimension getPreferredSize() {
        // Set a default size for the title screen
        return new Dimension(854, 480);
    }
}
