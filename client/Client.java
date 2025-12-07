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
    private int currentPort = 12345;
    
    // Статическая ссылка на окно плеера
    private static AudioPlayerWindow playerWindow;
    
    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        
        HBox controlPanel = new HBox(10);
        Button connectButton = new Button("Подключиться");
        Button reloadButton = new Button("Обновить");
        Button playButton = new Button("Воспроизвести");
        playButton.setDisable(true);
        reloadButton.setDisable(true);
        
        controlPanel.getChildren().addAll(connectButton, reloadButton, playButton, statusLabel);
        
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
        
        // Двойной клик для открытия плеера
        listView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                openAudioPlayer();
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
        playButton.setOnAction(e -> openAudioPlayer());
        
        listView.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> {
                if (newValue != null) {
                    selectedTrackLabel.setText(newValue.getTitle() + " (" + newValue.getDuration() + ")");
                    playButton.setDisable(false);
                } else {
                    playButton.setDisable(true);
                }
            }
        );
        
        root.getChildren().addAll(controlPanel, listView, infoPanel);
        
        Scene scene = new Scene(root, 600, 400);
        primaryStage.setTitle("OnlineMusicLibrary");
        primaryStage.setScene(scene);
        primaryStage.show();
        
        primaryStage.setOnCloseRequest(e -> {
            if (playerWindow != null) {
                playerWindow.stopAudio();
            }
        });
        
        reloadButton.disableProperty().bind(
            statusLabel.textProperty().isEqualTo("Не подключено")
        );
    }
    
    private void showConnectDialog() {
        Dialog<ConnectionInfo> dialog = new Dialog<>();
        dialog.setTitle("Подключение к серверу");
        dialog.setHeaderText("Введите параметры подключения:");
        
        ButtonType connectButtonType = new ButtonType("Подключиться", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(connectButtonType, ButtonType.CANCEL);
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        TextField serverAddress = new TextField("localhost");
        serverAddress.setPromptText("localhost или IP адрес");
        
        TextField portField = new TextField("12345");
        portField.setPromptText("Порт");
        
        grid.add(new Label("Адрес сервера:"), 0, 0);
        grid.add(serverAddress, 1, 0);
        grid.add(new Label("Порт:"), 0, 1);
        grid.add(portField, 1, 1);
        
        dialog.getDialogPane().setContent(grid);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == connectButtonType) {
                try {
                    return new ConnectionInfo(
                        serverAddress.getText(),
                        Integer.parseInt(portField.getText())
                    );
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(info -> {
            currentServer = info.server;
            currentPort = info.port;
            connectToServer();
        });
    }
    
    private void connectToServer() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            
            socket = new Socket(currentServer, currentPort);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            
            statusLabel.setText("Подключено к " + currentServer + ":" + currentPort);
            loadTracks();
            
        } catch (IOException e) {
            statusLabel.setText("Ошибка подключения: " + e.getMessage());
            showAlert("Ошибка подключения", "Не удалось подключиться к серверу " + currentServer + ":" + currentPort);
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
    
    private void openAudioPlayer() {
        MusicTrack selectedTrack = listView.getSelectionModel().getSelectedItem();
        if (selectedTrack == null) {
            showAlert("Ошибка", "Выберите трек для воспроизведения");
            return;
        }
        
        int selectedIndex = listView.getSelectionModel().getSelectedIndex();
        
        // Если окно плеера уже существует, обновляем его
        if (playerWindow != null && playerWindow.isShowing()) {
            playerWindow.loadTrack(selectedTrack, selectedIndex, trackList);
        } else {
            // Создаем новое окно
            playerWindow = new AudioPlayerWindow(
                selectedTrack, 
                selectedIndex, 
                trackList,
                currentServer,
                currentPort,
                listView.getSelectionModel()  // Передаем selectionModel для синхронизации
            );
            playerWindow.show();
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
    
    // Класс для хранения информации о подключении
    private static class ConnectionInfo {
        String server;
        int port;
        
        ConnectionInfo(String server, int port) {
            this.server = server;
            this.port = port;
        }
    }
    
    public static class MusicTrack {
        private String id;
        private String title;
        private String duration;
        private String artist;
        private String filename;
        private String cover;
        
        public MusicTrack(String id, String title, String duration, String artist, String filename, String cover) {
            this.id = id;
            this.title = title;
            this.duration = duration;
            this.artist = artist;
            this.filename = filename;
            this.cover = cover;
        }
        
        public static MusicTrack fromString(String str) {
            try {
                String[] parts = str.split(":");
                if (parts.length >= 6) {
                    String cover = parts.length >= 7 ? parts[6] : "-";
                    return new MusicTrack(parts[0], parts[1], parts[2] + ":" + parts[3], parts[4], parts[5], cover);
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
        public String getFilename() { return filename; }
        public String getCover() { return cover; }
        
        @Override
        public String toString() {
            return title + " - " + artist + " (" + duration + ")";
        }
    }
}