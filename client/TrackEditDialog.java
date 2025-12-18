import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.image.Image;
import org.json.JSONObject;
import java.io.*;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TrackEditDialog extends Stage {
    private TextField idField;
    private TextField titleField;
    private TextField durationField;
    private TextField artistField;
    private TextField audioFileField;
    private TextField coverFileField;
    private Button browseAudioButton;
    private Button browseCoverButton;
    private Button editDurationButton;
    private Button saveButton;
    private Button cancelButton;
    
    private File audioFile;
    private File coverFile;
    
    private boolean editMode = false;
    private Client.MusicTrack originalTrack;
    
    private String serverAddress;
    private int serverPort;
    
    private boolean durationDetermined = false;
    private boolean durationFieldEnabled = false; // Флаг для отслеживания состояния поля
    
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
        idField.setDisable(editMode);
        grid.add(idField, 1, 0);
        
        // Название
        grid.add(new Label("Название:"), 0, 1);
        titleField = new TextField();
        titleField.setPromptText("Название трека");
        grid.add(titleField, 1, 1);
        
        // Длительность
        grid.add(new Label("Длительность:"), 0, 2);
        HBox durationBox = new HBox(5);
        durationField = new TextField();
        durationField.setPromptText("мм:сс (например, 3:45)");
        durationField.setPrefWidth(150);
        durationField.setEditable(false); // ПОЛЕ ИЗНАЧАЛЬНО НЕАКТИВНО
        durationField.setStyle("-fx-background-color: #f0f0f0; -fx-text-fill: #666;");
        
        editDurationButton = new Button("Изменить");
        editDurationButton.setDisable(true); // Кнопка изначально неактивна
        editDurationButton.setOnAction(e -> {
            // Активируем поле для редактирования
            durationField.setEditable(true);
            durationField.setStyle(""); // Убираем серый фон
            durationField.requestFocus();
            durationFieldEnabled = true;
            editDurationButton.setDisable(true); // Кнопка становится неактивной после нажатия
            editDurationButton.setText("Изменяется...");
        });
        
        durationBox.getChildren().addAll(durationField, editDurationButton);
        grid.add(durationBox, 1, 2);
        
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
        browseAudioButton.setOnAction(e -> {
            browseAudioFile();
            // После выбора файла автоматически определяем длительность
            if (audioFile != null && audioFile.exists()) {
                determineAudioDuration();
            }
        });
        
        browseCoverButton.setOnAction(e -> browseCoverFile());
        saveButton.setOnAction(e -> saveTrack());
        cancelButton.setOnAction(e -> close());
        
        Scene scene = new Scene(grid, 550, 350);
        setScene(scene);
        
        // Устанавливаем иконку приложения
        try {
            String iconPath = "src/icon.png";
            File iconFile = new File(iconPath);
            if (iconFile.exists()) {
                this.getIcons().add(new Image("file:" + iconPath));
                Logger.debug("Иконка приложения загружена: " + iconPath);
            } else {
                // Попробуем альтернативный путь
                iconPath = "icon.png";
                iconFile = new File(iconPath);
                if (iconFile.exists()) {
                    this.getIcons().add(new Image("file:" + iconPath));
                    Logger.debug("Иконка приложения загружена: " + iconPath);
                }
            }
        } catch (Exception e) {
            Logger.error("Ошибка при загрузке иконки: " + e.getMessage());
        }
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
            
            // Сбрасываем состояние поля длительности
            durationField.setEditable(false);
            durationField.setStyle("-fx-background-color: #f0f0f0; -fx-text-fill: #666;");
            durationFieldEnabled = false;
            editDurationButton.setText("Изменить");
            
            // Автоматически заполняем имя файла обложки
            if (!editMode && (coverFileField.getText() == null || coverFileField.getText().isEmpty())) {
                String baseName = selectedFile.getName().replaceFirst("[.][^.]+$", "");
                coverFileField.setText(baseName + ".png");
            }
            
            // Сбрасываем флаг определения длительности
            durationDetermined = false;
        }
    }
    
    private void determineAudioDuration() {
        if (audioFile == null || !audioFile.exists()) {
            return;
        }
        
        if (durationDetermined) {
            return; // Уже определили
        }
        
        Logger.debug("Автоматическое определение длительности аудиофайла: " + audioFile.getName());
        
        // Показываем сообщение о процессе определения
        durationField.setText("Определяется...");
        editDurationButton.setDisable(true); // Кнопка неактивна во время определения
        
        // Определяем длительность в отдельном потоке
        new Thread(() -> {
            try {
                String duration = getAudioDurationWithFFmpeg(audioFile);
                durationDetermined = true;
                
                javafx.application.Platform.runLater(() -> {
                    if (duration != null && !duration.equals("00:00") && !duration.equals("Определяется...")) {
                        durationField.setText(duration);
                        durationField.setEditable(false); // ПОЛЕ ОСТАЕТСЯ НЕАКТИВНЫМ
                        durationField.setStyle("-fx-background-color: #f0f0f0; -fx-text-fill: #666;");
                        editDurationButton.setDisable(false); // Активируем кнопку "Изменить"
                        editDurationButton.setText("Изменить");
                        Logger.info("Автоматически определена длительность: " + duration + " для файла: " + audioFile.getName());
                    } else {
                        Logger.warning("Не удалось определить длительность файла автоматически: " + audioFile.getName());
                        durationField.setText("00:00");
                        durationField.setEditable(false); // ПОЛЕ ОСТАЕТСЯ НЕАКТИВНЫМ
                        durationField.setStyle("-fx-background-color: #f0f0f0; -fx-text-fill: #666;");
                        editDurationButton.setDisable(false); // Активируем кнопку "Изменить"
                        editDurationButton.setText("Изменить");
                        showAlert("Внимание", 
                            "Не удалось определить длительность автоматически. " +
                            "Нажмите кнопку 'Изменить', чтобы ввести длительность вручную.");
                    }
                });
                
            } catch (Exception e) {
                Logger.error("Ошибка при определении длительности: " + e.getMessage(), e);
                javafx.application.Platform.runLater(() -> {
                    durationField.setText("00:00");
                    durationField.setEditable(false); // ПОЛЕ ОСТАЕТСЯ НЕАКТИВНЫМ
                    durationField.setStyle("-fx-background-color: #f0f0f0; -fx-text-fill: #666;");
                    editDurationButton.setDisable(false); // Активируем кнопку "Изменить"
                    editDurationButton.setText("Изменить");
                    showAlert("Ошибка", 
                        "Не удалось определить длительность: " + e.getMessage() + 
                        "\nНажмите кнопку 'Изменить', чтобы ввести длительность вручную.");
                });
            }
        }).start();
    }
    
    private String getAudioDurationWithFFmpeg(File audioFile) {
        // Сначала пробуем получить длительность через ffprobe (основной способ)
        String duration = getDurationWithFFprobe(audioFile);
        if (duration != null && !duration.equals("00:00")) {
            return duration;
        }
        
        // Если ffprobe не сработал, пробуем через ffmpeg (альтернативный способ)
        duration = getDurationWithFFmpeg(audioFile);
        if (duration != null && !duration.equals("00:00")) {
            return duration;
        }
        
        return "00:00";
    }
    
    private String getDurationWithFFprobe(File audioFile) {
        Process process = null;
        try {
            // Команда для получения информации о файле через ffprobe
            ProcessBuilder pb = new ProcessBuilder(
                "ffprobe", 
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                audioFile.getAbsolutePath()
            );
            
            Logger.debug("Выполнение команды ffprobe: " + String.join(" ", pb.command()));
            
            process = pb.start();
            
            // Читаем вывод ffprobe
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    try {
                        double duration = Double.parseDouble(line.trim());
                        return formatDurationFromSeconds(duration);
                    } catch (NumberFormatException e) {
                        Logger.warning("Не удалось преобразовать длительность из ffprobe: " + line);
                        return null;
                    }
                }
            }
            
        } catch (Exception e) {
            Logger.error("Ошибка при использовании ffprobe: " + e.getMessage());
        } finally {
            if (process != null) {
                try {
                    boolean finished = process.waitFor(5, TimeUnit.SECONDS);
                    if (!finished) {
                        process.destroy();
                        Logger.warning("Процесс ffprobe не завершился за 5 секунд");
                    }
                } catch (InterruptedException e) {
                    Logger.error("Процесс ffprobe был прерван: " + e.getMessage());
                    process.destroy();
                }
            }
        }
        
        return null;
    }
    
    private String getDurationWithFFmpeg(File audioFile) {
        Process process = null;
        try {
            // Альтернативный способ, если ffprobe не сработал
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", 
                "-i", audioFile.getAbsolutePath(),
                "-f", "null", "-"
            );
            
            Logger.debug("Выполнение команды ffmpeg: " + String.join(" ", pb.command()));
            
            process = pb.start();
            BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream())
            );
            
            Pattern pattern = Pattern.compile("Duration: (\\d{2}):(\\d{2}):(\\d{2}\\.\\d+)");
            String line;
            
            while ((line = errorReader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    int hours = Integer.parseInt(matcher.group(1));
                    int minutes = Integer.parseInt(matcher.group(2));
                    double seconds = Double.parseDouble(matcher.group(3));
                    
                    double totalSeconds = hours * 3600 + minutes * 60 + seconds;
                    return formatDurationFromSeconds(totalSeconds);
                }
            }
            
        } catch (Exception e) {
            Logger.error("Ошибка при использовании ffmpeg: " + e.getMessage());
        } finally {
            if (process != null) {
                try {
                    boolean finished = process.waitFor(5, TimeUnit.SECONDS);
                    if (!finished) {
                        process.destroy();
                        Logger.warning("Процесс ffmpeg не завершился за 5 секунд");
                    }
                } catch (InterruptedException e) {
                    Logger.error("Процесс ffmpeg был прерван: " + e.getMessage());
                    process.destroy();
                }
            }
        }
        
        return null;
    }
    
    private String formatDurationFromSeconds(double totalSeconds) {
        try {
            int total = (int) Math.round(totalSeconds);
            int minutes = total / 60;
            int seconds = total % 60;
            
            return String.format("%d:%02d", minutes, seconds);
        } catch (Exception e) {
            Logger.error("Ошибка при форматировании секунд: " + e.getMessage());
            return "00:00";
        }
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
            durationField.getText().isEmpty() || artistField.getText().isEmpty() ||
            audioFileField.getText().isEmpty()) {
            
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText("Не все поля заполнены");
            alert.setContentText("Пожалуйста, заполните все обязательные поля (ID, название, длительность, исполнитель, аудиофайл).");
            alert.showAndWait();
            return;
        }
        
        // Проверка формата длительности
        String durationText = durationField.getText();
        if (!durationText.matches("\\d+:\\d{2}")) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText("Некорректный формат длительности");
            alert.setContentText("Введите длительность в формате мм:сс (например, 3:45)");
            alert.showAndWait();
            return;
        }
        
        // Проверка существования аудиофайла
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
    
    private void showAlert(String title, String message) {
        javafx.application.Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
