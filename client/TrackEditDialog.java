import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import org.json.JSONObject;
import java.io.*;
import java.util.Base64;

public class TrackEditDialog extends Stage {
    private TextField idField;
    private TextField titleField;
    private TextField durationField; // Неактивное поле
    private TextField artistField;
    private TextField audioFileField;
    private TextField coverFileField;
    private Button browseAudioButton;
    private Button browseCoverButton;
    private Button saveButton;
    private Button cancelButton;
    
    private File audioFile;
    private File coverFile;
    
    private boolean editMode = false;
    private Client.MusicTrack originalTrack;
    
    private String serverAddress;
    private int serverPort;
    
    // Конструктор для добавления нового трека
    public TrackEditDialog(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        setTitle("Добавить новый трек");
        initModality(Modality.APPLICATION_MODAL);
        setupUI();
        loadNextId();
    }
    
    // Конструктор для редактирования существующего трека
    public TrackEditDialog(Client.MusicTrack track, String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        editMode = true;
        originalTrack = track;
        setTitle("Редактировать трек: " + track.getTitle());
        initModality(Modality.APPLICATION_MODAL);
        setupUI();
        fillFields(track);
    }
    
    private void setupUI() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 20, 20));
        
        // ID
        grid.add(new Label("ID:"), 0, 0);
        idField = new TextField();
        idField.setPromptText("Уникальный идентификатор");
        idField.setDisable(editMode); // Нельзя менять ID при редактировании
        grid.add(idField, 1, 0);
        
        // Название
        grid.add(new Label("Название:"), 0, 1);
        titleField = new TextField();
        titleField.setPromptText("Название трека");
        grid.add(titleField, 1, 1);
        
        // Длительность (неактивное поле, определяется автоматически)
        grid.add(new Label("Длительность:"), 0, 2);
        durationField = new TextField();
        durationField.setPromptText("Определяется автоматически");
        durationField.setEditable(false); // Поле только для чтения
        durationField.setStyle("-fx-background-color: #f0f0f0; -fx-text-fill: #666;");
        grid.add(durationField, 1, 2);
        
        // Исполнитель
        grid.add(new Label("Исполнитель:"), 0, 3);
        artistField = new TextField();
        artistField.setPromptText("Имя исполнителя");
        grid.add(artistField, 1, 3);
        
        // Аудиофайл
        grid.add(new Label("Аудиофайл:"), 0, 4);
        HBox audioBox = new HBox(5);
        audioFileField = new TextField();
        audioFileField.setPromptText("Выберите аудиофайл");
        audioFileField.setPrefWidth(250);
        browseAudioButton = new Button("Обзор...");
        audioBox.getChildren().addAll(audioFileField, browseAudioButton);
        grid.add(audioBox, 1, 4);
        
        // Обложка
        grid.add(new Label("Обложка:"), 0, 5);
        HBox coverBox = new HBox(5);
        coverFileField = new TextField();
        coverFileField.setPromptText("Выберите изображение или оставьте -");
        coverFileField.setPrefWidth(250);
        browseCoverButton = new Button("Обзор...");
        coverBox.getChildren().addAll(coverFileField, browseCoverButton);
        grid.add(coverBox, 1, 5);
        
        // Кнопки
        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(20, 0, 0, 0));
        saveButton = new Button(editMode ? "Сохранить изменения" : "Добавить трек");
        cancelButton = new Button("Отмена");
        buttonBox.getChildren().addAll(saveButton, cancelButton);
        grid.add(buttonBox, 1, 6);
        
        // Обработчики событий
        browseAudioButton.setOnAction(e -> browseAudioFile());
        browseCoverButton.setOnAction(e -> browseCoverFile());
        saveButton.setOnAction(e -> saveTrack());
        cancelButton.setOnAction(e -> close());
        
        Scene scene = new Scene(grid, 500, 320);
        setScene(scene);
    }
    
    private void fillFields(Client.MusicTrack track) {
        idField.setText(track.getId());
        titleField.setText(track.getTitle());
        durationField.setText(track.getDuration());
        artistField.setText(track.getArtist());
        audioFileField.setText(track.getFilename());
        coverFileField.setText(track.getCover().equals("-") ? "" : track.getCover());
    }
    
    private void browseAudioFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите аудиофайл");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Аудиофайлы", "*.mp3", "*.wav", "*.ogg", "*.flac", "*.m4a", "*.aac"),
            new FileChooser.ExtensionFilter("Все файлы", "*.*")
        );
        
        File selectedFile = fileChooser.showOpenDialog(this);
        if (selectedFile != null) {
            audioFile = selectedFile;
            audioFileField.setText(selectedFile.getName());
            
            // Определяем длительность аудиофайла
            determineAudioDuration(selectedFile);
            
            // Автоматически заполняем имя файла обложки
            if (!editMode && (coverFileField.getText() == null || coverFileField.getText().isEmpty())) {
                String baseName = selectedFile.getName().replaceFirst("[.][^.]+$", "");
                coverFileField.setText(baseName + ".png");
            }
        }
    }
    
    private void determineAudioDuration(File audioFile) {
        Logger.debug("Определение длительности аудиофайла: " + audioFile.getName());
        
        // Показываем сообщение о процессе определения
        durationField.setText("Определяется...");
        
        // Определяем длительность в отдельном потоке
        new Thread(() -> {
            try {
                String fileUrl = audioFile.toURI().toString();
                
                javafx.application.Platform.runLater(() -> {
                    try {
                        Media media = new Media(fileUrl);
                        
                        media.setOnError(() -> {
                            Logger.warning("Не удалось определить длительность файла: " + audioFile.getName() + 
                                         ". Ошибка: " + media.getError().getMessage());
                            durationField.setText("00:00");
                        });
                        
                        // Используем слушатель для определения длительности
                        media.durationProperty().addListener((observable, oldValue, newValue) -> {
                            if (newValue != null && newValue.greaterThan(Duration.ZERO) && !newValue.equals(Duration.UNKNOWN)) {
                                int minutes = (int) newValue.toMinutes();
                                int seconds = (int) newValue.toSeconds() % 60;
                                String durationStr = String.format("%02d:%02d", minutes, seconds);
                                Logger.debug("Определена длительность: " + durationStr + " для файла: " + audioFile.getName());
                                durationField.setText(durationStr);
                            } else if (newValue.equals(Duration.UNKNOWN)) {
                                Logger.warning("Длительность файла неизвестна: " + audioFile.getName());
                                durationField.setText("00:00");
                            }
                        });
                        
                    } catch (Exception e) {
                        Logger.error("Ошибка при создании Media объекта: " + e.getMessage(), e);
                        durationField.setText("00:00");
                    }
                });
                
            } catch (Exception e) {
                Logger.error("Ошибка при определении длительности: " + e.getMessage(), e);
                javafx.application.Platform.runLater(() -> {
                    durationField.setText("00:00");
                });
            }
        }).start();
    }
    
    private void browseCoverFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите обложку");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
            new FileChooser.ExtensionFilter("Все файлы", "*.*")
        );
        
        File selectedFile = fileChooser.showOpenDialog(this);
        if (selectedFile != null) {
            coverFile = selectedFile;
            coverFileField.setText(selectedFile.getName());
        }
    }
    
    private void loadNextId() {
        new Thread(() -> {
            try {
                java.net.Socket socket = new java.net.Socket(serverAddress, serverPort);
                java.io.PrintWriter out = new java.io.PrintWriter(socket.getOutputStream(), true);
                java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));
                
                JSONObject request = new JSONObject();
                request.put("command", "GET_NEXT_ID");
                out.println(request.toString());
                
                String response = in.readLine();
                JSONObject jsonResponse = new JSONObject(response);
                
                if (jsonResponse.getString("status").equals("OK")) {
                    String nextId = jsonResponse.getString("nextId");
                    javafx.application.Platform.runLater(() -> {
                        idField.setText(nextId);
                    });
                }
                
                socket.close();
            } catch (Exception e) {
                Logger.error("Ошибка при получении следующего ID: " + e.getMessage(), e);
            }
        }).start();
    }
    
    private String encodeFileToBase64(File file) throws IOException {
        if (file == null || !file.exists()) {
            return "";
        }
        
        byte[] fileContent = java.nio.file.Files.readAllBytes(file.toPath());
        return Base64.getEncoder().encodeToString(fileContent);
    }
    
    private void saveTrack() {
        // Проверка обязательных полей
        if (idField.getText().isEmpty() || titleField.getText().isEmpty() || 
            artistField.getText().isEmpty() || audioFileField.getText().isEmpty()) {
            
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText("Не все поля заполнены");
            alert.setContentText("Пожалуйста, заполните все обязательные поля (ID, название, исполнитель, аудиофайл).");
            alert.showAndWait();
            return;
        }
        
        // Проверка, что длительность определена (не "Определяется...")
        if (durationField.getText().equals("Определяется...")) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Внимание");
            alert.setHeaderText("Определяется длительность");
            alert.setContentText("Пожалуйста, подождите пока определится длительность аудиофайла.");
            alert.showAndWait();
            return;
        }
        
        // Проверка формата длительности
        String durationText = durationField.getText();
        if (!durationText.equals("00:00") && !durationText.matches("\\d+:\\d{2}")) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText("Некорректная длительность");
            alert.setContentText("Длительность должна быть в формате мм:сс (например, 3:45) или 00:00 для неустановленной.");
            alert.showAndWait();
            return;
        }
        
        // Проверка существования аудиофайла (кроме случая редактирования без изменения файла)
        if (!editMode && (audioFile == null || !audioFile.exists())) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText("Аудиофайл не выбран");
            alert.setContentText("Пожалуйста, выберите аудиофайл для трека.");
            alert.showAndWait();
            return;
        }
        
        // Отправка на сервер
        new Thread(() -> {
            try {
                java.net.Socket socket = new java.net.Socket(serverAddress, serverPort);
                java.io.PrintWriter out = new java.io.PrintWriter(socket.getOutputStream(), true);
                java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));
                
                JSONObject request = new JSONObject();
                
                if (editMode) {
                    request.put("command", "UPDATE_TRACK");
                } else {
                    request.put("command", "ADD_TRACK");
                }
                
                request.put("id", idField.getText());
                request.put("title", titleField.getText());
                request.put("duration", durationField.getText());
                request.put("artist", artistField.getText());
                request.put("audioFilename", audioFileField.getText());
                request.put("coverFilename", 
                    coverFileField.getText().isEmpty() ? "-" : coverFileField.getText());
                
                // Кодируем и добавляем аудиофайл
                if (audioFile != null) {
                    String audioData = encodeFileToBase64(audioFile);
                    request.put("audioData", audioData);
                } else if (!editMode) {
                    // Для нового трека аудиофайл обязателен
                    throw new IOException("Аудиофайл не выбран");
                }
                
                // Кодируем и добавляем обложку
                if (coverFile != null) {
                    String coverData = encodeFileToBase64(coverFile);
                    request.put("coverData", coverData);
                } else {
                    request.put("coverData", "");
                }
                
                Logger.info("Отправка запроса на " + (editMode ? "обновление" : "добавление") + " трека: " + titleField.getText());
                out.println(request.toString());
                
                String response = in.readLine();
                JSONObject jsonResponse = new JSONObject(response);
                
                socket.close();
                
                javafx.application.Platform.runLater(() -> {
                    if (jsonResponse.getString("status").equals("OK")) {
                        Logger.info("Трек успешно " + (editMode ? "обновлен" : "добавлен") + ": " + titleField.getText());
                        Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                        successAlert.setTitle("Успех");
                        successAlert.setHeaderText(null);
                        successAlert.setContentText(jsonResponse.getString("message"));
                        successAlert.showAndWait();
                        close();
                    } else {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Ошибка");
                        alert.setHeaderText("Ошибка при сохранении трека");
                        alert.setContentText(jsonResponse.getString("message"));
                        alert.showAndWait();
                    }
                });
                
            } catch (Exception e) {
                Logger.error("Ошибка при сохранении трека: " + e.getMessage(), e);
                javafx.application.Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Ошибка");
                    alert.setHeaderText("Ошибка при сохранении трека");
                    alert.setContentText(e.getMessage());
                    alert.showAndWait();
                });
            }
        }).start();
    }
}
