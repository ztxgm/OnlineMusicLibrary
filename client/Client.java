import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.json.*;
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
        Logger.info("Запуск клиентского приложения OnlineMusicLibrary");
        
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        
        // Панель управления
        HBox controlPanel = new HBox(10);
        Button connectButton = new Button("Подключиться");
        Button reloadButton = new Button("🗘");
        Button playButton = new Button("▶");
        Button addButton = new Button("➕");
        Button editButton = new Button("✏");
        Button deleteButton = new Button("🗑");
        
        // Добавляем всплывающие подсказки
        Tooltip reloadTooltip = new Tooltip("Обновить список треков");
        Tooltip playTooltip = new Tooltip("Воспроизвести выбранный трек");
        Tooltip addTooltip = new Tooltip("Добавить новый трек");
        Tooltip editTooltip = new Tooltip("Редактировать выбранный трек");
        Tooltip deleteTooltip = new Tooltip("Удалить выбранный трек");
        
        reloadButton.setTooltip(reloadTooltip);
        playButton.setTooltip(playTooltip);
        addButton.setTooltip(addTooltip);
        editButton.setTooltip(editTooltip);
        deleteButton.setTooltip(deleteTooltip);
        
        // Увеличиваем размер кнопок для лучшей видимости символов
        reloadButton.setPrefWidth(40);
        playButton.setPrefWidth(40);
        addButton.setPrefWidth(40);
        editButton.setPrefWidth(40);
        deleteButton.setPrefWidth(40);
        
        // Изначально все кнопки, кроме connectButton, отключены
        reloadButton.setDisable(true);
        playButton.setDisable(true);
        addButton.setDisable(true);
        editButton.setDisable(true);
        deleteButton.setDisable(true);
        
        controlPanel.getChildren().addAll(
            connectButton, reloadButton, playButton, addButton, 
            editButton, deleteButton, statusLabel
        );
        
        // Настройка ListView
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
                Logger.debug("Двойной клик по списку треков");
                openAudioPlayer();
            }
        });
        
        // Контекстное меню для правого клика
        ContextMenu contextMenu = new ContextMenu();
        MenuItem editMenuItem = new MenuItem("✏ Редактировать");
        MenuItem deleteMenuItem = new MenuItem("🗑 Удалить");
        contextMenu.getItems().addAll(editMenuItem, deleteMenuItem);
        listView.setContextMenu(contextMenu);
        
        // Обработчики контекстного меню
        editMenuItem.setOnAction(e -> editSelectedTrack());
        deleteMenuItem.setOnAction(e -> deleteSelectedTrack());
        
        // Панель информации о треке
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
        
        // Обработчики кнопок
        connectButton.setOnAction(e -> showConnectDialog());
        reloadButton.setOnAction(e -> loadTracks());
        playButton.setOnAction(e -> openAudioPlayer());
        addButton.setOnAction(e -> showAddTrackDialog());
        editButton.setOnAction(e -> editSelectedTrack());
        deleteButton.setOnAction(e -> deleteSelectedTrack());
        
        // Слушатель выбора трека - только для обновления метки
        listView.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> {
                if (newValue != null) {
                    selectedTrackLabel.setText(newValue.getTitle() + " (" + newValue.getDuration() + ")");
                    Logger.debug("Выбран трек: " + newValue.getTitle());
                } else {
                    selectedTrackLabel.setText("Выберите трек");
                }
            }
        );
        
        root.getChildren().addAll(controlPanel, listView, infoPanel);
        
        Scene scene = new Scene(root, 700, 450);
        primaryStage.setTitle("OnlineMusicLibrary");
        primaryStage.setScene(scene);
        primaryStage.show();
        
        Logger.info("Главное окно клиента отображено");
        
        primaryStage.setOnCloseRequest(e -> {
            Logger.info("Закрытие клиентского приложения");
            if (playerWindow != null) {
                playerWindow.stopAudio();
            }
            Logger.close();
        });
        
        // Слушатель для изменения статуса подключения
        statusLabel.textProperty().addListener((observable, oldValue, newValue) -> {
            boolean isConnected = !newValue.equals("Не подключено");
            reloadButton.setDisable(!isConnected);
            addButton.setDisable(!isConnected);
            
            // Также управляем состоянием кнопок в зависимости от выбора трека
            if (!isConnected) {
                editButton.setDisable(true);
                deleteButton.setDisable(true);
                playButton.setDisable(true);
            } else {
                MusicTrack selectedTrack = listView.getSelectionModel().getSelectedItem();
                boolean hasSelection = selectedTrack != null;
                editButton.setDisable(!hasSelection);
                deleteButton.setDisable(!hasSelection);
                playButton.setDisable(!hasSelection);
            }
        });
        
        // Дополнительный слушатель для выбора трека, чтобы управлять кнопками
        listView.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> {
                boolean isConnected = !statusLabel.getText().equals("Не подключено");
                if (isConnected) {
                    boolean hasSelection = newValue != null;
                    editButton.setDisable(!hasSelection);
                    deleteButton.setDisable(!hasSelection);
                    playButton.setDisable(!hasSelection);
                }
            }
        );
    }
    
    private void showConnectDialog() {
        Logger.debug("Отображение диалога подключения к серверу");
        
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
                    Logger.error("Некорректный номер порта: " + portField.getText());
                    return null;
                }
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(info -> {
            currentServer = info.server;
            currentPort = info.port;
            Logger.info("Введены параметры подключения: " + currentServer + ":" + currentPort);
            connectToServer();
        });
    }
    
    private void showAddTrackDialog() {
        Logger.info("Открытие диалога добавления трека");
        TrackEditDialog dialog = new TrackEditDialog(currentServer, currentPort);
        dialog.showAndWait();
        
        // После закрытия диалога обновляем список треков
        loadTracks();
    }
    
    private void editSelectedTrack() {
        MusicTrack selectedTrack = listView.getSelectionModel().getSelectedItem();
        if (selectedTrack == null) {
            showAlert("Ошибка", "Выберите трек для редактирования");
            return;
        }
        
        Logger.info("Открытие диалога редактирования трека: " + selectedTrack.getTitle());
        TrackEditDialog dialog = new TrackEditDialog(selectedTrack, currentServer, currentPort);
        dialog.showAndWait();
        
        // После закрытия диалога обновляем список треков
        loadTracks();
    }
    
    private void deleteSelectedTrack() {
        MusicTrack selectedTrack = listView.getSelectionModel().getSelectedItem();
        if (selectedTrack == null) {
            showAlert("Ошибка", "Выберите трек для удаления");
            return;
        }
        
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Подтверждение удаления");
        confirmDialog.setHeaderText("Удалить трек?");
        confirmDialog.setContentText("Вы уверены, что хотите удалить трек \"" + 
                                   selectedTrack.getTitle() + "\"?\n" +
                                   "При этом будут удалены:\n" +
                                   "• Аудиофайл: " + selectedTrack.getFilename() + "\n" +
                                   "• Обложка: " + 
                                   (selectedTrack.getCover().equals("-") ? "нет" : selectedTrack.getCover()));
        
        confirmDialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                deleteTrack(selectedTrack.getId());
            }
        });
    }
    
    private void deleteTrack(String id) {
        Logger.info("Удаление трека с ID: " + id);
        
        new Thread(() -> {
            try {
                Socket socket = new Socket(currentServer, currentPort);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                JSONObject request = new JSONObject();
                request.put("command", "DELETE_TRACK");
                request.put("id", id);
                out.println(request.toString());
                
                String response = in.readLine();
                JSONObject jsonResponse = new JSONObject(response);
                
                socket.close();
                
                javafx.application.Platform.runLater(() -> {
                    if (jsonResponse.getString("status").equals("OK")) {
                        Logger.info("Трек успешно удален");
                        showAlert("Успех", "Трек успешно удален\n" +
                                (jsonResponse.has("audioDeleted") && jsonResponse.getBoolean("audioDeleted") ? 
                                    "Аудиофайл удален\n" : "") +
                                (jsonResponse.has("coverDeleted") && jsonResponse.getBoolean("coverDeleted") ? 
                                    "Обложка удалена" : ""));
                        loadTracks(); // Обновляем список
                    } else {
                        showAlert("Ошибка", "Не удалось удалить трек: " + 
                                jsonResponse.getString("message"));
                    }
                });
                
            } catch (Exception e) {
                Logger.error("Ошибка при удалении трека: " + e.getMessage(), e);
                javafx.application.Platform.runLater(() -> {
                    showAlert("Ошибка", "Ошибка при удалении трека: " + e.getMessage());
                });
            }
        }).start();
    }
    
    private void connectToServer() {
        Logger.info("Попытка подключения к серверу: " + currentServer + ":" + currentPort);
        
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                Logger.debug("Закрыто предыдущее соединение");
            }
            
            socket = new Socket(currentServer, currentPort);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            
            statusLabel.setText("Подключено к " + currentServer + ":" + currentPort);
            Logger.info("Успешное подключение к серверу");
            
            loadTracks();
            
        } catch (IOException e) {
            String errorMsg = "Ошибка подключения: " + e.getMessage();
            Logger.error(errorMsg, e);
            statusLabel.setText("Ошибка подключения: " + e.getMessage());
            showAlert("Ошибка подключения", "Не удалось подключиться к серверу " + currentServer + ":" + currentPort);
        }
    }
    
    private void loadTracks() {
        Logger.info("Загрузка списка треков с сервером");
        
        if (out == null) {
            Logger.error("Нет подключения к серверу для загрузки треков");
            showAlert("Ошибка", "Сначала подключитесь к серверу");
            return;
        }
        
        try {
            // Отправляем JSON запрос
            JSONObject request = new JSONObject();
            request.put("command", "GET_ALL");
            out.println(request.toString());
            Logger.debug("Отправлен запрос GET_ALL на сервер");
            
            // Получаем JSON ответ
            String response = in.readLine();
            JSONObject jsonResponse = new JSONObject(response);
            
            if (!jsonResponse.getString("status").equals("OK")) {
                String errorMsg = "Ошибка загрузки треков: " + jsonResponse.getString("message");
                Logger.error(errorMsg);
                showAlert("Ошибка", errorMsg);
                return;
            }
            
            trackList.clear();
            JSONArray tracksArray = jsonResponse.getJSONArray("data");
            Logger.info("Получено " + tracksArray.length() + " треков от сервера");
            
            for (int i = 0; i < tracksArray.length(); i++) {
                JSONObject trackJson = tracksArray.getJSONObject(i);
                MusicTrack track = MusicTrack.fromJson(trackJson);
                if (track != null) {
                    trackList.add(track);
                }
            }
            
            Logger.info("Список треков обновлен, количество: " + trackList.size());
            
        } catch (IOException e) {
            Logger.error("Ошибка ввода-вывода при загрузке треков: " + e.getMessage(), e);
            statusLabel.setText("Ошибка загрузки данных");
            showAlert("Ошибка", "Не удалось загрузить данные с сервера");
        } catch (JSONException e) {
            Logger.error("Ошибка парсинга JSON ответа от сервера: " + e.getMessage(), e);
            showAlert("Ошибка", "Некорректный ответ от сервера: " + e.getMessage());
        } catch (Exception e) {
            Logger.error("Неожиданная ошибка при загрузке треков: " + e.getMessage(), e);
            showAlert("Ошибка", "Неожиданная ошибка: " + e.getMessage());
        }
    }
    
    private void openAudioPlayer() {
        MusicTrack selectedTrack = listView.getSelectionModel().getSelectedItem();
        if (selectedTrack == null) {
            Logger.warning("Попытка открыть плеер без выбранного трека");
            showAlert("Ошибка", "Выберите трек для воспроизведения");
            return;
        }
        
        int selectedIndex = listView.getSelectionModel().getSelectedIndex();
        Logger.info("Открытие аудиоплеера для трека: " + selectedTrack.getTitle() + " (индекс: " + selectedIndex + ")");
        
        // Если окно плеера уже существует, обновляем его
        if (playerWindow != null && playerWindow.isShowing()) {
            Logger.debug("Обновление существующего окна аудиоплеера");
            playerWindow.loadTrack(selectedTrack, selectedIndex, trackList);
        } else {
            // Создаем новое окно
            Logger.debug("Создание нового окна аудиоплеера");
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
        Logger.warning("Показать alert: " + title + " - " + message);
        
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    public static void main(String[] args) {
        Logger.info("Запуск клиентского приложения OnlineMusicLibrary");
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
        
        public static MusicTrack fromJson(JSONObject json) {
            try {
                return new MusicTrack(
                    json.getString("id"),
                    json.getString("title"),
                    json.getString("duration"),
                    json.getString("artist"),
                    json.getString("audioFilename"),
                    json.optString("coverFilename", "-")
                );
            } catch (Exception e) {
                Logger.error("Ошибка создания MusicTrack из JSON: " + e.getMessage(), e);
                return null;
            }
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
