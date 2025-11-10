import java.awt.*;
import javax.swing.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.io.Serializable;

class HeroGamePanel extends JPanel implements Runnable {
    private HeroGame game; // Reference to the main game frame
    private static final long serialVersionUID = 1L;

    // Game constants
    public static final int NATIVE_SCREEN_WIDTH = 854;
    public static final int NATIVE_SCREEN_HEIGHT = 480;
    public static final int UNIT_SIZE = 48;

    // World dimensions
    public static final int WORLD_WIDTH = NATIVE_SCREEN_WIDTH * 5;
    public static final int WORLD_HEIGHT = NATIVE_SCREEN_HEIGHT * 5;

    // Object counts
    private static final int NUM_ROCKS = 75;
    private static final int NUM_STUMPS = 55;
    private static final int NUM_ENEMIES = 4;
    private static final int NUM_COINS = 5;
    private static final int NUM_FOODS = 5;

    // Variable for bouncing animation
    private double bouncePhase = 0.0; // Phase for the bouncing animation

    // Coin sprite sheet dimensions
    private int currentCoinFrame = 0;
    private long lastFrameUpdateTime = 0;
    private final long frameDuration = 100; // Duration for each coin frame in milliseconds

    // Hero stats
    private int heroLevel;
    private int heroXP;
    private int xpToNextLevel;
    private int heroX, heroY;
    private int heroHealth;
    private int heroMaxHealth;
    private int coinCount;

    // State flags for smooth movement
    private boolean upPressed, downPressed, leftPressed, rightPressed;

    // Timer for controlling movement speed
    private long lastMoveTime = 0;
    private final int BASE_MOVE_COOLDOWN = 150;

    // Upgrade and Power-Up Stats
    private int attackLevel;
    private int defenseLevel;
    private int evasivenessLevel;
    private int healthUpgradeLevel;

    private long critBoostEndTime = 0;
    private long speedBoostEndTime = 0;
    private long shieldBoostEndTime = 0;

    // Costs for levels 2, 3, 4, and 5. Level 1 is the base
    private final int[] upgradeCosts = {3, 8, 15, 25};

    // Camera position
    private int cameraX, cameraY;
    
    // Game objects and state
    private Random random;
    private List<Point> rocks;
    private List<Point> stumps;
    private List<Enemy> enemies;
    private final List<DisplayMessage> activeMessages = new ArrayList<>();
    private List<BufferedImage> coinFrames = new ArrayList<>();
    private List<Point> coins;
    private List<Point> foods;

    // Images
    private BufferedImage heroImage;
    private BufferedImage enemyImage;
    private BufferedImage rockImage;
    private BufferedImage stumpImage;
    private BufferedImage coinSpriteSheet;
    private BufferedImage foodImage;
    private BufferedImage marketIcon;
    private BufferedImage grassTexture;
    private Rectangle marketIconBounds;

    // Game State Management
    public enum GameState {
        NORMAL,
        BOSS_FIGHT_PENDING,
        BOSS_FIGHT_ACTIVE,
        POST_BOSS
    }
    private GameState currentGameState;
    private int level20EnemiesDefeated;

    // --- Screen Shake Variables ---
    private boolean isShaking = false;
    private long shakeEndTime = 0;

    private Thread gameThread;
    private volatile boolean running = false;
    private final int FPS = 60;

    // Constructor
    public HeroGamePanel(HeroGame game) {
        this.game = game;
        this.random = new Random();
        this.setPreferredSize(new Dimension(NATIVE_SCREEN_WIDTH, NATIVE_SCREEN_HEIGHT));
        this.setBackground(new Color(82, 100, 29));
        this.setFocusable(true);
        this.addKeyListener(new MyKeyAdapter());

        // Add Mouse Listener for Market ---
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Check if the click was inside the market icon's bounds
                if (marketIconBounds != null && marketIconBounds.contains(e.getPoint())) {
                    showMarket();
                }
            }
        });
        
        // Initialize lists
        this.rocks = new ArrayList<>();
        this.stumps = new ArrayList<>();
        this.enemies = new ArrayList<>();
        this.coins = new ArrayList<>();
        this.foods = new ArrayList<>();

        loadImages();
    }

    // --- Game State Methods ---

    public void startGame() {
        activeMessages.clear();
        rocks.clear();
        stumps.clear();
        enemies.clear();
        coins.clear();
        foods.clear();
        
        generateObstacles();

        // Generate coins
        for (int i = 0; i < NUM_COINS; i++) {
            Point coin = new Point();
            teleportCoin(coin);
            coins.add(coin);
        }

        // Generate foods
        for (int i = 0; i < NUM_FOODS; i++) {
            Point food = new Point();
            teleportFood(food);
            foods.add(food);
        }

        for (int i = 0; i < NUM_ENEMIES; i++) {
            enemies.add(new Enemy(this));
        }

        do {
            heroX = (NATIVE_SCREEN_WIDTH / 2 / UNIT_SIZE) * UNIT_SIZE;
            heroY = (NATIVE_SCREEN_HEIGHT / 2 / UNIT_SIZE) * UNIT_SIZE;
        } while (isObstacle(heroX, heroY));

        this.heroLevel = 1;
        this.heroXP = 0;
        this.xpToNextLevel = 100;
        this.heroMaxHealth = 10;
        this.heroHealth = this.heroMaxHealth;
        this.coinCount = 0;
        this.attackLevel = 1;
        this.defenseLevel = 1;
        this.evasivenessLevel = 1;
        this.healthUpgradeLevel = 1;
        this.currentGameState = GameState.NORMAL;
        this.level20EnemiesDefeated = 0;

        updateCamera();
    }

    public void saveGame() {
        GameStateData data = new GameStateData();
        data.heroX = this.heroX;
        data.heroY = this.heroY;
        data.heroLevel = this.heroLevel;
        data.heroXP = this.heroXP;
        data.xpToNextLevel = this.xpToNextLevel;
        data.heroHealth = this.heroHealth;
        data.heroMaxHealth = this.heroMaxHealth;
        data.coinCount = this.coinCount; // FIXED: Save coin count
        data.enemies = this.enemies;
        data.rocks = this.rocks;
        data.stumps = this.stumps;
        data.coins = this.coins;
        data.foods = this.foods; // FIXED: Save foods
        data.attackLevel = this.attackLevel;
        data.defenseLevel = this.defenseLevel;
        data.evasivenessLevel = this.evasivenessLevel;
        data.healthUpgradeLevel = this.healthUpgradeLevel;
        data.currentGameState = this.currentGameState;
        data.level20EnemiesDefeated = this.level20EnemiesDefeated;
        data.cameraX = this.cameraX;
        java.io.File savesDir = new java.io.File("saves");
        if (!savesDir.exists()) {
            savesDir.mkdirs(); // This creates the directory if it's missing
        }
        JFileChooser fileChooser = new JFileChooser(savesDir);
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            java.io.File file = fileChooser.getSelectedFile();
            try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(new java.io.FileOutputStream(file))) {
                oos.writeObject(data);
                displayMessage("Game Saved!");
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error saving game!", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public boolean loadGame() {
        java.io.File savesDir = new java.io.File("saves");
        if (!savesDir.exists()) {
            savesDir.mkdirs(); // This creates the directory if it's missing
        }
        // Keep the old synchronous loader for API compatibility, but implement a safer async loader below.
        JFileChooser fileChooser = new JFileChooser(savesDir);
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            final java.io.File file = fileChooser.getSelectedFile();

            // Show a small non-blocking loading dialog while we deserialize off the EDT
            final JDialog loadingDialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Loading...", Dialog.ModalityType.MODELESS);
            JProgressBar bar = new JProgressBar();
            bar.setIndeterminate(true);
            bar.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
            loadingDialog.getContentPane().add(bar);
            loadingDialog.pack();
            loadingDialog.setLocationRelativeTo(this);

            Thread loader = new Thread(() -> {
                try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(new java.io.FileInputStream(file))) {
                    GameStateData data = (GameStateData) ois.readObject();

                    // Defensive defaults for missing fields in older saves
                    List<Enemy> loadedEnemies = (data.enemies != null) ? data.enemies : new ArrayList<Enemy>();
                    List<Point> loadedRocks = (data.rocks != null) ? data.rocks : new ArrayList<Point>();
                    List<Point> loadedStumps = (data.stumps != null) ? data.stumps : new ArrayList<Point>();
                    List<Point> loadedCoins = (data.coins != null) ? data.coins : new ArrayList<Point>();
                    List<Point> loadedFoods = (data.foods != null) ? data.foods : new ArrayList<Point>();
                    HeroGamePanel.GameState loadedState = (data.currentGameState != null) ? data.currentGameState : GameState.NORMAL;

                    // Apply loaded state back on the EDT
                    SwingUtilities.invokeLater(() -> {
                        this.heroX = data.heroX;
                        this.heroY = data.heroY;
                        this.heroLevel = data.heroLevel;
                        this.heroXP = data.heroXP;
                        this.xpToNextLevel = data.xpToNextLevel;
                        this.heroHealth = data.heroHealth;
                        this.heroMaxHealth = data.heroMaxHealth;
                        this.coinCount = data.coinCount;

                        this.enemies = loadedEnemies;
                        this.rocks = loadedRocks;
                        this.stumps = loadedStumps;
                        this.coins = loadedCoins;
                        this.foods = loadedFoods;

                        this.attackLevel = data.attackLevel;
                        this.defenseLevel = data.defenseLevel;
                        this.evasivenessLevel = data.evasivenessLevel;
                        this.healthUpgradeLevel = data.healthUpgradeLevel;
                        this.currentGameState = loadedState;
                        this.level20EnemiesDefeated = data.level20EnemiesDefeated;
                        this.cameraX = data.cameraX;
                        this.cameraY = data.cameraY;

                        // Reconnect transient fields
                        for (Enemy enemy : enemies) {
                            enemy.panel = this;
                        }

                        activeMessages.clear();
                        updateCamera();
                        repaint();
                        loadingDialog.dispose();
                    });

                } catch (IOException | ClassNotFoundException e) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, "Error loading game file!", "Error", JOptionPane.ERROR_MESSAGE);
                        loadingDialog.dispose();
                    });
                    e.printStackTrace();
                }
            }, "SaveFileLoader");

            // Show the dialog and start loader thread
            loadingDialog.setVisible(true);
            loader.start();

            // We return true here because the user selected a file and load has started.
            // The actual completion happens asynchronously.
            return true;
        }
        return false;
    }

    /**
     * New async entry point for loading that callers can use when they don't need an immediate boolean result.
     * This keeps the UI responsive while heavy deserialization happens off the EDT.
     */
    public void loadGameAsync() {
        // Reuse existing synchronous entrypoint which we've implemented to start a background loader.
        loadGame();
    }

    // --- Game Logic Methods ---

    private void loadImages() {
        try {
            heroImage = ImageIO.read(getClass().getResource("/resources/hero_image.png"));
            enemyImage = ImageIO.read(getClass().getResource("/resources/enemy_image.png"));
            rockImage = ImageIO.read(getClass().getResource("/resources/rock.png"));
            stumpImage = ImageIO.read(getClass().getResource("/resources/stump.png"));
            coinSpriteSheet = ImageIO.read(getClass().getResource("/resources/coin.png"));
            foodImage = ImageIO.read(getClass().getResource("/resources/food.png"));
            marketIcon = ImageIO.read(getClass().getResource("/resources/market.png"));
            grassTexture = ImageIO.read(getClass().getResource("/resources/grass.png"));

            // Cut the coin sprite sheet into frames
            if (coinSpriteSheet != null) {
                int frameWidth = 120;  // The width of one coin frame in pixels
                int frameHeight = 120; // The height of one coin frame
                int numFrames = 8;    // The number of frames in your sheet

                for (int i = 0; i < numFrames; i++) {
                    coinFrames.add(coinSpriteSheet.getSubimage(i * frameWidth, 0, frameWidth, frameHeight));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error loading one or more images.");
            heroImage = enemyImage = rockImage = stumpImage = coinSpriteSheet = null;
        }
    }

    private void teleportFood(Point food) {
        int nextX, nextY;
        do {
            nextX = random.nextInt(WORLD_WIDTH / UNIT_SIZE) * UNIT_SIZE;
            nextY = random.nextInt(WORLD_HEIGHT / UNIT_SIZE) * UNIT_SIZE;
        } while (isObstacle(nextX, nextY) || (nextX == heroX && nextY == heroY));

        food.setLocation(nextX, nextY);
    }

    private void teleportCoin(Point coin) {
        int nextX, nextY;
        do {
            nextX = random.nextInt(WORLD_WIDTH / UNIT_SIZE) * UNIT_SIZE;
            nextY = random.nextInt(WORLD_HEIGHT / UNIT_SIZE) * UNIT_SIZE;
        } while (isObstacle(nextX, nextY) || (nextX == heroX && nextY == heroY));

        coin.setLocation(nextX, nextY);
    }

    // For the animation of the coin sprite
    private void updateAnimation() {
        long currentTime = System.currentTimeMillis();
        // Check if enough time has passed to show the next frame
        if (currentTime - lastFrameUpdateTime > frameDuration) {
            currentCoinFrame++; // Move to the next frame
            if (currentCoinFrame >= coinFrames.size()) {
                currentCoinFrame = 0; // Loop back to the first frame
            }
            lastFrameUpdateTime = currentTime; // Reset the timer
        }
    }

    private void generateObstacles() {
        for (int i = 0; i < NUM_ROCKS; i++) {
            int x, y;
            do {
                x = random.nextInt(WORLD_WIDTH / UNIT_SIZE) * UNIT_SIZE;
                y = random.nextInt(WORLD_HEIGHT / UNIT_SIZE) * UNIT_SIZE;
            } while (isObstacle(x, y));
            rocks.add(new Point(x, y));
        }
        for (int i = 0; i < NUM_STUMPS; i++) {
            int x, y;
            do {
                x = random.nextInt(WORLD_WIDTH / UNIT_SIZE) * UNIT_SIZE;
                y = random.nextInt(WORLD_HEIGHT / UNIT_SIZE) * UNIT_SIZE;
            } while (isObstacle(x, y));
            stumps.add(new Point(x, y));
        }
    }

    private boolean isObstacle(int x, int y) {
        Point target = new Point(x, y);
        return rocks.contains(target) || stumps.contains(target);
    }

    public void checkCollisions() {
        // --- Enemy Collision ---
        Enemy collidedEnemy = null;
        for (Enemy enemy : enemies) {
            if (heroX == enemy.x && heroY == enemy.y) {
                collidedEnemy = enemy;
                break;
            }
        }

        if (collidedEnemy != null) {
            // --- STEP 1: ENEMY ATTACKS FIRST ---
            double evasionChance = (evasivenessLevel - 1) * 0.14;
            if (random.nextDouble() > evasionChance) { // Check if the hero dodges
                if (System.currentTimeMillis() < shieldBoostEndTime) { // Check if the shield blocks
                    displayMessage("Shield blocked!");
                } else {
                    // If not dodged or shielded, the hero takes damage.
                    int damageTaken = Math.max(1, collidedEnemy.level - (this.defenseLevel - 1));
                    this.heroHealth -= damageTaken;
                    displayMessage("Ouch! -" + damageTaken + " HP");
                    
                    // Check if the hero died from the hit.
                    if (heroHealth <= 0) {
                        displayMessage("You died! Resetting...");
                        heroHealth = heroMaxHealth;
                        coinCount = 0;
                        // ... other death penalties ...
                        heroX = (NATIVE_SCREEN_WIDTH / 2 / UNIT_SIZE) * UNIT_SIZE;
                        heroY = (NATIVE_SCREEN_HEIGHT / 2 / UNIT_SIZE) * UNIT_SIZE;
                        updateCamera();
                        return; // EXIT apon death. The hero does not get to counter-attack.
                    }
                }
            } else {
                displayMessage("Dodged!");
            }

            // --- STEP 2: HERO COUNTER-ATTACKS (only if the hero survived) ---
            int damageDealt = 1 + (this.attackLevel - 1);
            if (System.currentTimeMillis() < critBoostEndTime && random.nextDouble() < 0.25) {
                damageDealt *= 2;
                displayMessage("Critical Hit!");
            }
            
            boolean didDie = collidedEnemy.takeDamage(damageDealt);

            if (didDie) {
                // Grant rewards for the kill
                System.out.println("DEBUG: Enemy Died! Level: " + collidedEnemy.level + ", GameState: " + currentGameState);
                int xpGained = collidedEnemy.level * 25;
                addXP(xpGained);
                int coinsDropped = (collidedEnemy.level <= 10) ? 1 : (collidedEnemy.level <= 15 ? 2 : 3);
                this.coinCount += coinsDropped;
                displayMessage("+" + coinsDropped + " Coin(s)!");

                // Decide what happens to the defeated enemy's spot
                boolean removePermanently = false;
                if (collidedEnemy.level == 25 && currentGameState == GameState.BOSS_FIGHT_ACTIVE) {
                    displayMessage("You defeated the boss!");
                    currentGameState = GameState.POST_BOSS;
                    removePermanently = true;
                    for (int i = 0; i < NUM_ENEMIES; i++) enemies.add(new Enemy(this));
                } else if (collidedEnemy.level == 20 && currentGameState == GameState.NORMAL) {
                    level20EnemiesDefeated++;
                    removePermanently = true;
                    if (level20EnemiesDefeated >= 4) {
                        currentGameState = GameState.BOSS_FIGHT_PENDING;
                    }
                }
                
                // Execute the decision
                if (removePermanently) {
                    enemies.remove(collidedEnemy);
                } else {
                    collidedEnemy.respawn();
                }

            } else { // Enemy survived the hit
                collidedEnemy.teleportNearby();
            }
        }
        // Coin collision
        Point collectedCoin = null;
        for (Point coin : coins) {
            if (heroX == coin.x && heroY == coin.y) {
                collectedCoin = coin;
                break;
            }
        }
        if (collectedCoin != null) {
            this.coinCount++;
            displayMessage("+1 Coin!");
            teleportCoin(collectedCoin);
        }

        // Food collision
        Point collectedFood = null;
        for (Point food : foods) {
            if (heroX == food.x && heroY == food.y) {
                collectedFood = food;
                break;
            }
        }
        if (collectedFood != null) {
            heroHealth += 10; // Heal the hero
            // Clamp health so it doesn't go over the max
            heroHealth = Math.min(heroHealth, heroMaxHealth); 
            displayMessage("+10 HP!");
            teleportFood(collectedFood); // Move the food to a new spot
        }
    }

    public void addXP(int amount) {
        heroXP += amount;
        displayMessage("+" + amount + " XP");
        while (heroXP >= xpToNextLevel) {
            levelUp();
        }
    }

    public void addMaxHealth(int amount) {
        heroMaxHealth += amount;
        heroHealth += amount;
        displayMessage("Max Health increased by " + amount);
    }

    private void levelUp() {
        heroLevel++;
        heroXP -= xpToNextLevel;
        addMaxHealth(5);
        xpToNextLevel *= 1.5;
        displayMessage("LEVEL UP!");
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
        if (nextX >= 0 && nextX < WORLD_WIDTH && 
            nextY >= 0 && nextY < WORLD_HEIGHT &&
            !isObstacle(nextX, nextY)) {
            heroX = nextX;
            heroY = nextY;
        }
    }

    private void updateCamera() {
        cameraX = heroX - (NATIVE_SCREEN_WIDTH / 2);
        cameraY = heroY - (NATIVE_SCREEN_HEIGHT / 2);
        cameraX = Math.max(0, Math.min(cameraX, WORLD_WIDTH - NATIVE_SCREEN_WIDTH));
        cameraY = Math.max(0, Math.min(cameraY, WORLD_HEIGHT - NATIVE_SCREEN_HEIGHT));
    }
    
    // --- Drawing and UI Methods ---

    @Override
    protected void paintComponent(Graphics g) {
        int coinDrawSize = UNIT_SIZE / 2;
        int offset = (UNIT_SIZE - coinDrawSize) / 2;

        // --- Calculate screen shake offset ---
        int shakeOffsetX = 0;
        int shakeOffsetY = 0;
        if (isShaking) {
            int shakeIntensity = 8; // How far the screen will shake in pixels
            shakeOffsetX = (random.nextInt(shakeIntensity * 2) - shakeIntensity);
            shakeOffsetY = (random.nextInt(shakeIntensity * 2) - shakeIntensity);
        }

        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        AffineTransform originalTransform = g2d.getTransform();

        // --- Scaling Logic ---
        double scaleX = (double) getWidth() / NATIVE_SCREEN_WIDTH;
        double scaleY = (double) getHeight() / NATIVE_SCREEN_HEIGHT;
        double scale = Math.min(scaleX, scaleY);
        int offsetX = (getWidth() - (int) (NATIVE_SCREEN_WIDTH * scale)) / 2;
        int offsetY = (getHeight() - (int) (NATIVE_SCREEN_HEIGHT * scale)) / 2;
        g2d.translate(offsetX, offsetY);
        g2d.scale(scale, scale);

        // --- World and Object Drawing ---
        g2d.translate(-cameraX + shakeOffsetX, -cameraY + shakeOffsetY);
        // Draw the background as a solid green color (do not use grass texture)
        g2d.setColor(new Color(82, 100, 29));
        g2d.fillRect(0, 0, WORLD_WIDTH, WORLD_HEIGHT);

        for (Point rockPos : rocks) {
            if (rockImage != null) g2d.drawImage(rockImage, rockPos.x, rockPos.y, UNIT_SIZE, UNIT_SIZE, null);
        }
        for (Point stumpPos : stumps) {
            if (stumpImage != null) g2d.drawImage(stumpImage, stumpPos.x, stumpPos.y, UNIT_SIZE, UNIT_SIZE, null);
        }
        for (Enemy enemy : enemies) {
            if (enemyImage != null) g2d.drawImage(enemyImage, enemy.x, enemy.y, UNIT_SIZE, UNIT_SIZE, null);
            drawEnemyUI(g2d, enemy);
        }
        if (heroImage != null) g2d.drawImage(heroImage, heroX, heroY, UNIT_SIZE, UNIT_SIZE, null);

        // Draw shield effect if active
        if (System.currentTimeMillis() < shieldBoostEndTime) {
            // Create a semi-transparent blue color
            g2d.setColor(new Color(0, 150, 255, 70));
            g2d.fillOval(heroX - 10, heroY - 10, UNIT_SIZE + 20, UNIT_SIZE + 20); // Draw a larger circle around the hero
        }

        // --- Coin Drawing ---
        if (!coinFrames.isEmpty()) {
            for (Point coinPos : coins) {
                // Draw the current animation frame at the coin's position
                g2d.drawImage(coinFrames.get(currentCoinFrame), coinPos.x + offset, coinPos.y + offset, (UNIT_SIZE / 2), (UNIT_SIZE / 2), null);
            }
        }

        // --- Food Drawing ---
        if (foodImage != null) {
            // Calculate the vertical bounce offset using a sine wave
            int bounceOffset = (int) (Math.sin(bouncePhase) * 4); // 4 is the bounce height in pixels
            for (Point foodPos : foods) {
                g2d.drawImage(foodImage, foodPos.x, foodPos.y + bounceOffset, UNIT_SIZE, UNIT_SIZE, null);
            }
        }

        // --- UI Drawing ---
        drawActiveMessages(g2d);
        
        drawHud(g2d);
        g2d.setTransform(originalTransform);

        // Draw the market icon in a fixed position ---
        if (marketIcon != null) {
            int iconSize = 48;
            // Set the clickable bounds right before drawing
            marketIconBounds = new Rectangle(15, 15, iconSize, iconSize);
            g2d.drawImage(marketIcon, marketIconBounds.x, marketIconBounds.y, marketIconBounds.width, marketIconBounds.height, null);
        }

        // Draw the boss counter
        drawBossCounter(g2d);
    }
    
    private void drawEnemyUI(Graphics2D g2d, Enemy enemy) {
        final int barWidth = 40, barHeight = 7, yOffset = 15;
        int barX = enemy.x + (UNIT_SIZE / 2) - (barWidth / 2);
        int barY = enemy.y - yOffset;

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        g2d.drawString("Lvl " + enemy.level, barX - 35, barY + barHeight);
        
        g2d.setColor(Color.DARK_GRAY);
        g2d.fillRect(barX, barY, barWidth, barHeight);
        
        double healthPercentage = (double) enemy.currentHealth / enemy.maxHealth;
        g2d.setColor(Color.RED);

        g2d.fillRect(barX, barY, (int)(barWidth * healthPercentage), barHeight);
        
        g2d.setColor(Color.WHITE);
        g2d.drawRect(barX, barY, barWidth, barHeight);
    }

    private void drawHud(Graphics2D g2d) {
        // --- Define base positions and dimensions ---
        int hudX = cameraX + 15;
        int hudY = cameraY + NATIVE_SCREEN_HEIGHT - 40; // Base Y position near the bottom
        int barWidth = 120;
        int hpBarHeight = 14;
        int xpBarHeight = 7;

        // --- 1. Draw Hero's Level ---
        String levelText = "Lvl: " + heroLevel;
        g2d.setFont(new Font("Arial", Font.BOLD, 16));
        g2d.setColor(Color.WHITE);
        // Draw a shadow for better readability
        g2d.setColor(Color.BLACK);
        g2d.drawString(levelText, hudX + 1, hudY + 19);
        g2d.setColor(Color.WHITE);
        g2d.drawString(levelText, hudX, hudY + 18);

        // --- 2. Draw Health and XP Bars ---
        int barX = hudX + 65;
        // Health Bar
        double hpPercentage = (double) heroHealth / heroMaxHealth;
        g2d.setColor(Color.DARK_GRAY);
        g2d.fillRect(barX, hudY, barWidth, hpBarHeight);
        g2d.setColor(Color.GREEN);
        g2d.fillRect(barX, hudY, (int)(barWidth * hpPercentage), hpBarHeight);
        g2d.setColor(Color.WHITE);
        g2d.drawRect(barX, hudY, barWidth, hpBarHeight);

        // XP Bar (positioned below the HP bar)
        int xpBarY = hudY + hpBarHeight + 2;
        double xpPercentage = (double) heroXP / xpToNextLevel;
        g2d.setColor(Color.DARK_GRAY);
        g2d.fillRect(barX, xpBarY, barWidth, xpBarHeight);
        g2d.setColor(Color.CYAN);
        g2d.fillRect(barX, xpBarY, (int)(barWidth * xpPercentage), xpBarHeight);
        g2d.setColor(Color.WHITE);
        g2d.drawRect(barX, xpBarY, barWidth, xpBarHeight);

        // --- 3. Draw Coin Counter ---
        int coinX = barX + barWidth + 20; // Position to the right of the bars
        if (!coinFrames.isEmpty()) {
            // Use the first frame of the coin frame for the icon
            g2d.drawImage(coinFrames.get(0), coinX, hudY + 2, UNIT_SIZE / 2, UNIT_SIZE / 2, null);            
            g2d.setFont(new Font("Arial", Font.BOLD, 18));
            g2d.setColor(Color.BLACK);
            g2d.drawString("x " + coinCount, coinX + 30, hudY + 21); // Shadow
            g2d.setColor(Color.YELLOW);
            g2d.drawString("x " + coinCount, coinX + 29, hudY + 20);
        }
    }
        
    public void displayMessage(String text) {
        activeMessages.add(new DisplayMessage(text));
    }

    private void drawActiveMessages(Graphics2D g2d) {
        final int baseYOffset = 30, lineHeight = 15;
        int linesUp = 0;
        
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        
        for (int i = activeMessages.size() - 1; i >= 0; i--) {
            DisplayMessage message = activeMessages.get(i);
            if (System.currentTimeMillis() > message.endTime) {
                activeMessages.remove(i);
                continue;
            }
            FontMetrics fm = g2d.getFontMetrics();
            int messageWidth = fm.stringWidth(message.text);
            int messageX = heroX + (UNIT_SIZE / 2) - (messageWidth / 2);
            int messageY = heroY - baseYOffset - (linesUp * lineHeight);

            g2d.setColor(Color.BLACK);
            g2d.drawString(message.text, messageX + 1, messageY + 1); // Shadow
            g2d.setColor(Color.YELLOW);
            g2d.drawString(message.text, messageX, messageY);

            linesUp++;
        }
    }

    private void showInGameMenu() {
        Object[] options = {"Save Game", "Exit to Title", "Resume"};
        int choice = JOptionPane.showOptionDialog(game, "Game Paused", "Menu",
            JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[2]);

        if (choice == 0) { // Save Game
            saveGame();
        } else if (choice == 1) { // Exit to Title
            // --- NEW: Add a confirmation dialog before exiting to title ---
            int exitChoice = JOptionPane.showConfirmDialog(
                game,
                "Do you want to save your game before returning to the title screen?",
                "Exit to Title",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );

            if (exitChoice == JOptionPane.YES_OPTION) {
                saveGame();
                game.showTitleScreen();
            } else if (exitChoice == JOptionPane.NO_OPTION) {
                game.showTitleScreen();
            }
            // If the user chooses "Cancel", the dialog closes and they remain in the paused game.
        }
        // If choice is 2 (Resume), do nothing.
    }

    // --- Inner Classes ---

    public class MyKeyAdapter extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            int keyCode = e.getKeyCode();

            switch (keyCode) {
                case KeyEvent.VK_W: case KeyEvent.VK_UP: upPressed = true; break;
                case KeyEvent.VK_S: case KeyEvent.VK_DOWN: downPressed = true; break;
                case KeyEvent.VK_A: case KeyEvent.VK_LEFT: leftPressed = true; break;
                case KeyEvent.VK_D: case KeyEvent.VK_RIGHT: rightPressed = true; break;
                case KeyEvent.VK_ESCAPE: showInGameMenu(); break;
            }
        }

        @Override
        public void keyReleased(KeyEvent e) {
            int keyCode = e.getKeyCode();

            switch (keyCode) {
                case KeyEvent.VK_W: case KeyEvent.VK_UP: upPressed = false; break;
                case KeyEvent.VK_S: case KeyEvent.VK_DOWN: downPressed = false; break;
                case KeyEvent.VK_A: case KeyEvent.VK_LEFT: leftPressed = false; break;
                case KeyEvent.VK_D: case KeyEvent.VK_RIGHT: rightPressed = false; break;
            }
        }
    }
    
    public static class Enemy implements Serializable {
        private static final long serialVersionUID = 1L;
        int x, y, level, currentHealth, maxHealth;

        private transient HeroGamePanel panel;

        Enemy(HeroGamePanel panel) {
            this.panel = panel;
            this.level = 0;
            respawn();
        }

        public boolean takeDamage(int amount) {
            this.currentHealth -= amount;
            return this.currentHealth <= 0;
        }

        // In the Enemy inner class
        public void respawn() {
            this.level++;
            // For post-boss gameplay, make sure enemies respawn at a high level
            if (panel.currentGameState == GameState.POST_BOSS && this.level <= 20) {
                this.level = 21;
            }

            this.maxHealth = this.level;
            this.currentHealth = this.maxHealth;
            teleportAnywhere();
        }

        // Teleports the enemy within a 5-unit radius of the hero, avoiding obstacles and the hero's position.
        public void teleportNearby() {
            int nextX, nextY;
            int attempts = 0; // Limit attempts to avoid infinite loops

            do {
                // Generate a random offset from -5 to +5 for x and y
                int dx = (panel.random.nextInt(11) - 5) * UNIT_SIZE;
                int dy = (panel.random.nextInt(11) - 5) * UNIT_SIZE;

                nextX = this.x + dx;
                nextY = this.y + dy;

                attempts++;
                // If we can't find a spot after 50 attempts, just teleport anywhere
                if (attempts > 50) {
                    teleportAnywhere();
                    return;
                }
                } while (
                    (nextX == this.x && nextY == this.y) || // Don't teleport to the same spot
                    panel.isObstacle(nextX, nextY) || // Don't land on an obstacle
                    nextX < UNIT_SIZE || nextX >= WORLD_WIDTH - UNIT_SIZE || // Don't go out of bounds. They won't spawn on the edges.
                    nextY < UNIT_SIZE || nextY >= WORLD_HEIGHT - UNIT_SIZE // Don't go out of bounds. They won't spawn on the edges.
                );

                // A valid nearby spot was found, so update the enemy's position
                this.x = nextX;
                this.y = nextY;
        }

        public void teleportAnywhere() {
            int nextX, nextY;
            // Ensure the enemy spawns at least 1 unit away from the edges
            do {
                int validWidthUnits = (WORLD_WIDTH / UNIT_SIZE) - 2;
                int validHeightUnits = (WORLD_HEIGHT / UNIT_SIZE) - 2;

                nextX = (panel.random.nextInt(validWidthUnits) + 1) * UNIT_SIZE;
                nextY = (panel.random.nextInt(validHeightUnits) + 1) * UNIT_SIZE;
            } while (panel.isObstacle(nextX, nextY) || (nextX == panel.heroX && nextY == panel.heroY));

            this.x = nextX;
            this.y = nextY;
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

    // This method is called automatically when the panel is added to the frame.
    // It's the perfect place to start our game thread.
    @Override
    public void addNotify() {
        super.addNotify();
        startGameThread();
    }

    public void startGameThread() {
        running = true;
        gameThread = new Thread(this);
        gameThread.start();
    }

    @Override
    public void run() {
        double drawInterval = 1000000000.0 / FPS;
        double delta = 0;
        long lastTime = System.nanoTime();
        long currentTime;

        while (running) {
            currentTime = System.nanoTime();
            delta += (currentTime - lastTime) / drawInterval;
            lastTime = currentTime;

            if (delta >= 1) {
                update();  // Update game logic (like animations)
                repaint(); // Redraw the screen
                delta--;
            }
        }
    }

    // This new method contains all logic that needs to run continuously.
    public void update() {
        updateAnimation();
        bouncePhase += 0.1;

        // --- Handle hero movement based on held keys and speed ---
        int currentMoveCooldown = (System.currentTimeMillis() < speedBoostEndTime) ? BASE_MOVE_COOLDOWN / 2 : BASE_MOVE_COOLDOWN;
        if (System.currentTimeMillis() - lastMoveTime > currentMoveCooldown) {
            if (upPressed) moveHero('U');
            else if (downPressed) moveHero('D');
            else if (leftPressed) moveHero('L');
            else if (rightPressed) moveHero('R');
            
            // If any move was made, reset the timer and check for collisions
            if (upPressed || downPressed || leftPressed || rightPressed) {
                lastMoveTime = System.currentTimeMillis();
                checkCollisions();
                updateCamera();
            }
        }

        // --- Check for expired power-ups ---
        if (critBoostEndTime > 0 && System.currentTimeMillis() > critBoostEndTime) {
            critBoostEndTime = 0;
            displayMessage("Critical Boost wore off!");
        }
        if (speedBoostEndTime > 0 && System.currentTimeMillis() > speedBoostEndTime) {
            speedBoostEndTime = 0;
            displayMessage("Speed Boost wore off!");
        }
        if (shieldBoostEndTime > 0 && System.currentTimeMillis() > shieldBoostEndTime) {
            shieldBoostEndTime = 0;
            displayMessage("Shield wore off!");
        }

        // --- Boss Spawn Logic ---
        // Check if it's time to start the boss sequence ---
        if (currentGameState == GameState.BOSS_FIGHT_PENDING) {
            triggerBossSequence();
        }

        // Check if the screen shake should end
        if (isShaking && System.currentTimeMillis() > shakeEndTime) {
            isShaking = false;
        }
    }

    // --- This method starts the shake and spawns the boss ---
    private void triggerBossSequence() {
        displayMessage("The ground rumbles violently!");
        // Start the screen shake for 1.5 seconds
        isShaking = true;
        shakeEndTime = System.currentTimeMillis() + 1500;

        spawnBoss(); // Spawn the boss while the screen is shaking
        currentGameState = GameState.BOSS_FIGHT_ACTIVE;
    }

    // --- Method to create the boss ---
    private void spawnBoss() {
        displayMessage("A powerful enemy appears!");
        // Create a boss enemy - for now, it's just a high-level enemy.
        // You can create a new Boss class later for unique behavior.
        Enemy boss = new Enemy(this);
        boss.level = 25;
        boss.maxHealth = 250;
        boss.currentHealth = 250;
        // Place it in the center of the world
        boss.x = (WORLD_WIDTH / 2 / UNIT_SIZE) * UNIT_SIZE;
        boss.y = (WORLD_HEIGHT / 2 / UNIT_SIZE) * UNIT_SIZE;
        enemies.add(boss);
        
        currentGameState = GameState.BOSS_FIGHT_ACTIVE;
    }

    private void drawBossCounter(Graphics2D g2d) {
        // Only draw the counter if at least one level 20 enemy has been defeated
        // and the game is in the NORMAL state.
        if (level20EnemiesDefeated > 0 && currentGameState == GameState.NORMAL) {
            String text = "Enemies Defeated: " + level20EnemiesDefeated + "/4";
            g2d.setFont(new Font("Arial", Font.BOLD, 18));
            
            // Use FontMetrics to right-align the text
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(text);
            int x = NATIVE_SCREEN_WIDTH - textWidth - 20;
            int y = 35;

            // Draw a shadow for readability
            g2d.setColor(Color.BLACK);
            g2d.drawString(text, x + 1, y + 1);
            g2d.setColor(Color.ORANGE);
            g2d.drawString(text, x, y);
        }
    }

    // In HeroGamePanel.java
    private void showMarket() {
        Object[] options = {"Upgrades", "Consumables", "Cancel"};
        int choice = JOptionPane.showOptionDialog(game, "Welcome to the Market!", "Market",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[2]);

        if (choice == 0) { // Upgrades
            // --- These lines now use the helper method to check the correct max level ---
            String attackDesc = (attackLevel >= getMaxLevelFor("Attack")) ? "MAX" : (attackLevel >= 5 ? "15 Coins" : upgradeCosts[attackLevel - 1] + " Coins");
            String defenseDesc = (defenseLevel >= getMaxLevelFor("Defense")) ? "MAX" : (defenseLevel >= 5 ? "15 Coins" : upgradeCosts[defenseLevel - 1] + " Coins");
            String evasionDesc = (evasivenessLevel >= getMaxLevelFor("Evasiveness")) ? "MAX" : upgradeCosts[evasivenessLevel - 1] + " Coins";
            String healthDesc = (healthUpgradeLevel >= getMaxLevelFor("Health")) ? "MAX" : (healthUpgradeLevel >= 5 ? "15 Coins" : upgradeCosts[healthUpgradeLevel - 1] + " Coins");

            Object[] upgradeOptions = {
                "Attack (Lvl " + attackLevel + ") - " + attackDesc,
                "Defense (Lvl " + defenseLevel + ") - " + defenseDesc,
                "Evasiveness (Lvl " + evasivenessLevel + ") - " + evasionDesc,
                "Max Health (Lvl " + healthUpgradeLevel + ") - " + healthDesc,
                "Back"
            };
            
            int upgradeChoice = JOptionPane.showOptionDialog(game, "Choose an upgrade.", "Upgrades",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, upgradeOptions, upgradeOptions[4]);

            // --- THIS IS THE SWITCH STATEMENT THAT NEEDED FIXING ---
            // It checks which button was clicked and calls the appropriate purchase logic.
            switch (upgradeChoice) {
                // FIXED: Calls are now correct with only one argument
                case 0: purchaseUpgrade("Attack"); break;
                case 1: purchaseUpgrade("Defense"); break;
                case 2: purchaseUpgrade("Evasiveness"); break;
                case 3: purchaseUpgrade("Health"); break;
            }

        } else if (choice == 1) { // Consumables
        String foodOption = "Buy Food (+10 HP) - 5 Coins";
        String critOption = "Buy Critical Boost (30s) - 25 Coins";
        String speedOption = "Buy Speed Boost (60s) - 15 Coins";
        String shieldOption = "Buy Shield (15s) - 25 Coins";
        Object[] consumableOptions = {foodOption, critOption, speedOption, shieldOption, "Cancel"};

        int buyChoice = JOptionPane.showOptionDialog(game, "What would you like to buy?", "Consumables",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, consumableOptions, consumableOptions[4]);
        
        switch (buyChoice) {
            case 0: // Buy Food
                if (heroHealth >= heroMaxHealth) displayMessage("You are already at full health!");
                else if (coinCount < 5) displayMessage("Not enough coins!");
                else {
                    coinCount -= 5;
                    heroHealth = Math.min(heroHealth + 10, heroMaxHealth);
                    displayMessage("Healed for 10 HP!");
                }
                break;
            case 1: // Buy Crit Boost
                if (coinCount < 25) displayMessage("Not enough coins!");
                else {
                    coinCount -= 25;
                    critBoostEndTime = System.currentTimeMillis() + 30000; // 30 seconds
                    displayMessage("Critical Boost activated!");
                }
                break;
            case 2: // Buy Speed Boost
                if (coinCount < 15) displayMessage("Not enough coins!");
                else {
                    coinCount -= 15;
                    speedBoostEndTime = System.currentTimeMillis() + 60000; // 60 seconds
                    displayMessage("Speed Boost activated!");
                }
                break;
            case 3: // Buy Shield
                if (coinCount < 25) displayMessage("Not enough coins!");
                else {
                    coinCount -= 25;
                    shieldBoostEndTime = System.currentTimeMillis() + 15000; // 15 seconds
                    displayMessage("Shield activated!");
                }
                break;
            }
        }
    }

    // Add this new method to HeroGamePanel.java
    private int getMaxLevelFor(String upgradeName) {
        switch (upgradeName) {
            case "Attack":
            case "Defense":
            case "Health":
                // These can go up to level 20 only after the boss is beaten
                return (currentGameState == GameState.POST_BOSS) ? 20 : 5;
            case "Evasiveness":
            default:
                // Evasiveness is always capped at level 5
                return 5;
        }
    }

    private void purchaseUpgrade(String upgradeName) {
        int currentLevel;
        // NEW: This block determines the current level based on the upgrade's name
        switch (upgradeName) {
            case "Attack":      currentLevel = attackLevel;         break;
            case "Defense":     currentLevel = defenseLevel;        break;
            case "Evasiveness": currentLevel = evasivenessLevel;    break;
            case "Health":      currentLevel = healthUpgradeLevel;  break;
            default: return; // Exit if the name is invalid
        }

        int maxLevel = getMaxLevelFor(upgradeName);
        if (currentLevel >= maxLevel) {
            displayMessage(upgradeName + " is already max level!");
            return;
        }

        // --- Dynamic Cost Calculation ---
        int cost;
        if (currentLevel < 5) {
            cost = upgradeCosts[currentLevel - 1]; // Use array for early levels
        } else {
            cost = 15; // Flat cost for levels 5+
        }

        if (coinCount < cost) {
            displayMessage("Not enough coins!");
            return;
        }

        coinCount -= cost;
        String successMessage = "";

        switch (upgradeName) {
            case "Attack":
                attackLevel++;
                successMessage = "Attack upgraded to Lvl " + attackLevel + "!";
                break;
            case "Defense":
                defenseLevel++;
                successMessage = "Defense upgraded to Lvl " + defenseLevel + "!";
                break;
            case "Evasiveness":
                evasivenessLevel++;
                successMessage = "Evasion upgraded to Lvl " + evasivenessLevel + "!";
                break;
            case "Health":
                healthUpgradeLevel++;
                addMaxHealth(5);
                successMessage = "Max Health upgraded!";
                break;
        }
        displayMessage(successMessage);
    }


    // This is a special method to generate a world for screenshots. It uses the temporary key 'P', which is commented out in the key listener.
    public void generateWorldForScreenshot() {
        // Clear all game objects
        activeMessages.clear();
        enemies.clear();
        rocks.clear();
        stumps.clear();

        // Generate the obstacles
        generateObstacles();

        // Center the camera in the middle of the world
        cameraX = (WORLD_WIDTH - NATIVE_SCREEN_WIDTH) / 2;
        cameraY = (WORLD_HEIGHT - NATIVE_SCREEN_HEIGHT) / 2;

        // Redraw the panel
        repaint();
    }
}
