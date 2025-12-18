import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.image.*;
import javafx.stage.Stage;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.beans.value.ChangeListener;
import javafx.scene.input.ScrollEvent;
import org.json.*;
import java.io.*;
import java.net.Socket;
import java.util.List;

public class AudioPlayerWindow {
    private Stage stage;
    private MediaPlayer mediaPlayer;
    private Client.MusicTrack currentTrack;
    private Label timeLabel;
    private Slider progressSlider;
    private Slider volumeSlider;
    private Button playPauseButton;
    private Button previousButton;
    private Button nextButton;
    private Label trackTitleLabel;
    private Label artistLabel;
    private ImageView coverImageView;
    
    private List<Client.MusicTrack> trackList;
    private int currentTrackIndex;
    private String serverAddress;
    private int serverPort;
    private MultipleSelectionModel<Client.MusicTrack> selectionModel;
    
    private boolean userIsAdjusting = false;
    private ChangeListener<Duration> timeChangeListener;
    private boolean seeking = false;
    
    private static final int COVER_SIZE = 320;
    private static final int WINDOW_WIDTH = COVER_SIZE;
    private static final int WINDOW_HEIGHT = 550;
    
    // Кэш для обложек
    private static final java.util.Map<String, Image> coverCache = new java.util.HashMap<>();
    
    public AudioPlayerWindow(Client.MusicTrack track, int trackIndex, 
                           List<Client.MusicTrack> trackList, 
                           String serverAddress, int serverPort,
                           MultipleSelectionModel<Client.MusicTrack> selectionModel) {
        Logger.info("Создание AudioPlayerWindow для трека: " + track.getTitle());
        
        this.currentTrack = track;
        this.currentTrackIndex = trackIndex;
        this.trackList = trackList;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.selectionModel = selectionModel;
        
        createWindow();
        loadAndPlayTrack();
    }
    
    private void createWindow() {
        Logger.debug("Создание окна аудиоплеера");
        
        stage = new Stage();
        
        // Устанавливаем иконку приложения
        try {
            String iconPath = "src/icon.png";
            File iconFile = new File(iconPath);
            if (iconFile.exists()) {
                stage.getIcons().add(new Image("file:" + iconPath));
                Logger.debug("Иконка приложения загружена: " + iconPath);
            } else {
                // Попробуем альтернативный путь
                iconPath = "icon.png";
                iconFile = new File(iconPath);
                if (iconFile.exists()) {
                    stage.getIcons().add(new Image("file:" + iconPath));
                    Logger.debug("Иконка приложения загружена: " + iconPath);
                }
            }
        } catch (Exception e) {
            Logger.error("Ошибка при загрузке иконки: " + e.getMessage());
        }
        
        stage.setTitle(currentTrack.getTitle());
        stage.setWidth(WINDOW_WIDTH);
        stage.setHeight(WINDOW_HEIGHT);
        stage.setResizable(false);
        
        // Основной контейнер
        VBox root = new VBox(15);
        root.setPadding(new Insets(0, 15, 25, 15));
        root.setAlignment(Pos.TOP_CENTER);
        
        // Обложка
        coverImageView = new ImageView();
        coverImageView.setFitWidth(COVER_SIZE);
        coverImageView.setFitHeight(COVER_SIZE);
        coverImageView.setPreserveRatio(true);
        coverImageView.setSmooth(true);
        coverImageView.setCache(true);
        
        // Контейнер для текста
        VBox textContainer = new VBox(8);
        textContainer.setAlignment(Pos.CENTER);
        textContainer.setMaxWidth(COVER_SIZE);
        
        trackTitleLabel = new Label(currentTrack.getTitle());
        trackTitleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        trackTitleLabel.setWrapText(true);
        trackTitleLabel.setMaxWidth(COVER_SIZE);
        trackTitleLabel.setAlignment(Pos.CENTER);
        
        artistLabel = new Label(currentTrack.getArtist());
        artistLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");
        artistLabel.setWrapText(true);
        artistLabel.setMaxWidth(COVER_SIZE);
        artistLabel.setAlignment(Pos.CENTER);
        
        textContainer.getChildren().addAll(trackTitleLabel, artistLabel);
        
        // Контейнер для прогресса
        VBox progressContainer = new VBox(10);
        progressContainer.setAlignment(Pos.CENTER);
        progressContainer.setMaxWidth(COVER_SIZE);
        
        // Ползунок и время в одной строке
        HBox progressBox = new HBox(10);
        progressBox.setAlignment(Pos.CENTER);
        
        progressSlider = new Slider(0, 100, 0);
        progressSlider.setPrefWidth(COVER_SIZE - 90);
        progressSlider.setDisable(true);
        
        timeLabel = new Label("00:00 / " + currentTrack.getDuration());
        timeLabel.setMinWidth(85);
        timeLabel.setStyle("-fx-font-size: 12px;");
        
        progressBox.getChildren().addAll(progressSlider, timeLabel);
        
        // Контейнер для кнопок управления
        VBox controlsContainer = new VBox(15);
        controlsContainer.setAlignment(Pos.CENTER);
        controlsContainer.setMaxWidth(COVER_SIZE);
        
        // Основные кнопки управления (play/pause/stop)
        HBox mainControls = new HBox(10);
        mainControls.setAlignment(Pos.CENTER);
        
        previousButton = new Button("⏮");
        previousButton.setDisable(!hasPreviousTrack());
        previousButton.setPrefWidth(50);
        
        playPauseButton = new Button("▶");
        playPauseButton.setDisable(true);
        playPauseButton.setPrefWidth(50);
        
        Button stopButton = new Button("⏹");
        stopButton.setPrefWidth(50);
        
        nextButton = new Button("⏭");
        nextButton.setDisable(!hasNextTrack());
        nextButton.setPrefWidth(50);
        
        mainControls.getChildren().addAll(previousButton, playPauseButton, stopButton, nextButton);
        
        // Контейнер для регулятора громкости
        HBox volumeContainer = new HBox(10);
        volumeContainer.setAlignment(Pos.CENTER);
        volumeContainer.setMaxWidth(COVER_SIZE);
        
        // Иконка громкости
        Label volumeIconLabel = new Label("🔊");
        volumeIconLabel.setStyle("-fx-font-size: 16px;");
        
        // Слайдер громкости
        volumeSlider = new Slider(0, 100, 80);
        volumeSlider.setPrefWidth(COVER_SIZE - 60);
        volumeSlider.setShowTickLabels(false);
        volumeSlider.setShowTickMarks(false);
        
        // Обработчик изменения громкости
        volumeSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(newValue.doubleValue() / 100.0);
                Logger.debug("Изменение громкости: " + newValue + "%");
            }
        });
        
        // Обработка прокрутки мыши на слайдере громкости
        volumeSlider.addEventFilter(ScrollEvent.SCROLL, event -> {
            double delta = event.getDeltaY();
            double currentValue = volumeSlider.getValue();
            
            if (delta > 0) {
                volumeSlider.setValue(Math.min(100, currentValue + 5));
            } else {
                volumeSlider.setValue(Math.max(0, currentValue - 5));
            }
            event.consume();
        });
        
        volumeContainer.getChildren().addAll(volumeIconLabel, volumeSlider);
        
        // Добавляем все в контейнеры
        progressContainer.getChildren().addAll(progressBox, mainControls, volumeContainer);
        
        // Добавляем отступ между группами элементов
        Region spacer1 = new Region();
        VBox.setVgrow(spacer1, Priority.ALWAYS);
        
        Region spacer2 = new Region();
        VBox.setVgrow(spacer2, Priority.ALWAYS);
        
        root.getChildren().addAll(coverImageView, textContainer, spacer1, progressContainer, spacer2);
        
        // Обработчики событий
        playPauseButton.setOnAction(e -> togglePlayPause());
        stopButton.setOnAction(e -> stop());
        previousButton.setOnAction(e -> playPreviousTrack());
        nextButton.setOnAction(e -> playNextTrack());
        
        // Обработка перемотки
        progressSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (userIsAdjusting && mediaPlayer != null && mediaPlayer.getTotalDuration().greaterThan(Duration.ZERO)) {
                seeking = true;
                double seekTime = mediaPlayer.getTotalDuration().toSeconds() * (newValue.doubleValue() / 100.0);
                mediaPlayer.seek(Duration.seconds(seekTime));
                Logger.debug("Перемотка на: " + seekTime + " секунд");
                Platform.runLater(() -> {
                    updateTimeLabelForSeek(seekTime);
                });
            }
        });
        
        progressSlider.setOnMousePressed(e -> userIsAdjusting = true);
        progressSlider.setOnMouseReleased(e -> {
            if (mediaPlayer != null && mediaPlayer.getTotalDuration().greaterThan(Duration.ZERO)) {
                double seekTime = mediaPlayer.getTotalDuration().toSeconds() * (progressSlider.getValue() / 100.0);
                mediaPlayer.seek(Duration.seconds(seekTime));
                updateTimeLabelForSeek(seekTime);
            }
            userIsAdjusting = false;
            seeking = false;
        });
        
        progressSlider.setOnMouseClicked(e -> {
            if (!progressSlider.isValueChanging() && mediaPlayer != null && mediaPlayer.getTotalDuration().greaterThan(Duration.ZERO)) {
                double seekTime = mediaPlayer.getTotalDuration().toSeconds() * (progressSlider.getValue() / 100.0);
                mediaPlayer.seek(Duration.seconds(seekTime));
                updateTimeLabelForSeek(seekTime);
            }
        });
        
        stage.setOnCloseRequest(e -> stopAudio());
        
        Scene scene = new Scene(root);
        stage.setScene(scene);
        
        // Загружаем обложку немедленно
        loadCover(currentTrack.getCover(), currentTrack.getId());
        selectInList(currentTrackIndex);
        
        Logger.info("Окно аудиоплеера создано для трека: " + currentTrack.getTitle());
    }
    
    private void loadAndPlayTrack() {
        Logger.info("Загрузка и воспроизведение трека: " + currentTrack.getTitle() + " (ID: " + currentTrack.getId() + ")");
        
        new Thread(() -> {
            try {
                Logger.debug("Установка соединения с сервером: " + serverAddress + ":" + serverPort);
                Socket socket = new Socket(serverAddress, serverPort);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                // Отправляем запрос на получение аудиофайла по ID
                JSONObject request = new JSONObject();
                request.put("command", "GET_FILE_BY_ID");
                request.put("id", currentTrack.getId());
                out.println(request.toString());
                Logger.debug("Отправлен запрос на получение файла с ID: " + currentTrack.getId());
                
                // Получаем JSON ответ о файле
                String response = in.readLine();
                JSONObject jsonResponse = new JSONObject(response);
                
                if (!jsonResponse.getString("status").equals("FILE")) {
                    String errorMsg = "Ошибка получения файла: " + 
                        (jsonResponse.has("message") ? jsonResponse.getString("message") : "Неизвестная ошибка");
                    Logger.error(errorMsg);
                    Platform.runLater(() -> showError(errorMsg));
                    socket.close();
                    return;
                }
                
                long fileSize = jsonResponse.getLong("size");
                Logger.info("Получение аудиофайла размером: " + fileSize + " байт");
                
                File tempFile = File.createTempFile("stream_", ".mp3");
                tempFile.deleteOnExit();
                Logger.debug("Создан временный файл: " + tempFile.getAbsolutePath());
                
                try (FileOutputStream fos = new FileOutputStream(tempFile);
                     InputStream is = socket.getInputStream()) {
                    
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalRead = 0;
                    
                    while (totalRead < fileSize && 
                           (bytesRead = is.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalRead))) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                    }
                    
                    Logger.debug("Аудиофайл загружен: " + totalRead + " из " + fileSize + " байт");
                }
                
                socket.close();
                Logger.debug("Соединение с сервером закрыто");
                
                Platform.runLater(() -> {
                    try {
                        String fileUrl = tempFile.toURI().toString();
                        Media media = new Media(fileUrl);
                        
                        if (mediaPlayer != null) {
                            Logger.debug("Остановка предыдущего медиаплеера");
                            mediaPlayer.stop();
                            mediaPlayer.dispose();
                        }
                        
                        mediaPlayer = new MediaPlayer(media);
                        Logger.info("Медиаплеер создан для трека: " + currentTrack.getTitle());
                        
                        // Устанавливаем начальную громкость
                        mediaPlayer.setVolume(volumeSlider.getValue() / 100.0);
                        
                        mediaPlayer.setOnReady(() -> {
                            Logger.debug("Медиа готово к воспроизведению");
                            playPauseButton.setDisable(false);
                            progressSlider.setDisable(false);
                            updateTimeLabel();
                            
                            mediaPlayer.play();
                            Logger.info("Начато воспроизведение трека: " + currentTrack.getTitle());
                            playPauseButton.setText("⏸");
                            setupTimeListener();
                        });
                        
                        mediaPlayer.setOnEndOfMedia(() -> {
                            Logger.debug("Трек завершен: " + currentTrack.getTitle());
                            playPauseButton.setText("▶");
                            Platform.runLater(() -> {
                                if (hasNextTrack()) {
                                    Logger.info("Автоматический переход к следующему треку");
                                    playNextTrack();
                                }
                            });
                        });
                        
                        mediaPlayer.setOnError(() -> {
                            String errorMsg = "Ошибка воспроизведения: " + 
                                    (mediaPlayer.getError() != null ? mediaPlayer.getError().getMessage() : "Неизвестная ошибка");
                            Logger.error(errorMsg);
                            showError(errorMsg);
                        });
                        
                    } catch (Exception e) {
                        String errorMsg = "Ошибка создания медиаплеера: " + e.getMessage();
                        Logger.error(errorMsg, e);
                        showError(errorMsg);
                    }
                });
                
            } catch (Exception e) {
                String errorMsg = "Ошибка загрузки трека: " + e.getMessage();
                Logger.error(errorMsg, e);
                Platform.runLater(() -> showError(errorMsg));
            }
        }).start();
    }
    
    private void loadCover(String coverFilename, String trackId) {
        Logger.debug("Загрузка обложки: " + coverFilename + " для трека ID: " + trackId);
        
        if (coverFilename == null || coverFilename.isEmpty() || coverFilename.equals("-")) {
            Logger.debug("Используется обложка по умолчанию для трека ID: " + trackId);
            loadDefaultCover(trackId);
        } else {
            // Проверяем кэш
            String cacheKey = trackId + "_" + coverFilename;
            if (coverCache.containsKey(cacheKey)) {
                Logger.debug("Обложка найдена в кэше для трека ID: " + trackId);
                Image cachedImage = coverCache.get(cacheKey);
                coverImageView.setImage(cachedImage);
            } else {
                loadCoverFile(coverFilename, trackId);
            }
        }
    }
    
    private void loadCoverFile(String coverFilename, String trackId) {
        Logger.debug("Загрузка файла обложки: " + coverFilename + " для трека ID: " + trackId);
        
        new Thread(() -> {
            try {
                Socket socket = new Socket(serverAddress, serverPort);
                socket.setSoTimeout(10000); // Таймаут 10 секунд
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                // Отправляем запрос на получение обложки
                JSONObject request = new JSONObject();
                request.put("command", "GET_COVER");
                request.put("coverFilename", coverFilename);
                out.println(request.toString());
                Logger.debug("Отправлен запрос на получение обложки: " + coverFilename);
                
                // Получаем JSON ответ о файле
                String response = in.readLine();
                if (response == null) {
                    Logger.warning("Пустой ответ от сервера при запросе обложки: " + coverFilename);
                    Platform.runLater(() -> loadDefaultCover(trackId));
                    socket.close();
                    return;
                }
                
                JSONObject jsonResponse = new JSONObject(response);
                
                if (!jsonResponse.getString("status").equals("FILE")) {
                    Logger.warning("Не удалось получить обложку: " + coverFilename + ", статус: " + jsonResponse.getString("status"));
                    Platform.runLater(() -> loadDefaultCover(trackId));
                    socket.close();
                    return;
                }
                
                long fileSize = jsonResponse.getLong("size");
                Logger.debug("Получение обложки размером: " + fileSize + " байт");
                
                File tempFile = File.createTempFile("cover_", ".png");
                tempFile.deleteOnExit();
                
                try (FileOutputStream fos = new FileOutputStream(tempFile);
                     InputStream is = socket.getInputStream()) {
                    
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalRead = 0;
                    
                    while (totalRead < fileSize && 
                           (bytesRead = is.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalRead))) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                    }
                    
                    Logger.debug("Обложка загружена: " + totalRead + " из " + fileSize + " байт");
                }
                
                socket.close();
                
                Platform.runLater(() -> {
                    try {
                        Image image = new Image(tempFile.toURI().toString(), COVER_SIZE, COVER_SIZE, true, true, true);
                        
                        // Кэшируем изображение
                        String cacheKey = trackId + "_" + coverFilename;
                        coverCache.put(cacheKey, image);
                        
                        // Проверяем, что это все еще актуальный трек
                        if (currentTrack != null && currentTrack.getId().equals(trackId)) {
                            coverImageView.setImage(image);
                            Logger.debug("Обложка установлена: " + coverFilename + " для трека ID: " + trackId);
                        } else {
                            Logger.debug("Обложка загружена для другого трека, игнорируем");
                        }
                    } catch (Exception e) {
                        Logger.error("Ошибка загрузки обложки: " + e.getMessage());
                        loadDefaultCover(trackId);
                    }
                });
                
            } catch (Exception e) {
                Logger.error("Ошибка загрузки обложки: " + e.getMessage(), e);
                Platform.runLater(() -> loadDefaultCover(trackId));
            }
        }).start();
    }
    
    private void loadDefaultCover(String trackId) {
        Logger.debug("Создание обложки по умолчанию для трека ID: " + trackId);
        
        Platform.runLater(() -> {
            if (currentTrack != null && currentTrack.getId().equals(trackId)) {
                javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(COVER_SIZE, COVER_SIZE);
                javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();
                
                // Градиентный фон
                for (int y = 0; y < COVER_SIZE; y++) {
                    for (int x = 0; x < COVER_SIZE; x++) {
                        double r = 0.1 + (0.3 * x / COVER_SIZE);
                        double g = 0.1 + (0.3 * y / COVER_SIZE);
                        double b = 0.4;
                        gc.setFill(javafx.scene.paint.Color.color(r, g, b));
                        gc.fillRect(x, y, 1, 1);
                    }
                }
                
                // Текст
                gc.setFill(javafx.scene.paint.Color.WHITE);
                gc.setFont(javafx.scene.text.Font.font("Arial", 18));
                
                String title = currentTrack.getTitle();
                if (title.length() > 20) title = title.substring(0, 17) + "...";
                gc.fillText(title, COVER_SIZE/2 - 50, COVER_SIZE/2 - 10);
                
                String artist = currentTrack.getArtist();
                if (artist.length() > 25) artist = artist.substring(0, 22) + "...";
                gc.fillText(artist, COVER_SIZE/2 - 60, COVER_SIZE/2 + 20);
                
                coverImageView.setImage(canvas.snapshot(null, null));
                Logger.debug("Обложка по умолчанию создана для трека ID: " + trackId);
            }
        });
    }
    
    private void setupTimeListener() {
        Logger.debug("Настройка слушателя времени воспроизведения");
        
        if (timeChangeListener != null && mediaPlayer != null) {
            mediaPlayer.currentTimeProperty().removeListener(timeChangeListener);
        }
        
        timeChangeListener = (observable, oldValue, newValue) -> {
            Platform.runLater(() -> {
                if (!seeking && mediaPlayer != null && 
                    mediaPlayer.getTotalDuration().greaterThan(Duration.ZERO)) {
                    double currentTime = mediaPlayer.getCurrentTime().toSeconds();
                    double totalTime = mediaPlayer.getTotalDuration().toSeconds();
                    double progress = (currentTime / totalTime) * 100.0;
                    
                    if (!userIsAdjusting) {
                        progressSlider.setValue(progress);
                    }
                }
                updateTimeLabel();
            });
        };
        
        if (mediaPlayer != null) {
            mediaPlayer.currentTimeProperty().addListener(timeChangeListener);
        }
    }
    
    public void loadTrack(Client.MusicTrack track, int trackIndex, List<Client.MusicTrack> trackList) {
        Logger.info("Загрузка нового трека: " + track.getTitle() + " (индекс: " + trackIndex + ")");
        
        this.currentTrack = track;
        this.currentTrackIndex = trackIndex;
        this.trackList = trackList;
        
        Platform.runLater(() -> {
            stage.setTitle(track.getTitle());
            trackTitleLabel.setText(track.getTitle());
            artistLabel.setText(track.getArtist());
            previousButton.setDisable(!hasPreviousTrack());
            nextButton.setDisable(!hasNextTrack());
            progressSlider.setValue(0);
            timeLabel.setText("00:00 / " + track.getDuration());
            playPauseButton.setDisable(true);
            playPauseButton.setText("▶");
            seeking = false;
            userIsAdjusting = false;
            
            // Загружаем обложку немедленно
            loadCover(track.getCover(), track.getId());
            
            if (mediaPlayer != null) {
                Logger.debug("Остановка предыдущего медиаплеера");
                mediaPlayer.stop();
                mediaPlayer.dispose();
                mediaPlayer = null;
            }
            
            selectInList(trackIndex);
        });
        
        loadAndPlayTrack();
    }
    
    private void togglePlayPause() {
        if (mediaPlayer == null) return;
        
        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
            Logger.debug("Пауза воспроизведения");
            playPauseButton.setText("▶");
        } else {
            mediaPlayer.play();
            Logger.debug("Возобновление воспроизведения");
            playPauseButton.setText("⏸");
        }
    }
    
    private void stop() {
        Logger.debug("Остановка воспроизведения");
        
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            playPauseButton.setText("▶");
            progressSlider.setValue(0);
            updateTimeLabel();
            seeking = false;
            userIsAdjusting = false;
        }
    }
    
    private void playPreviousTrack() {
        Logger.info("Запрос на воспроизведение предыдущего трека");
        
        if (hasPreviousTrack()) {
            int prevIndex = currentTrackIndex - 1;
            Client.MusicTrack prevTrack = trackList.get(prevIndex);
            Logger.debug("Переход к предыдущему треку: " + prevTrack.getTitle());
            loadTrack(prevTrack, prevIndex, trackList);
        }
    }
    
    private void playNextTrack() {
        Logger.info("Запрос на воспроизведение следующего трека");
        
        if (hasNextTrack()) {
            int nextIndex = currentTrackIndex + 1;
            Client.MusicTrack nextTrack = trackList.get(nextIndex);
            Logger.debug("Переход к следующему треку: " + nextTrack.getTitle());
            loadTrack(nextTrack, nextIndex, trackList);
        }
    }
    
    private boolean hasPreviousTrack() {
        return currentTrackIndex > 0;
    }
    
    private boolean hasNextTrack() {
        return currentTrackIndex < trackList.size() - 1;
    }
    
    private void updateTimeLabel() {
        if (mediaPlayer != null && mediaPlayer.getTotalDuration().greaterThan(Duration.ZERO)) {
            Duration currentTime = mediaPlayer.getCurrentTime();
            Duration totalTime = mediaPlayer.getTotalDuration();
            
            String current = formatTime(currentTime);
            String total = formatTime(totalTime);
            
            timeLabel.setText(current + " / " + total);
        } else {
            timeLabel.setText("00:00 / " + currentTrack.getDuration());
        }
    }
    
    private void updateTimeLabelForSeek(double seconds) {
        if (mediaPlayer != null && mediaPlayer.getTotalDuration().greaterThan(Duration.ZERO)) {
            Duration totalTime = mediaPlayer.getTotalDuration();
            Duration seekTime = Duration.seconds(seconds);
            
            String current = formatTime(seekTime);
            String total = formatTime(totalTime);
            
            timeLabel.setText(current + " / " + total);
        }
    }
    
    private String formatTime(Duration duration) {
        int minutes = (int) duration.toMinutes();
        int seconds = (int) duration.toSeconds() % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    private void showError(String message) {
        Logger.error("Показать ошибку пользователю: " + message);
        
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    public void stopAudio() {
        Logger.info("Остановка аудио и очистка ресурсов");
        
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        
        // Очищаем кэш при закрытии окна
        coverCache.clear();
    }
    
    public void show() {
        Logger.info("Показ окна аудиоплеера");
        stage.show();
    }
    
    public boolean isShowing() {
        return stage != null && stage.isShowing();
    }
    
    public void selectInList(int index) {
        Logger.debug("Выбор трека в списке по индексу: " + index);
        
        Platform.runLater(() -> {
            if (selectionModel != null) {
                selectionModel.select(index);
            }
        });
    }
}