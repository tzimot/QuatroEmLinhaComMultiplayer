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

    private final Button[][] boardButtons = new Button[ROWS][COLS]; // Matriz de botões (tabuleiro)
    private Button endTurnButton; 
    private final NetworkClient client; // Cliente de rede para comunicação com o servidor
    private boolean myTurn = false;
    private boolean hasPlacedPiece = false;  
    private final String myColor;     
    private String opponentColor = null;
    private final boolean amPlayer1;
    private final int winPieces;       // Número de peças necessárias para ganhar (ex: 4)

    // define o cliente, se é o jogador 1, a sua cor e o número de peças para vencer
    public GameController(NetworkClient client, boolean amPlayer1, String myColor, int winPieces) {
        this.client = client;
        this.amPlayer1 = amPlayer1;
        this.myColor = myColor.toLowerCase();
        this.winPieces = winPieces;
        this.myTurn = amPlayer1; 

    // Inicializa o jogo e a interface gráfica
    public void startGame(Stage stage) {
        GridPane grid = new GridPane(); 
        grid.setPadding(new Insets(10));
        grid.setHgap(5);
        grid.setVgap(5);

        // Cria os botões (células do tabuleiro)
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                Button cell = new Button(" ");
                cell.setMinSize(50, 50);
                cell.setShape(new Circle(25));
                cell.setStyle("-fx-background-radius: 25; -fx-background-color: lightgray; -fx-border-color: black;");

                final int finalCol = col;
                // Evento de clique para colocar peça
                cell.setOnAction(e -> {
                    if (myTurn && !hasPlacedPiece) {
                        boolean placed = placePiece(finalCol, myColor, true);
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
                client.sendMessage("END_TURN"); // Notifica o servidor que o turno terminou
            }
        });

        Label colorLabel = new Label("A tua cor é o " + translateColor(myColor) + "!");

        VBox root = new VBox(10, colorLabel, grid, endTurnButton); // Layout vertical
        root.setPadding(new Insets(10));

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle("Quatro em Linha");
        stage.show();

        startListening(); // recebe mensagens do servidor
    }

    // Thread que lê mensagens do servidor
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
                            enableBoard(); // Habilita jogada
                        });
                    } else if (msg.equals("WAIT")) {
                        Platform.runLater(() -> {
                            myTurn = false;
                            hasPlacedPiece = false;
                            endTurnButton.setDisable(true);
                            disableBoard(); // Aguarda turno
                        });
                    } else if (msg.startsWith("MOVE ")) {
                        int col = Integer.parseInt(msg.substring(5));
                        Platform.runLater(() -> placePiece(col, opponentColor, false)); // Adiciona jogada do oponente
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

    // Coloca peça do jogador ou do adversário na coluna escolhida
    private boolean placePiece(int col, String color, boolean isPlayer) {
        if (color == null) return false; // Protege contra cor nula
        for (int row = ROWS - 1; row >= 0; row--) {
            Button btn = boardButtons[row][col];
            if (btn.getText().equals(" ")) {
                styleAndAnimateButton(btn, color, row);
                boolean venceu = checkWin(row, col, color);
                if (venceu) {
                    if (isPlayer) {
                        gameOver("Ganhaste!");
                        client.sendMessage("GAME_OVER");
                    } else {
                        gameOver("Perdeste...");
                    }
                }
                if (isPlayer) {
                    client.sendMessage("MOVE " + col); 
                }
                return true;
            }
        }
        return false; // Coluna cheia
    }

    // Aplica estilo e animação ao botão
    private void styleAndAnimateButton(Button btn, String color, int row) {
        btn.setText("");
        btn.setStyle("-fx-background-color: " + color + ";");
        btn.setShape(new Circle(25));
        TranslateTransition drop = new TranslateTransition(Duration.millis(300), btn);
        drop.setFromY(-50 * row);
        drop.setToY(0);
        drop.play();
    }

    // Verifica se houve vitória após a jogada
    private boolean checkWin(int row, int col, String color) {
        return checkDirection(row, col, color, 1, 0)    // vertical
            || checkDirection(row, col, color, 0, 1)    // horizontal
            || checkDirection(row, col, color, 1, 1)    // diagonal \
            || checkDirection(row, col, color, 1, -1);  // diagonal /
    }

    // Verifica uma direção específica
    private boolean checkDirection(int row, int col, String color, int deltaRow, int deltaCol) {
        int count = 1;
        count += countPieces(row, col, color, deltaRow, deltaCol);
        count += countPieces(row, col, color, -deltaRow, -deltaCol);
        return count >= winPieces;
    }

    // Conta quantas peças da mesma cor existem numa direção
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

    // Ativa o tabuleiro apenas para células vazias
    private void enableBoard() {
        for (Button[] row : boardButtons)
            for (Button cell : row)
                if (cell.getText().equals(" "))
                    cell.setDisable(false);
    }
    
    private String translateColor(String color) {
        switch (color.toLowerCase()) {
            case "red": return "vermelho";
            case "yellow": return "amarelo";
            case "blue": return "azul";
            case "green": return "verde";
            case "purple": return "roxo";
            case "orange": return "laranja";
            default: return color;
        }
    }
}
