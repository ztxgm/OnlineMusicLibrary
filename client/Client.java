import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.io.*;
import java.net.*;

public class Client extends Application {
    private ObservableList<MusicTrack> trackList = FXCollections.observableArrayList();
    private ListView<MusicTrack> listView = new ListView<>(trackList);
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Label statusLabel = new Label("Не подключено");
    private Label selectedTrackLabel = new Label("Выберите трек");
    private String currentServer = "localhost";
    
    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        
        HBox controlPanel = new HBox(10);
        Button connectButton = new Button("Подключиться");
        Button reloadButton = new Button("Обновить");
        reloadButton.setDisable(true);
        
        controlPanel.getChildren().addAll(connectButton, reloadButton, statusLabel);
        
        listView.setCellFactory(param -> new ListCell<MusicTrack>() {
            @Override
            protected void updateItem(MusicTrack item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.toString());
                }
            }
        });
        
        VBox infoPanel = new VBox(5);
        infoPanel.setPadding(new Insets(10));
        infoPanel.setStyle("-fx-border-color: gray; -fx-border-width: 1;");
        
        Label infoLabel = new Label("Информация о треке:");
        infoLabel.setStyle("-fx-font-weight: bold;");
        
        HBox trackInfoBox = new HBox(10);
        trackInfoBox.getChildren().addAll(
            new Label("Название:"),
            selectedTrackLabel
        );
        
        infoPanel.getChildren().addAll(infoLabel, trackInfoBox);
        
        connectButton.setOnAction(e -> showConnectDialog());
        reloadButton.setOnAction(e -> loadTracks());
        
        listView.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> {
                if (newValue != null) {
                    selectedTrackLabel.setText(newValue.getTitle() + " (" + newValue.getDuration() + ")");
                }
            }
        );
        
        root.getChildren().addAll(controlPanel, listView, infoPanel);
        
        Scene scene = new Scene(root, 600, 400);
        primaryStage.setTitle("Музыкальная библиотека");
        primaryStage.setScene(scene);
        primaryStage.show();
        
        reloadButton.disableProperty().bind(
            statusLabel.textProperty().isEqualTo("Не подключено")
        );
    }
    
    private void showConnectDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Подключение к серверу");
        dialog.setHeaderText("Введите адрес сервера:");
        
        ButtonType connectButtonType = new ButtonType("Подключиться", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(connectButtonType, ButtonType.CANCEL);
        
        TextField serverAddress = new TextField("localhost");
        serverAddress.setPromptText("localhost или IP адрес");
        
        VBox content = new VBox(10);
        content.getChildren().addAll(new Label("Адрес сервера:"), serverAddress);
        dialog.getDialogPane().setContent(content);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == connectButtonType) {
                return serverAddress.getText();
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(server -> {
            currentServer = server;
            connectToServer();
        });
    }
    
    private void connectToServer() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            
            socket = new Socket(currentServer, 12345);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            
            statusLabel.setText("Подключено к " + currentServer);
            loadTracks();
            
        } catch (IOException e) {
            statusLabel.setText("Ошибка подключения: " + e.getMessage());
            showAlert("Ошибка подключения", "Не удалось подключиться к серверу " + currentServer);
        }
    }
    
    private void loadTracks() {
        if (out == null) {
            showAlert("Ошибка", "Сначала подключитесь к серверу");
            return;
        }
        
        try {
            out.println("GET_ALL");
            trackList.clear();
            
            String response;
            while (!(response = in.readLine()).equals("END")) {
                MusicTrack track = MusicTrack.fromString(response);
                if (track != null) {
                    trackList.add(track);
                }
            }
            
        } catch (IOException e) {
            statusLabel.setText("Ошибка загрузки данных");
            showAlert("Ошибка", "Не удалось загрузить данные с сервера");
        }
    }
    
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    public static void main(String[] args) {
        launch(args);
    }
    
    public static class MusicTrack {
        private String id;
        private String title;
        private String duration;
        private String artist;
        
        public MusicTrack(String id, String title, String duration, String artist) {
            this.id = id;
            this.title = title;
            this.duration = duration;
            this.artist = artist;
        }
        
        public static MusicTrack fromString(String str) {
            try {
                String[] parts = str.split(":");
                if (parts.length >= 5) {
                    return new MusicTrack(parts[0], parts[1], parts[2] + ":" + parts[3], parts[4]);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
        
        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getDuration() { return duration; }
        public String getArtist() { return artist; }
        
        @Override
        public String toString() {
            return title + " - " + artist + " (" + duration + ")";
        }
    }
}