import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
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
    
    private List<Client.MusicTrack> trackList;
    private int currentTrackIndex;
    private String serverAddress;
    private int serverPort;
    private MultipleSelectionModel<Client.MusicTrack> selectionModel;
    
    // Для управления перемоткой
    private boolean userIsAdjusting = false;
    private ChangeListener<Duration> timeChangeListener;
    private boolean seeking = false;
    
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
        stage.setTitle("Онлайн аудиоплеер");
        stage.setWidth(600);
        stage.setHeight(200);
        stage.setResizable(false);
        
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        
        // Заголовок трека
        trackTitleLabel = new Label(currentTrack.getTitle());
        trackTitleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        trackTitleLabel.setMaxWidth(Double.MAX_VALUE);
        trackTitleLabel.setAlignment(Pos.CENTER);
        
        Label artistLabel = new Label("Исполнитель: " + currentTrack.getArtist());
        artistLabel.setStyle("-fx-font-size: 12px;");
        
        // Панель с прогрессом и временем
        HBox progressBox = new HBox(10);
        progressBox.setAlignment(Pos.CENTER);
        
        progressSlider = new Slider(0, 100, 0);
        progressSlider.setPrefWidth(400);
        progressSlider.setDisable(true);
        
        // ИСПРАВЛЕННАЯ обработка перемотки
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
        
        // Обработка событий мыши для перемотки
        progressSlider.setOnMousePressed(e -> {
            userIsAdjusting = true;
        });
        
        progressSlider.setOnMouseReleased(e -> {
            if (mediaPlayer != null && mediaPlayer.getTotalDuration().greaterThan(Duration.ZERO)) {
                double seekTime = mediaPlayer.getTotalDuration().toSeconds() * (progressSlider.getValue() / 100.0);
                mediaPlayer.seek(Duration.seconds(seekTime));
                updateTimeLabelForSeek(seekTime);
            }
            userIsAdjusting = false;
            seeking = false;
        });
        
        // Для клика на ползунок (без перетаскивания)
        progressSlider.setOnMouseClicked(e -> {
            if (!progressSlider.isValueChanging() && mediaPlayer != null && mediaPlayer.getTotalDuration().greaterThan(Duration.ZERO)) {
                double seekTime = mediaPlayer.getTotalDuration().toSeconds() * (progressSlider.getValue() / 100.0);
                mediaPlayer.seek(Duration.seconds(seekTime));
                updateTimeLabelForSeek(seekTime);
            }
        });
        
        timeLabel = new Label("00:00 / " + currentTrack.getDuration());
        timeLabel.setMinWidth(100);
        
        progressBox.getChildren().addAll(progressSlider, timeLabel);
        
        // Панель управления
        HBox controlsBox = new HBox(15);
        controlsBox.setAlignment(Pos.CENTER);
        
        previousButton = new Button("⏮ Предыдущий");
        previousButton.setDisable(!hasPreviousTrack());
        
        playPauseButton = new Button("▶ Воспроизвести");
        playPauseButton.setDisable(true);
        
        Button stopButton = new Button("⏹ Стоп");
        
        nextButton = new Button("Следующий ⏭");
        nextButton.setDisable(!hasNextTrack());
        
        controlsBox.getChildren().addAll(previousButton, playPauseButton, stopButton, nextButton);
        
        // Обработчики событий
        playPauseButton.setOnAction(e -> togglePlayPause());
        stopButton.setOnAction(e -> stop());
        previousButton.setOnAction(e -> playPreviousTrack());
        nextButton.setOnAction(e -> playNextTrack());
        
        root.getChildren().addAll(trackTitleLabel, artistLabel, progressBox, controlsBox);
        
        // Обработка закрытия окна
        stage.setOnCloseRequest(e -> {
            stopAudio();
        });
        
        Scene scene = new Scene(root);
        stage.setScene(scene);
        
        // Синхронизируем выделение в списке
        selectInList(currentTrackIndex);
    }
    
    private void loadAndPlayTrack() {
        new Thread(() -> {
            try {
                // Получаем имя файла с сервера
                Socket infoSocket = new Socket(serverAddress, serverPort);
                PrintWriter infoOut = new PrintWriter(infoSocket.getOutputStream(), true);
                BufferedReader infoIn = new BufferedReader(new InputStreamReader(infoSocket.getInputStream()));
                
                infoOut.println("GET_FILE_INFO:" + currentTrack.getId());
                String filename = infoIn.readLine();
                
                infoSocket.close();
                
                if (filename.startsWith("ERROR:")) {
                    Platform.runLater(() -> {
                        showError("Файл не найден на сервере");
                    });
                    return;
                }
                
                // Создаем соединение для загрузки файла
                Socket audioSocket = new Socket(serverAddress, serverPort);
                PrintWriter audioOut = new PrintWriter(audioSocket.getOutputStream(), true);
                BufferedReader audioIn = new BufferedReader(new InputStreamReader(audioSocket.getInputStream()));
                
                audioOut.println("GET_FILE:" + filename);
                
                String response = audioIn.readLine();
                if (!response.startsWith("FILE_SIZE:")) {
                    Platform.runLater(() -> {
                        showError("Ошибка получения файла: " + response);
                    });
                    audioSocket.close();
                    return;
                }
                
                long fileSize = Long.parseLong(response.substring(10));
                
                // Создаем временный файл
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
                
                // Загружаем и воспроизводим трек
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
                            
                            // Автоматически начинаем воспроизведение
                            mediaPlayer.play();
                            playPauseButton.setText("⏸ Пауза");
                            
                            // Обновляем слушатель времени
                            setupTimeListener();
                        });
                        
                        mediaPlayer.setOnEndOfMedia(() -> {
                            playPauseButton.setText("▶ Воспроизвести");
                            // Автоматически переходим к следующему треку
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
                Platform.runLater(() -> {
                    showError("Ошибка загрузки трека: " + e.getMessage());
                });
            }
        }).start();
    }
    
    private void setupTimeListener() {
        // Удаляем предыдущий слушатель если есть
        if (timeChangeListener != null && mediaPlayer != null) {
            mediaPlayer.currentTimeProperty().removeListener(timeChangeListener);
        }
        
        // Создаем новый слушатель
        timeChangeListener = (observable, oldValue, newValue) -> {
            Platform.runLater(() -> {
                if (!seeking && mediaPlayer != null && 
                    mediaPlayer.getTotalDuration().greaterThan(Duration.ZERO)) {
                    double currentTime = mediaPlayer.getCurrentTime().toSeconds();
                    double totalTime = mediaPlayer.getTotalDuration().toSeconds();
                    double progress = (currentTime / totalTime) * 100.0;
                    
                    // Обновляем ползунок только если пользователь его не двигает
                    if (!userIsAdjusting) {
                        progressSlider.setValue(progress);
                    }
                }
                updateTimeLabel();
            });
        };
        
        // Добавляем слушатель
        if (mediaPlayer != null) {
            mediaPlayer.currentTimeProperty().addListener(timeChangeListener);
        }
    }
    
    public void loadTrack(Client.MusicTrack track, int trackIndex, List<Client.MusicTrack> trackList) {
        this.currentTrack = track;
        this.currentTrackIndex = trackIndex;
        this.trackList = trackList;
        
        Platform.runLater(() -> {
            trackTitleLabel.setText(track.getTitle());
            previousButton.setDisable(!hasPreviousTrack());
            nextButton.setDisable(!hasNextTrack());
            progressSlider.setValue(0);
            timeLabel.setText("00:00 / " + track.getDuration());
            playPauseButton.setDisable(true);
            playPauseButton.setText("▶ Воспроизвести");
            seeking = false;
            userIsAdjusting = false;
            
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.dispose();
                mediaPlayer = null;
            }
            
            // Синхронизируем выделение в списке
            selectInList(trackIndex);
        });
        
        loadAndPlayTrack();
    }
    
    private void togglePlayPause() {
        if (mediaPlayer == null) return;
        
        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
            playPauseButton.setText("▶ Воспроизвести");
        } else {
            mediaPlayer.play();
            playPauseButton.setText("⏸ Пауза");
        }
    }
    
    private void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            playPauseButton.setText("▶ Воспроизвести");
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