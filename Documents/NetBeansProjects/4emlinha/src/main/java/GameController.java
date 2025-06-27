import javafx.application.Platform;  // Permite executar código na thread de UI do JavaFX
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

    private Button[][] boardButtons = new Button[ROWS][COLS]; // Matriz de botões (tabuleiro)
    private Button endTurnButton; // Botão para terminar jogada
    private NetworkClient client; // Cliente de rede para comunicação com o servidor
    private boolean myTurn = false;
    private boolean hasPlacedPiece = false;  // Se o jogador já colocou uma peça neste turno
    private String myColor;      // Cor do jogador atual
    private String opponentColor = null;
    private boolean amPlayer1;
    private int winPieces;       // Número de peças necessárias para ganhar (ex: 4)

    // Construtor: define o cliente, se é o jogador 1, a sua cor e o número de peças para vencer
    public GameController(NetworkClient client, boolean amPlayer1, String myColor, int winPieces) {
        this.client = client;
        this.amPlayer1 = amPlayer1;
        this.myColor = myColor.toLowerCase();
        this.winPieces = winPieces;
        this.myTurn = amPlayer1; // Jogador 1 começa
    }

    // Inicializa o jogo e a interface gráfica
    public void startGame(Stage stage) {
        GridPane grid = new GridPane(); // Layout em grelha para o tabuleiro
        grid.setPadding(new Insets(10));
        grid.setHgap(5);
        grid.setVgap(5);

        // Cria os botões (células do tabuleiro)
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                Button cell = new Button(" ");
                cell.setMinSize(50, 50);
                cell.setShape(new Circle(25)); // Botão redondo
                cell.setStyle("-fx-background-radius: 25; -fx-background-color: lightgray; -fx-border-color: black;");

                final int finalCol = col;
                // Evento de clique para colocar peça
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

        // Botão para terminar a jogada
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

        startListening(); // Começa a escutar mensagens do servidor
    }

    // Thread que escuta mensagens do servidor
    private void startListening() {
        Thread listener = new Thread(() -> {
            try {
                while (true) {
                    String msg = client.readMessage(); // Lê mensagem do servidor
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
                        Platform.runLater(() -> placeOpponentPiece(col)); // Adiciona jogada do oponente
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

    // Coloca peça do jogador na coluna escolhida
    private boolean placePiece(int col) {
        for (int row = ROWS - 1; row >= 0; row--) {
            Button btn = boardButtons[row][col];
            if (btn.getText().equals(" ")) {
                btn.setText("");
                btn.setStyle("-fx-background-color: " + myColor + ";");
                btn.setShape(new Circle(25));

                // Animação de queda
                TranslateTransition drop = new TranslateTransition(Duration.millis(300), btn);
                drop.setFromY(-50 * row);
                drop.setToY(0);
                drop.play();

                if (checkWin(row, col, myColor)) {
                    gameOver("Ganhaste!");
                    client.sendMessage("GAME_OVER");
                }

                client.sendMessage("MOVE " + col); // Envia jogada ao servidor
                return true;
            }
        }
        return false; // Coluna cheia
    }

    // Coloca a peça do adversário
    private void placeOpponentPiece(int col) {
        for (int row = ROWS - 1; row >= 0; row--) {
            Button btn = boardButtons[row][col];
            if (btn.getText().equals(" ")) {
                btn.setText("");
                btn.setStyle("-fx-background-color: " + opponentColor + ";");
                btn.setShape(new Circle(25));

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

    // Mostra mensagem de fim de jogo
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

    // Desativa o tabuleiro
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
    
    // Traduz a cor do inglês para português
    private String translateColor(String color) {
        color = color.toLowerCase();
        if (color.equals("red")) return "vermelho";
        if (color.equals("yellow")) return "amarelo";
        if (color.equals("blue")) return "azul";
        if (color.equals("green")) return "verde";
        if (color.equals("purple")) return "roxo";
        if (color.equals("orange")) return "laranja";
        return color; // caso não reconheça
    }
}
