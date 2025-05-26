import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class Main extends Application {
    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        Label titleLabel = new Label("Quatro em Linha - Conexão");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        TextField ipField = new TextField("localhost");
        ipField.setPromptText("Introduza o IP do servidor.");

        TextField portField = new TextField("5555");
        portField.setPromptText("Introduza a porta.");

        TextField nameField = new TextField();
        nameField.setPromptText("Introduza o seu nome.");

        ComboBox<String> colorPicker = new ComboBox<>();
        // Use English color keys internally but display Portuguese labels
        colorPicker.getItems().addAll("red", "yellow", "green", "blue");
        colorPicker.setPromptText("Escolha a cor das suas peças.");

        // Optional: custom cell factory to show user-friendly names
        colorPicker.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String color, boolean empty) {
                super.updateItem(color, empty);
                if (empty || color == null) {
                    setText(null);
                    setStyle("");
                } else {
                    String label;
                        switch (color) {
                    case "red":
                    label = "Vermelho";
                    break;
                        case "yellow":
                    label = "Amarelo";
                    break;
                        case "green":
                    label = "Verde";
                    break;
                        case "blue":
                    label = "Azul";
                    break;
                    default:
                    label = color;
                        }
                    setText(label);
                }
            }
        });
        colorPicker.setButtonCell(colorPicker.getCellFactory().call(null));

        Button connectButton = new Button("Conectar!");
        connectButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; "
                + "-fx-font-weight: bold; -fx-padding: 8 16 8 16;");

        connectButton.setOnAction(e -> {
            try {
                String ip = ipField.getText().trim();
                int port = Integer.parseInt(portField.getText().trim());
                String name = nameField.getText().trim();
                String chosenColor = colorPicker.getValue();

                if (name.isEmpty()) {
                    showAlert("A introdução de um nome é obrigatória.");
                    return;
                }

                if (chosenColor == null || !(chosenColor.equals("red") || chosenColor.equals("yellow") 
                        || chosenColor.equals("green") || chosenColor.equals("blue"))) {
                    showAlert("Por favor escolha uma cor válida.");
                    return;
                }

                NetworkClient client = new NetworkClient(ip, port, name);

                // Send color choice and wait for acceptance
                client.sendMessage("COLOR " + chosenColor);

                String response;
                while (true) {
                    response = client.readMessage();
                    if (response == null) {
                        showAlert("Conexão perdida com o servidor.");
                        return;
                    }
                    if (response.equals("COLOR_ACCEPTED")) {
                        break;  // Proceed
                    } else if (response.startsWith("COLOR_REJECTED")) {
                        showAlert("Cor rejeitada pelo servidor: " + response.substring(14));
                        client.close();
                        return;
                    } else if (response.startsWith("SEND_COLOR")) {
                        // Server is asking again, send color again or stop?
                        client.sendMessage("COLOR " + chosenColor);
                    } else {
                        // Ignore other messages here
                    }
                }

                // Read role message from server
                String roleMsg = client.readMessage();
                boolean amPlayer1 = "ROLE PLAYER1".equals(roleMsg);

                int winPieces = 4;  // or your logic

                GameController controller = new GameController(client, amPlayer1, chosenColor, winPieces);
                controller.startGame(primaryStage);

            } catch (Exception ex) {
                ex.printStackTrace();
                showAlert("Não foi possível conectar ao servidor: " + ex.getMessage());
            }
        });

        VBox formBox = new VBox(10,
                new Label("IP do servidor:"), ipField,
                new Label("Porta:"), portField,
                new Label("Nome do jogador:"), nameField,
                new Label("Cor:"), colorPicker,
                connectButton);
        formBox.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(20, titleLabel, formBox);
        root.setPadding(new Insets(25));
        root.setAlignment(Pos.CENTER);

        Scene scene = new Scene(root);
        stage.setTitle("Conectar ao servidor do jogo.");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("ERRO DE CONEXÃO");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
