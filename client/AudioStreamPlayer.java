import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import java.io.*;
import java.net.Socket;

public class AudioStreamPlayer {
    private Stage stage;
    private MediaPlayer mediaPlayer;
    private Client.MusicTrack track;
    private Label timeLabel;
    private Slider progressSlider;
    private Button playPauseButton;
    private File tempAudioFile;
    
    public AudioStreamPlayer(Client.MusicTrack track, Socket audioSocket, long fileSize) {
        this.track = track;
        createWindow();
        
        // Загружаем и воспроизводим в отдельном потоке
        new Thread(() -> {
            try {
                downloadAndStream(audioSocket, fileSize);
            } catch (Exception e) {
                e.printStackTrace();
                showError("Ошибка при потоковой передаче: " + e.getMessage());
            }
        }).start();
    }
    
    private void createWindow() {
        stage = new Stage();
        stage.setTitle("Онлайн стриминг - " + track.getTitle());
        stage.setWidth(400);
        stage.setHeight(200);
        
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setAlignment(Pos.CENTER);
        
        Label trackLabel = new Label(track.getTitle());
        trackLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        
        Label artistLabel = new Label("Исполнитель: " + track.getArtist());
        
        HBox controlsBox = new HBox(10);
        controlsBox.setAlignment(Pos.CENTER);
        
        playPauseButton = new Button("▶ Воспроизвести");
        playPauseButton.setDisable(true);
        Button stopButton = new Button("⏹ Стоп");
        
        progressSlider = new Slider(0, 100, 0);
        progressSlider.setPrefWidth(300);
        progressSlider.setDisable(true);
        
        timeLabel = new Label("00:00 / " + track.getDuration());
        
        controlsBox.getChildren().addAll(playPauseButton, stopButton);
        root.getChildren().addAll(trackLabel, artistLabel, progressSlider, timeLabel, controlsBox);
        
        playPauseButton.setOnAction(e -> togglePlayPause());
        stopButton.setOnAction(e -> stop());
        
        progressSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (mediaPlayer != null && progressSlider.isValueChanging()) {
                double totalDuration = mediaPlayer.getTotalDuration().toSeconds();
                double seekTime = totalDuration * (newValue.doubleValue() / 100.0);
                mediaPlayer.seek(Duration.seconds(seekTime));
            }
        });
        
        stage.setOnCloseRequest(e -> {
            cleanup();
        });
        
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
    }
    
    private void downloadAndStream(Socket audioSocket, long fileSize) {
        try {
            // Создаем временный файл
            tempAudioFile = File.createTempFile("stream_audio", ".mp3");
            tempAudioFile.deleteOnExit();
            
            // Считываем аудиоданные напрямую из InputStream (не через BufferedReader!)
            try (InputStream audioInput = audioSocket.getInputStream();
                 FileOutputStream fos = new FileOutputStream(tempAudioFile)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalRead = 0;
                
                while (totalRead < fileSize && (bytesRead = audioInput.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalRead))) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                    
                    // Обновляем прогресс
                    final double progress = (double) totalRead / fileSize;
                    javafx.application.Platform.runLater(() -> {
                        // Можно добавить прогресс-бар загрузки если нужно
                    });
                }
            }
            
            // Закрываем сокет
            audioSocket.close();
            
            // Создаем MediaPlayer после загрузки
            javafx.application.Platform.runLater(() -> {
                try {
                    String fileUrl = tempAudioFile.toURI().toString();
                    System.out.println("Воспроизведение файла: " + fileUrl);
                    
                    Media media = new Media(fileUrl);
                    mediaPlayer = new MediaPlayer(media);
                    
                    mediaPlayer.setOnReady(() -> {
                        System.out.println("Медиа готово к воспроизведению");
                        playPauseButton.setDisable(false);
                        progressSlider.setDisable(false);
                        updateTimeLabel();
                        
                        // Автоматически начинаем воспроизведение
                        mediaPlayer.play();
                        playPauseButton.setText("⏸ Пауза");
                    });
                    
                    mediaPlayer.currentTimeProperty().addListener((observable, oldValue, newValue) -> {
                        updateProgressSlider();
                        updateTimeLabel();
                    });
                    
                    mediaPlayer.setOnEndOfMedia(() -> {
                        playPauseButton.setText("▶ Воспроизвести");
                    });
                    
                    mediaPlayer.setOnError(() -> {
                        System.err.println("Ошибка MediaPlayer: " + mediaPlayer.getError());
                        showError("Ошибка воспроизведения: " + mediaPlayer.getError().getMessage());
                    });
                    
                } catch (Exception e) {
                    e.printStackTrace();
                    showError("Не удалось создать медиаплеер: " + e.getMessage());
                }
            });
            
        } catch (Exception e) {
            e.printStackTrace();
            showError("Ошибка при загрузке аудио: " + e.getMessage());
        }
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
        }
    }
    
    private void updateProgressSlider() {
        if (mediaPlayer != null && mediaPlayer.getTotalDuration().greaterThan(Duration.ZERO)) {
            double currentTime = mediaPlayer.getCurrentTime().toSeconds();
            double totalTime = mediaPlayer.getTotalDuration().toSeconds();
            double progress = (currentTime / totalTime) * 100.0;
            progressSlider.setValue(progress);
        }
    }
    
    private void updateTimeLabel() {
        if (mediaPlayer != null && mediaPlayer.getTotalDuration().greaterThan(Duration.ZERO)) {
            Duration currentTime = mediaPlayer.getCurrentTime();
            Duration totalTime = mediaPlayer.getTotalDuration();
            
            String current = formatTime(currentTime);
            String total = formatTime(totalTime);
            
            timeLabel.setText(current + " / " + total);
        } else {
            timeLabel.setText("00:00 / " + track.getDuration());
        }
    }
    
    private String formatTime(Duration duration) {
        int minutes = (int) duration.toMinutes();
        int seconds = (int) duration.toSeconds() % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    private void showError(String message) {
        javafx.application.Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
            stage.close();
        });
    }
    
    private void cleanup() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }
        
        if (tempAudioFile != null && tempAudioFile.exists()) {
            tempAudioFile.delete();
        }
    }
    
    public void show() {
        // Уже показывается в конструкторе
    }
}