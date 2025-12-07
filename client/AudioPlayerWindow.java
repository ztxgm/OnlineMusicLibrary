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
import java.io.*;
import java.net.Socket;
import java.util.List;

public class AudioPlayerWindow {
    private Stage stage;
    private MediaPlayer mediaPlayer;
    private Client.MusicTrack currentTrack;
    private Label timeLabel;
    private Slider progressSlider;
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
    
    // Новые размеры
    private static final int COVER_SIZE = 320;  // Уменьшил для лучшего вписывания
    private static final int WINDOW_WIDTH = COVER_SIZE; // + 60 пока эксперемент
    private static final int WINDOW_HEIGHT = 520;  // Увеличил высоту
    
    public AudioPlayerWindow(Client.MusicTrack track, int trackIndex, 
                           List<Client.MusicTrack> trackList, 
                           String serverAddress, int serverPort,
                           MultipleSelectionModel<Client.MusicTrack> selectionModel) {
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
        stage = new Stage();
        stage.setTitle(currentTrack.getTitle());
        stage.setWidth(WINDOW_WIDTH);
        stage.setHeight(WINDOW_HEIGHT);
        stage.setResizable(false);
        
        // Основной контейнер
        VBox root = new VBox(15);
        root.setPadding(new Insets(0, 10, 25, 10));  // Увеличил нижний padding
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
        progressSlider.setPrefWidth(COVER_SIZE - 90);  // Уменьшил ширину для времени
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
        HBox mainControls = new HBox(15);
        mainControls.setAlignment(Pos.CENTER);
        
        previousButton = new Button("⏮");
        previousButton.setDisable(!hasPreviousTrack());
        previousButton.setPrefWidth(60);
        
        playPauseButton = new Button("▶");
        playPauseButton.setDisable(true);
        playPauseButton.setPrefWidth(60);
        
        Button stopButton = new Button("⏹");
        stopButton.setPrefWidth(60);
        
        nextButton = new Button("⏭");
        nextButton.setDisable(!hasNextTrack());
        nextButton.setPrefWidth(60);
        
        mainControls.getChildren().addAll(previousButton, playPauseButton, stopButton, nextButton);
        
        // Добавляем все в контейнеры
        progressContainer.getChildren().addAll(progressBox, mainControls);
        
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
        
        loadCover(currentTrack.getCover());
        selectInList(currentTrackIndex);
    }
    
    private void loadAndPlayTrack() {
        new Thread(() -> {
            try {
                Socket infoSocket = new Socket(serverAddress, serverPort);
                PrintWriter infoOut = new PrintWriter(infoSocket.getOutputStream(), true);
                BufferedReader infoIn = new BufferedReader(new InputStreamReader(infoSocket.getInputStream()));
                
                infoOut.println("GET_FILE_INFO:" + currentTrack.getId());
                String response = infoIn.readLine();
                infoSocket.close();
                
                if (response.startsWith("ERROR:")) {
                    Platform.runLater(() -> showError("Файл не найден на сервере"));
                    return;
                }
                
                String[] fileInfo = response.split(":");
                String audioFilename = fileInfo[0];
                
                Socket audioSocket = new Socket(serverAddress, serverPort);
                PrintWriter audioOut = new PrintWriter(audioSocket.getOutputStream(), true);
                BufferedReader audioIn = new BufferedReader(new InputStreamReader(audioSocket.getInputStream()));
                
                audioOut.println("GET_FILE:" + audioFilename);
                
                String fileResponse = audioIn.readLine();
                if (!fileResponse.startsWith("FILE_SIZE:")) {
                    Platform.runLater(() -> showError("Ошибка получения файла: " + fileResponse));
                    audioSocket.close();
                    return;
                }
                
                long fileSize = Long.parseLong(fileResponse.substring(10));
                
                File tempFile = File.createTempFile("stream_", ".mp3");
                tempFile.deleteOnExit();
                
                try (FileOutputStream fos = new FileOutputStream(tempFile);
                     InputStream is = audioSocket.getInputStream()) {
                    
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalRead = 0;
                    
                    while (totalRead < fileSize && 
                           (bytesRead = is.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalRead))) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                    }
                }
                
                audioSocket.close();
                
                Platform.runLater(() -> {
                    try {
                        String fileUrl = tempFile.toURI().toString();
                        Media media = new Media(fileUrl);
                        
                        if (mediaPlayer != null) {
                            mediaPlayer.stop();
                            mediaPlayer.dispose();
                        }
                        
                        mediaPlayer = new MediaPlayer(media);
                        
                        mediaPlayer.setOnReady(() -> {
                            playPauseButton.setDisable(false);
                            progressSlider.setDisable(false);
                            updateTimeLabel();
                            
                            mediaPlayer.play();
                            playPauseButton.setText("⏸");
                            setupTimeListener();
                        });
                        
                        mediaPlayer.setOnEndOfMedia(() -> {
                            playPauseButton.setText("▶");
                            Platform.runLater(() -> {
                                if (hasNextTrack()) {
                                    playNextTrack();
                                }
                            });
                        });
                        
                        mediaPlayer.setOnError(() -> {
                            showError("Ошибка воспроизведения: " + 
                                    (mediaPlayer.getError() != null ? mediaPlayer.getError().getMessage() : "Неизвестная ошибка"));
                        });
                        
                    } catch (Exception e) {
                        showError("Ошибка создания медиаплеера: " + e.getMessage());
                    }
                });
                
            } catch (Exception e) {
                Platform.runLater(() -> showError("Ошибка загрузки трека: " + e.getMessage()));
            }
        }).start();
    }
    
    private void loadCover(String coverFilename) {
        if (coverFilename == null || coverFilename.isEmpty() || coverFilename.equals("-")) {
            loadDefaultCover();
        } else {
            loadCoverFile(coverFilename);
        }
    }
    
    private void loadCoverFile(String coverFilename) {
        new Thread(() -> {
            try {
                Socket coverSocket = new Socket(serverAddress, serverPort);
                PrintWriter coverOut = new PrintWriter(coverSocket.getOutputStream(), true);
                BufferedReader coverIn = new BufferedReader(new InputStreamReader(coverSocket.getInputStream()));
                
                coverOut.println("GET_COVER:" + coverFilename);
                
                String response = coverIn.readLine();
                if (!response.startsWith("FILE_SIZE:")) {
                    Platform.runLater(() -> loadDefaultCover());
                    coverSocket.close();
                    return;
                }
                
                long fileSize = Long.parseLong(response.substring(10));
                
                File tempFile = File.createTempFile("cover_", ".png");
                tempFile.deleteOnExit();
                
                try (FileOutputStream fos = new FileOutputStream(tempFile);
                     InputStream is = coverSocket.getInputStream()) {
                    
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalRead = 0;
                    
                    while (totalRead < fileSize && 
                           (bytesRead = is.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalRead))) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                    }
                }
                
                coverSocket.close();
                
                Platform.runLater(() -> {
                    try {
                        Image image = new Image(tempFile.toURI().toString(), COVER_SIZE, COVER_SIZE, true, true, true);
                        coverImageView.setImage(image);
                    } catch (Exception e) {
                        loadDefaultCover();
                    }
                });
                
            } catch (Exception e) {
                Platform.runLater(() -> loadDefaultCover());
            }
        }).start();
    }
    
    private void loadDefaultCover() {
        // Создаем простую обложку по умолчанию
        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(COVER_SIZE, COVER_SIZE);
        javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();
        
        // Градиент
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
    }
    
    private void setupTimeListener() {
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
            
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.dispose();
                mediaPlayer = null;
            }
            
            loadCover(track.getCover());
            selectInList(trackIndex);
        });
        
        loadAndPlayTrack();
    }
    
    private void togglePlayPause() {
        if (mediaPlayer == null) return;
        
        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
            playPauseButton.setText("▶");
        } else {
            mediaPlayer.play();
            playPauseButton.setText("⏸");
        }
    }
    
    private void stop() {
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
        if (hasPreviousTrack()) {
            int prevIndex = currentTrackIndex - 1;
            Client.MusicTrack prevTrack = trackList.get(prevIndex);
            loadTrack(prevTrack, prevIndex, trackList);
        }
    }
    
    private void playNextTrack() {
        if (hasNextTrack()) {
            int nextIndex = currentTrackIndex + 1;
            Client.MusicTrack nextTrack = trackList.get(nextIndex);
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
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    public void stopAudio() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
    }
    
    public void show() {
        stage.show();
    }
    
    public boolean isShowing() {
        return stage != null && stage.isShowing();
    }
    
    public void selectInList(int index) {
        Platform.runLater(() -> {
            if (selectionModel != null) {
                selectionModel.select(index);
            }
        });
    }
}