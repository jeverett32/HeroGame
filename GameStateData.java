import java.io.Serializable;
import java.util.List;
import java.awt.Point;

// This class holds all the data we need to save.
// It MUST implement Serializable.
public class GameStateData implements Serializable {
    // Required for serialization to work correctly
    private static final long serialVersionUID = 1L;

    // Hero data
    public int heroX, heroY;
    public int heroLevel;
    public int heroXP;
    public int xpToNextLevel;
    public int heroHealth;
    public int heroMaxHealth;
    public int coinCount;
    public int attackLevel;
    public int defenseLevel;
    public int evasivenessLevel;
    public int healthUpgradeLevel;


    // Add fields for game state
    public DonkeyGamePanel.GameState currentGameState;
    public int level20EnemiesDefeated;


    // World data
    public List<DonkeyGamePanel.Enemy> enemies;
    public List<Point> rocks;
    public List<Point> stumps;
    public List<Point> coins; // List of coin positions
    public List<Point> foods;

    public int cameraX, cameraY; // Camera position
}