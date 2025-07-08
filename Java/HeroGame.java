import java.awt.*;
import javax.swing.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage; // Import BufferedImage
import java.io.IOException;          // Import IOException for image loading
import javax.imageio.ImageIO;        // Import ImageIO for image loading
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Main class for the hero adventure game.
 * This class initializes the game window and starts the game.
 */

public class HeroGame extends JFrame {

    public HeroGame() {
        setTitle("Get That Donkey!");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // The window is now resizable
        setResizable(true);

        // Add the main game panel to the window
        add(new HeroGamePanel());

        // This sizes the window to fit the panel's preferred size
        pack();
        
        // Center the window on the screen
        setLocationRelativeTo(null);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            HeroGame game = new HeroGame();
            game.setVisible(true);
            // Start the game logic here
        });
    }
}

class HeroGamePanel extends JPanel {
    // This class will handle
    private static final long serialVersionUID = 1L;

    // Game constants
    public static final int NATIVE_SCREEN_WIDTH = 854; // Width of the game window
    public static final int NATIVE_SCREEN_HEIGHT = 480; // Height of the game window
    public static final int UNIT_SIZE = 48; // Size of each unit in the game

    // World dimensions
    public static final int WORLD_WIDTH = NATIVE_SCREEN_WIDTH *3; // Width in units
    public static final int WORLD_HEIGHT = NATIVE_SCREEN_HEIGHT *3; // Height in units

    // Number of obstacles
    private static final int NUM_ROCKS = 50;
    private static final int NUM_STUMPS = 30;
    private static final int NUM_ENEMIES = 4;

    // hero stats for leveling up
    private int  heroLevel;
    private int heroXP;
    private int xpToNextLevel;

    // Hero's position
    private int heroX, heroY;
    private Random random;

    // Camera position variables
    private int cameraX; // Camera's X position
    private int cameraY; // Camera's Y position

    // Lists to store positions of obstacles and enemies
    private List<Point> rocks;
    private List<Point> stumps;
    private List<Enemy> enemies;
    private final List<DisplayMessage> activeMessages = new ArrayList<>();

    // Images
    private BufferedImage heroImage;
    private BufferedImage enemyImage;
    private BufferedImage rockImage;
    private BufferedImage stumpImage;

    public HeroGamePanel() {
        random = new Random();
        this.setPreferredSize(new Dimension(NATIVE_SCREEN_WIDTH, NATIVE_SCREEN_HEIGHT));
        this.setBackground(Color.WHITE);
        this.setFocusable(true);
        this.addKeyListener(new MyKeyAdapter()); // Add the key listener
        
        // Initialize lists for obstacles
        rocks = new ArrayList<>();
        stumps = new ArrayList<>();
        enemies = new ArrayList<>();

        loadImages();
        startGame();
    }

    /**
     * An inner class to represent an enemy with its own state.
     */
    public class Enemy {
        int x, y;
        int level;
        int currentHealth;
        int maxHealth;

        Enemy() {
            this.level = 0; // Starting level
            respawn(); // Initial spawn
        }

        // Method for when the enemy takes damage
        public boolean takeDamage(int amount) {
            this.currentHealth -= amount;
            return this.currentHealth <= 0; // Returns true if the enemy is defeated
        }

        // Handles respawning, whether by leveling up or starting the game
        public void respawn() {
            // When an enemy respawns, it levels up
            this.level++;
            this.maxHealth = this.level;
            this.currentHealth = this.maxHealth;
            teleport();
        }

        // Moves the enemy to a new random, valid position
        public void teleport() {
            do {
                this.x = random.nextInt((WORLD_WIDTH / UNIT_SIZE)) * UNIT_SIZE;
                this.y = random.nextInt((WORLD_HEIGHT / UNIT_SIZE)) * UNIT_SIZE;
            } while (isObstacle(this.x, this.y) || (this.x == heroX && this.y == heroY)); // Ensure the enemy doesn't spawn on an obstacle or the hero
        }
    }

    private class DisplayMessage {
        String text;
        long endTime;

        DisplayMessage(String text) {
            this.text = text;
            this.endTime = System.currentTimeMillis() + 3000;
        }
    }

    public void startGame() {
        // Clear old obstacles before generating new ones
        rocks.clear();
        stumps.clear();
        enemies.clear();
        
        // Generate obstacles first, so hero and enemy can avoid them
        generateObstacles();

        // Create all the enemies
        for (int i = 0; i < NUM_ENEMIES; i++) {
            enemies.add(new Enemy());
        }

        // Place the hero, ensuring it doesn't start on an obstacle
        do {
            heroX = (NATIVE_SCREEN_WIDTH / 2 / UNIT_SIZE) * UNIT_SIZE;
            heroY = (NATIVE_SCREEN_HEIGHT / 2 / UNIT_SIZE) * UNIT_SIZE;
        } while (isObstacle(heroX, heroY));

        // Initialize hero stats
        this.heroLevel = 1; // Starting level
        this.heroXP = 0; // Starting XP
        this.xpToNextLevel = 100; // XP needed for the next level

        // Place the initial enemy and set the inital camera position
        updateCamera();
    }

    public void displayMessage(String text) {
        activeMessages.add(new DisplayMessage(text));
    }

    private void loadImages() {
        try {
            heroImage = ImageIO.read(getClass().getResource("/hero_image.png"));
            enemyImage = ImageIO.read(getClass().getResource("/enemy_image.png"));
            rockImage = ImageIO.read(getClass().getResource("/rock.png"));
            stumpImage = ImageIO.read(getClass().getResource("/stump.png"));
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error loading one or more images.");
            // Set images to null so the game can fall back to drawing shapes
            heroImage = enemyImage = rockImage = stumpImage = null;
        }
    }

    private void generateObstacles() {
        // Generate rocks
        for (int i = 0; i < NUM_ROCKS; i++) {
            int x, y;
            do {
                x = random.nextInt(WORLD_WIDTH / UNIT_SIZE) * UNIT_SIZE;
                y = random.nextInt(WORLD_HEIGHT / UNIT_SIZE) * UNIT_SIZE;
            } while (isObstacle(x, y)); // Ensure no two obstacles overlap
            rocks.add(new Point(x, y));
        }

        // Generate stumps
        for (int i = 0; i < NUM_STUMPS; i++) {
            int x, y;
            do {
                x = random.nextInt(WORLD_WIDTH / UNIT_SIZE) * UNIT_SIZE;
                y = random.nextInt(WORLD_HEIGHT / UNIT_SIZE) * UNIT_SIZE;
            } while (isObstacle(x, y)); // Ensure stumps don't overlap with rocks or other stumps
            stumps.add(new Point(x, y));
        }
    }

    // Helper method to check if a location is an obstacle ---
    private boolean isObstacle(int x, int y) {
        Point target = new Point(x, y);
        return rocks.contains(target) || stumps.contains(target);
    }
    
    // Method handling all hero-to-enemy collisions
    public void checkCollisions() {
        // Use a temporary enemy variable to avoid issues while modifying the list
        Enemy collidedEnemy = null;

        for (Enemy enemy : enemies) {
            if (heroX == enemy.x && heroY == enemy.y) {
                collidedEnemy = enemy; // Found a collision
                break; // Exit the loop since we found a collision
            }
        }

        if (collidedEnemy != null) {
            boolean didDie = collidedEnemy.takeDamage(1); // Deal 1 damage to the enemy

            if (didDie) {
                // Grant XP based on the enemy's level before it respawns
                int xpGained = collidedEnemy.level * 25;
                addXP(xpGained);
                displayMessage("Gained " + xpGained + " XP!");

                collidedEnemy.respawn(); // Respawn the enemy and level it up
            } else {
                collidedEnemy.teleport(); // Enemy just moves
            }
        }
    }

    public void addXP(int amount) {
        heroXP += amount;

        // Check if the hero can level up
        while (heroXP >= xpToNextLevel) {
            levelUp();
        }
    }

    private void levelUp() {
        heroLevel++;
        heroXP -= xpToNextLevel; // Subtract the cost, but keep leftover XP

        // Increase the XP required for the next level (e.g., by 50%)
        xpToNextLevel *= 1.5;

        displayMessage("LEVEL UP!");

        // --- This is where you would add rewards for leveling up ---
        // Example: Increase hero speed, health, or damage in the future.
    }

    private void updateCamera() {
        // Center the camera on the hero's position
        cameraX = heroX - (NATIVE_SCREEN_WIDTH / 2);
        cameraY = heroY - (NATIVE_SCREEN_HEIGHT / 2);

        // Ensure the camera doesn't go out of bounds
        cameraX = Math.max(0, Math.min(cameraX, WORLD_WIDTH - NATIVE_SCREEN_WIDTH));
        cameraY = Math.max(0, Math.min(cameraY, WORLD_HEIGHT - NATIVE_SCREEN_HEIGHT));
    }

    // Draws the active message if it exists and hasn't expired
    private void drawActiveMessages(Graphics2D g2d) {
    // Define the base position and the space between lines
    final int baseYOffset = 30; // Base position above the hero
    final int lineHeight = 15;  // Vertical space between messages
    int linesUp = 0;            // Counter for how many lines to move up

    g2d.setFont(new Font("Arial", Font.BOLD, 12));
    g2d.setColor(Color.YELLOW);

    // Loop backwards to safely remove items while iterating
    for (int i = activeMessages.size() - 1; i >= 0; i--) {
        DisplayMessage message = activeMessages.get(i);

        // If the message has expired, remove it and skip to the next one
        if (System.currentTimeMillis() > message.endTime) {
            activeMessages.remove(i);
            continue; // Skip the rest of the loop for this item
        }

        // Calculate the Y position for this message
        int messageY = heroY - baseYOffset - (linesUp * lineHeight);

        // Center the text horizontally
        FontMetrics fm = g2d.getFontMetrics();
        int messageWidth = fm.stringWidth(message.text);
        int messageX = heroX + (UNIT_SIZE / 2) - (messageWidth / 2);

        g2d.drawString(message.text, messageX, messageY);

        // Increment the counter to draw the next message higher
        linesUp++;
    }
}

    // This method draws the XP bar above the hero's head
    private void drawXpBar(Graphics2D g2d) {
        // Define the dimensions and position of the bar relative to the hero
        final int barWidth = 50;
        final int barHeight = 8;
        final int yOffset = 15; // How far above the hero's head the bar should be

        // Center the bar horizontally over the hero
        int barX = heroX + (UNIT_SIZE / 2) - (barWidth / 2);
        int barY = heroY - yOffset;

        // --- Draw the Level Text ---
        String levelText = "Lvl " + heroLevel;
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        // Position the text to the left of the bar
        g2d.drawString(levelText, barX - 35, barY + barHeight);

        // --- Draw the XP Bar ---
        // Draw the dark background of the bar first
        g2d.setColor(Color.DARK_GRAY);
        g2d.fillRect(barX, barY, barWidth, barHeight);

        // Calculate the width of the filled portion of the bar
        double xpPercentage = (double) heroXP / xpToNextLevel;
        int fillWidth = (int) (barWidth * xpPercentage);

        // Draw the filled portion of the bar
        g2d.setColor(Color.CYAN); // A bright color for the fill
        g2d.fillRect(barX, barY, fillWidth, barHeight);

        // Draw a white border around the bar so it's clearly visible
        g2d.setColor(Color.WHITE);
        g2d.drawRect(barX, barY, barWidth, barHeight);
    }

    private void drawEnemyUI(Graphics2D g2d, Enemy enemy) {
        final int barWidth = 40;
        final int barHeight = 7;
        final int yOffset = 15;

        int barX = enemy.x + (UNIT_SIZE / 2) - (barWidth / 2);
        int barY = enemy.y - yOffset;

        // Draw the Level Text
        String levelText = "Lvl " + enemy.level;
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        g2d.drawString(levelText, barX - 35, barY + barHeight);

        // Draw the Health Bar background
        g2d.setColor(Color.DARK_GRAY);
        g2d.fillRect(barX, barY, barWidth, barHeight);

        // Calculate health percentage and draw the fill
        double healthPercentage = (double) enemy.currentHealth / enemy.maxHealth;
        int fillWidth = (int) (barWidth * healthPercentage);
        g2d.setColor(Color.RED); // Use red for the health bar fill
        g2d.fillRect(barX, barY, fillWidth, barHeight);

        // Draw a white border around the health bar
        g2d.setColor(Color.WHITE);
        g2d.drawRect(barX, barY, barWidth, barHeight);
    }

    /**
     * This method is called by the system to draw everything on the panel.
     */
    @Override
    protected void paintComponent(Graphics g) {
            super.paintComponent(g); // Always call the superclass method first

            Graphics2D g2d = (Graphics2D) g;

            //Save the original transform
            AffineTransform originalTransform = g2d.getTransform();

            // Calculate scaling factors
            double scaleX = (double) getWidth() / NATIVE_SCREEN_WIDTH;
            double scaleY = (double) getHeight() / NATIVE_SCREEN_HEIGHT;

            // Use the smaller scale factor to maintain aspect ratio
            double scale = Math.min(scaleX, scaleY);

            // Calculate offset to center the scaled content
            int scaledWidth = (int) (NATIVE_SCREEN_WIDTH * scale);
            int scaledHeight = (int) (NATIVE_SCREEN_HEIGHT * scale);
            int offsetX = (getWidth() - scaledWidth) / 2;
            int offsetY = (getHeight() - scaledHeight) / 2;

            // Apply the scaling and translation
            g2d.translate(offsetX, offsetY);
            g2d.scale(scale, scale);

            g2d.translate(-cameraX, -cameraY); // Move the camera

            // Draw the background of the "native" game area
            g2d.setColor(new Color(82, 100, 29)); // Swamp background color
            g2d.fillRect(0, 0, WORLD_WIDTH, WORLD_HEIGHT);

            // Draw obstacles
            for (Point rockPos : rocks) {
                if (rockImage != null) g2d.drawImage(rockImage, rockPos.x, rockPos.y, UNIT_SIZE, UNIT_SIZE, null);
            }
            for (Point stumpPos : stumps) {
                if (stumpImage != null) g2d.drawImage(stumpImage, stumpPos.x, stumpPos.y, UNIT_SIZE, UNIT_SIZE, null);
            }

            // Draw the hero and enemy at their world positions
            if (heroImage != null) g2d.drawImage(heroImage, heroX, heroY, UNIT_SIZE, UNIT_SIZE, null);
            
            // Draw all the enemies and their UI
            for (Enemy enemy : enemies) {
                if (enemyImage != null) {
                    g2d.drawImage(enemyImage, enemy.x, enemy.y, UNIT_SIZE, UNIT_SIZE, null);
                }
                drawEnemyUI(g2d, enemy); // Draw the enemy's health bar
            }

            // Call the method to draw the XP bar over the hero
            drawXpBar(g2d);

            // Call the method to draw the active message if it exists
            drawActiveMessages(g2d);

            // Restore the original transform
            g2d.setTransform(originalTransform);
    }

    public void moveHero(char direction) {
        int nextX = heroX;
        int nextY = heroY;

        switch (direction) {
            case 'U': nextY -= UNIT_SIZE; break;
            case 'D': nextY += UNIT_SIZE; break;
            case 'L': nextX -= UNIT_SIZE; break;
            case 'R': nextX += UNIT_SIZE; break;
        }

        // Boundary and obstacle checking
        if (nextX >= 0 && nextX <  (WORLD_WIDTH) && 
            nextY >= 0 && nextY < (WORLD_HEIGHT) &&
            !isObstacle(nextX, nextY)) {
            
            // If the move is valid, update the hero's position
            heroX = nextX;
            heroY = nextY;
        }
    }

    public class MyKeyAdapter extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            int keyCode = e.getKeyCode();
            char currentDirection = ' '; // Default direction
            switch (keyCode) {
                case KeyEvent.VK_LEFT:
                case KeyEvent.VK_A:
                    currentDirection = 'L'; // Set the current direction to left
                    break;
                case KeyEvent.VK_RIGHT:
                case KeyEvent.VK_D:
                    currentDirection = 'R'; // Set the current direction to right
                    break;
                case KeyEvent.VK_UP:
                case KeyEvent.VK_W:
                    currentDirection = 'U'; // Set the current direction to up
                    break;
                case KeyEvent.VK_DOWN:
                case KeyEvent.VK_S:
                    currentDirection = 'D'; // Set the current direction to down
                    break;
            }
            // Only move if a valid direction key was pressed
            if (currentDirection != ' ') {
                moveHero(currentDirection); // Move the hero
                checkCollisions(); // Check if the hero has reached the enemy
                updateCamera(); // Update the camera position based on the hero's new position
                repaint(); // Redraw the hero at the new position
            }
        }
    }
}