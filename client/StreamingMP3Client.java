import javazoom.jl.player.Player;
import java.io.*;
import java.net.*;

public class StreamingMP3Client {
    private static String serverAddress;
    private static final int PORT = 12345;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Использование: java StreamingMP3Client <server_address>");
            System.out.println("Пример: java StreamingMP3Client localhost");
            return;
        }

        serverAddress = args[0];
        
        System.out.println("Подключение к серверу " + serverAddress + "...");
        
        try {
            Socket socket = new Socket(serverAddress, PORT);
            System.out.println("Подключение установлено!");
            
            // Получаем поток данных
            InputStream is = socket.getInputStream();
            BufferedInputStream bis = new BufferedInputStream(is);
            
            // Начинаем воспроизведение
            System.out.println("Начало воспроизведения...");
            Player player = new Player(bis);
            
            // Воспроизводим в отдельном потоке
            Thread playThread = new Thread(() -> {
                try {
                    player.play();
                } catch (Exception e) {
                    System.err.println("Ошибка воспроизведения: " + e.getMessage());
                }
            });
            
            playThread.start();
            
            // Ожидаем завершения воспроизведения
            playThread.join();
            
            System.out.println("Воспроизведение завершено!");
            socket.close();
            
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }
}