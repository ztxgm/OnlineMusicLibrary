import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.json.JSONObject;
import java.io.*;
import java.util.Base64;

public class TrackEditDialog extends Stage {
    private TextField idField;
    private TextField titleField;
    private TextField durationField;
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
        setTitle("Редактировать трек");
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
        grid.add(idField, 1, 0);
        
        // Название
        grid.add(new Label("Название:"), 0, 1);
        titleField = new TextField();
        titleField.setPromptText("Название трека");
        grid.add(titleField, 1, 1);
        
        // Длительность
        grid.add(new Label("Длительность:"), 0, 2);
        durationField = new TextField();
        durationField.setPromptText("мм:сс (например, 3:45)");
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
        audioFileField.setPromptText("Выберите MP3 файл");
        audioFileField.setPrefWidth(200);
        browseAudioButton = new Button("Обзор...");
        audioBox.getChildren().addAll(audioFileField, browseAudioButton);
        grid.add(audioBox, 1, 4);
        
        // Обложка
        grid.add(new Label("Обложка:"), 0, 5);
        HBox coverBox = new HBox(5);
        coverFileField = new TextField();
        coverFileField.setPromptText("Выберите изображение (PNG/JPG)");
        coverFileField.setPrefWidth(200);
        browseCoverButton = new Button("Обзор...");
        coverBox.getChildren().addAll(coverFileField, browseCoverButton);
        grid.add(coverBox, 1, 5);
        
        // Кнопки
        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(20, 0, 0, 0));
        saveButton = new Button(editMode ? "Сохранить" : "Добавить");
        cancelButton = new Button("Отмена");
        buttonBox.getChildren().addAll(saveButton, cancelButton);
        grid.add(buttonBox, 1, 6);
        
        // Обработчики событий
        browseAudioButton.setOnAction(e -> browseAudioFile());
        browseCoverButton.setOnAction(e -> browseCoverFile());
        saveButton.setOnAction(e -> saveTrack());
        cancelButton.setOnAction(e -> close());
        
        Scene scene = new Scene(grid, 400, 300);
        setScene(scene);
    }
    
    private void fillFields(Client.MusicTrack track) {
        idField.setText(track.getId());
        idField.setDisable(true); // Нельзя менять ID при редактировании
        titleField.setText(track.getTitle());
        durationField.setText(track.getDuration());
        artistField.setText(track.getArtist());
        audioFileField.setText(track.getFilename());
        coverFileField.setText(track.getCover());
    }
    
    private void browseAudioFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите аудиофайл");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Аудиофайлы", "*.mp3", "*.wav", "*.ogg"),
            new FileChooser.ExtensionFilter("Все файлы", "*.*")
        );
        
        File selectedFile = fileChooser.showOpenDialog(this);
        if (selectedFile != null) {
            audioFile = selectedFile;
            audioFileField.setText(selectedFile.getName());
            
            // Автоматически заполняем имя файла обложки
            if (!editMode && (coverFileField.getText() == null || coverFileField.getText().isEmpty())) {
                String baseName = selectedFile.getName().replaceFirst("[.][^.]+$", "");
                coverFileField.setText(baseName + ".png");
            }
        }
    }
    
    private void browseCoverFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите обложку");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.jpeg"),
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
            durationField.getText().isEmpty() || artistField.getText().isEmpty() ||
            audioFileField.getText().isEmpty()) {
            
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText("Не все поля заполнены");
            alert.setContentText("Пожалуйста, заполните все обязательные поля.");
            alert.showAndWait();
            return;
        }
        
        // Проверка формата длительности
        if (!durationField.getText().matches("\\d+:\\d{2}")) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText("Некорректный формат длительности");
            alert.setContentText("Введите длительность в формате мм:сс (например, 3:45)");
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
                    request.put("id", idField.getText());
                } else {
                    request.put("command", "ADD_TRACK");
                    request.put("id", idField.getText());
                }
                
                request.put("title", titleField.getText());
                request.put("duration", durationField.getText());
                request.put("artist", artistField.getText());
                request.put("audioFilename", audioFileField.getText());
                request.put("coverFilename", coverFileField.getText().isEmpty() ? "-" : coverFileField.getText());
                
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
                
                out.println(request.toString());
                
                String response = in.readLine();
                JSONObject jsonResponse = new JSONObject(response);
                
                socket.close();
                
                javafx.application.Platform.runLater(() -> {
                    if (jsonResponse.getString("status").equals("OK")) {
                        Logger.info("Трек успешно " + (editMode ? "обновлен" : "добавлен"));
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
