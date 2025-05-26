import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.animation.TranslateTransition;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

public class GameController {
    private static final int ROWS = 6;
    private static final int COLS = 7;

    private Button[][] boardButtons = new Button[ROWS][COLS];
    private Button endTurnButton;
    private NetworkClient client;
    private boolean myTurn = false;
    private boolean hasPlacedPiece = false;  // Track if player placed piece this turn
    private String myColor;      // "red" or "yellow"
    private String opponentColor = null;
    private boolean amPlayer1;
    private int winPieces;       // Nº de peças para ganhar (ex: 4)

    // Novo construtor com winPieces e cores
    public GameController(NetworkClient client, boolean amPlayer1, String myColor, int winPieces) {
    this.client = client;
    this.amPlayer1 = amPlayer1;
    this.myColor = myColor.toLowerCase();
    this.winPieces = winPieces;
    this.myTurn = amPlayer1;
}
    public void startGame(Stage stage) {
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(5);
        grid.setVgap(5);

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                Button cell = new Button(" ");
                cell.setMinSize(50, 50);
                cell.setPrefSize(50, 50);
                cell.setMaxSize(50, 50);
                cell.setShape(new Circle(25)); // always circular
                cell.setStyle("-fx-background-radius: 25; -fx-background-color: lightgray; -fx-border-color: black; -fx-border-radius: 25;");
                final int finalCol = col;
                cell.setOnAction(e -> {
                    if (myTurn && !hasPlacedPiece) {
                        boolean placed = placePiece(finalCol);
                        if (placed) {
                            hasPlacedPiece = true;
                            endTurnButton.setDisable(false);
                            disableBoard();
                        }
                    }
                });
                boardButtons[row][col] = cell;
                grid.add(cell, col, row);
            }
        }

        endTurnButton = new Button("Terminar jogada");
        endTurnButton.setDisable(true);
        endTurnButton.setOnAction(e -> {
            if (hasPlacedPiece) {
                myTurn = false;
                hasPlacedPiece = false;
                endTurnButton.setDisable(true);
                disableBoard();
                client.sendMessage("END_TURN");
            }
        });

        Label colorLabel = new Label("A tua cor é o " + translateColor(myColor) + "!");

        VBox root = new VBox(10, colorLabel, grid, endTurnButton);
        root.setPadding(new Insets(10));

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle("Quatro em Linha");
        stage.show();

        startListening();
    }

    private void startListening() {
    Thread listener = new Thread(() -> {
        try {
            while (true) {
                String msg = client.readMessage();
                if (msg == null) break;

                if (msg.equals("YOUR_TURN")) {
                    Platform.runLater(() -> {
                        myTurn = true;
                        hasPlacedPiece = false;
                        endTurnButton.setDisable(true);
                        enableBoard();
                    });
                } else if (msg.equals("WAIT")) {
                    Platform.runLater(() -> {
                        myTurn = false;
                        hasPlacedPiece = false;
                        endTurnButton.setDisable(true);
                        disableBoard();
                    });
                } else if (msg.startsWith("MOVE ")) {
                    int col = Integer.parseInt(msg.substring(5));
                    Platform.runLater(() -> placeOpponentPiece(col));
                } else if (msg.equals("GAME_OVER")) {
                    Platform.runLater(() -> gameOver("Perdeste..."));
                } else if (msg.startsWith("COLOR ")) {
                    String receivedColor = msg.substring(6).trim().toLowerCase();
                    if (!receivedColor.equals(myColor)) {
                        opponentColor = receivedColor;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    });
    listener.setDaemon(true);
    listener.start();
}


    private boolean placePiece(int col) {
        for (int row = ROWS - 1; row >= 0; row--) {
            Button btn = boardButtons[row][col];
            if (btn.getText().equals(" ")) {
                btn.setText("");
                btn.setStyle("-fx-background-color: " + myColor + ";");
                btn.setShape(new Circle(25));
                btn.setMinSize(50, 50);
                btn.setPrefSize(50, 50);
                btn.setMaxSize(50, 50);
                TranslateTransition drop = new TranslateTransition(Duration.millis(300), btn);
                drop.setFromY(-50 * row);
                drop.setToY(0);
                drop.play();

                if (checkWin(row, col, myColor)) {
                    gameOver("Ganhaste!");
                    client.sendMessage("GAME_OVER");
                }

                client.sendMessage("MOVE " + col);
                return true;
            }
        }
        return false;
    }

    private void placeOpponentPiece(int col) {
        for (int row = ROWS - 1; row >= 0; row--) {
            Button btn = boardButtons[row][col];
            if (btn.getText().equals(" ")) {
                btn.setText("");
                btn.setStyle("-fx-background-color: " + opponentColor + ";");
                btn.setShape(new Circle(25));
                btn.setMinSize(50, 50);
                btn.setPrefSize(50, 50);
                btn.setMaxSize(50, 50);

                TranslateTransition drop = new TranslateTransition(Duration.millis(300), btn);
                drop.setFromY(-50 * row);
                drop.setToY(0);
                drop.play();

                if (checkWin(row, col, opponentColor)) {
                    gameOver("Perdeste...");
                }
                break;
            }
        }
    }

    // Usar winPieces em vez de 4
    private boolean checkWin(int row, int col, String color) {
        return checkDirection(row, col, color, 1, 0)    // vertical
            || checkDirection(row, col, color, 0, 1)    // horizontal
            || checkDirection(row, col, color, 1, 1)    // diagonal \
            || checkDirection(row, col, color, 1, -1);  // diagonal /
    }

    private boolean checkDirection(int row, int col, String color, int deltaRow, int deltaCol) {
        int count = 1;
        count += countPieces(row, col, color, deltaRow, deltaCol);
        count += countPieces(row, col, color, -deltaRow, -deltaCol);
        return count >= winPieces;
    }

    private int countPieces(int row, int col, String color, int deltaRow, int deltaCol) {
        int r = row + deltaRow;
        int c = col + deltaCol;
        int count = 0;
        while (r >= 0 && r < ROWS && c >= 0 && c < COLS
               && boardButtons[r][c].getStyle().contains(color)) {
            count++;
            r += deltaRow;
            c += deltaCol;
        }
        return count;
    }

    private void gameOver(String message) {
        disableBoard();
        endTurnButton.setDisable(true);

        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Fim do jogo!");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void disableBoard() {
        for (Button[] row : boardButtons)
            for (Button cell : row)
                cell.setDisable(true);
    }

    private void enableBoard() {
        for (Button[] row : boardButtons)
            for (Button cell : row)
                if (cell.getText().equals(" "))
                    cell.setDisable(false);
    }
    
    private String translateColor(String color) {
    color = color.toLowerCase();
    if (color.equals("red")) return "vermelho";
    if (color.equals("yellow")) return "amarelo";
    if (color.equals("blue")) return "azul";
    if (color.equals("green")) return "verde";
    if (color.equals("purple")) return "roxo";
    if (color.equals("orange")) return "laranja";
    return color; // fallback
}
}
