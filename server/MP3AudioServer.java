import java.io.*;
import java.net.*;

public class MP3AudioServer {
    private static final int PORT = 12345;
    private static final String MP3_FILE_PATH = "audio.mp3"; // Ваш MP3 файл

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("-file")) {
            String filePath = args[1];
            System.out.println("Использование файла: " + filePath);
            startServer(filePath);
        } else {
            System.out.println("Использование: java MP3AudioServer -file <путь_к_файлу>");
            System.out.println("Использование файла по умолчанию: " + MP3_FILE_PATH);
            startServer(MP3_FILE_PATH);
        }
    }
    
    private static void startServer(String filePath) {
        System.out.println("Сервер аудио (MP3) запущен...");
        System.out.println("Ожидание подключений на порту " + PORT);
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Клиент подключен: " + clientSocket.getInetAddress());
                
                // Запускаем обработку клиента в отдельном потоке
                new Thread(() -> handleClient(clientSocket, filePath)).start();
            }
        } catch (IOException e) {
            System.err.println("Ошибка сервера: " + e.getMessage());
        }
    }

    private static void handleClient(Socket clientSocket, String filePath) {
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(filePath));
             BufferedOutputStream bos = new BufferedOutputStream(clientSocket.getOutputStream())) {
            
            // Сначала отправляем размер файла
            File file = new File(filePath);
            long fileSize = file.length();
            DataOutputStream dos = new DataOutputStream(bos);
            dos.writeLong(fileSize);
            dos.flush();
            
            // Затем отправляем сам файл
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;
            
            while ((bytesRead = bis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            bos.flush();
            
            System.out.println("MP3 файл отправлен клиенту. Отправлено байт: " + totalBytes);
            
        } catch (IOException e) {
            System.err.println("Ошибка обработки файла: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Ошибка закрытия сокета: " + e.getMessage());
            }
        }
    }
}