import java.awt.*;
import javax.swing.*;
import java.awt.event.*;
import java.io.IOException;

/**
 * Main class for the hero adventure game.
 * This class initializes the game window and starts the game.
 */

// A comment for testing git

public class HeroGame extends JFrame {
    private CardLayout cardLayout; // CardLayout for switching between game states
    private JPanel mainPanel;
    private TitlePanel titlePanel;
    private HeroGamePanel gamePanel;
    private String currentScreen = "";


    public HeroGame() {
        setTitle("Donkey");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setResizable(true);

        // --- NEW: Set the window icon ---
        try {
            // Load the icon image from the resources folder
            java.awt.Image iconImage = javax.imageio.ImageIO.read(getClass().getResource("/resources/rock.png"));
            // Set it as the frame's icon
            setIconImage(iconImage);
        } catch (IOException e) {
            e.printStackTrace(); // Print an error if the icon can't be found
    }

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        gamePanel = new HeroGamePanel(this); // Pass a reference to this frame
        titlePanel = new TitlePanel(this); // Create the title panel


        // Add panels to the card layout
        mainPanel.add(titlePanel, "TITLE");
        mainPanel.add(gamePanel, "GAME");

        add(mainPanel); // Add the main panel to the frame

        // Handle the window closing event
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                promptToSaveOnExit();
            }
        });

        pack();
        setLocationRelativeTo(null); // Center the window on the screen
        showTitleScreen(); // Show the title screen first
    }

    public void showTitleScreen() {
        cardLayout.show(mainPanel, "TITLE");
        this.currentScreen = "TITLE"; // Update the current screen state
    }

    public void startGame() {
        gamePanel.startGame(); // Start the game logic
        cardLayout.show(mainPanel, "GAME");
        this.currentScreen = "GAME"; // Update the current screen state
        gamePanel.requestFocusInWindow(); // Request focus for key events
    }

    public void loadGame() {
    // Start loading asynchronously to avoid freezing the UI. Switch to the game card immediately
    // so the user sees progress and the loaded state when it completes.
    gamePanel.loadGameAsync();
    cardLayout.show(mainPanel, "GAME");
    this.currentScreen = "GAME";
    gamePanel.requestFocusInWindow();
    }

    // New method to handle prompting the user before quitting
    private void promptToSaveOnExit() {
        // Check the state variable instead of calling a non-existent method
        if (!currentScreen.equals("GAME")) {
            System.exit(0); // If not in the game, exit immediately
        } else {
            // The dialog and the logic to handle its result are now in the same block
            int choice = JOptionPane.showConfirmDialog(
                this,
                "Do you want to save your game before exiting?",
                "Exit Game",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );

            if (choice == JOptionPane.YES_OPTION) {
                gamePanel.saveGame();
                System.exit(0);
            } else if (choice == JOptionPane.NO_OPTION) {
                System.exit(0);
            }
            // If CANCEL_OPTION or the dialog is closed, do nothing.
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            HeroGame game = new HeroGame();
            game.setVisible(true);
        });
    }
}
