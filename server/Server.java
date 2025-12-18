import java.io.*;
import java.net.*;

public class Server {
    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("Сервер завершает работу...");
            Logger.close();
        }));
        
        Logger.info("Запуск сервера...");
        
        new File(ServerConfig.MUSIC_DIR).mkdirs();
        new File(ServerConfig.COVERS_DIR).mkdirs();
        
        DatabaseManager dbManager = new DatabaseManager(ServerConfig.DB_FILE);
        dbManager.loadDatabase();
        
        try (ServerSocket serverSocket = new ServerSocket(ServerConfig.PORT)) {
            Logger.info("Сервер запущен на порту " + ServerConfig.PORT);
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                Logger.info("Новое подключение: " + clientSocket.getInetAddress());
                new ClientHandler(clientSocket, dbManager).start();
            }
        } catch (IOException e) {
            Logger.error("Ошибка в основном цикле сервера", e);
        } finally {
            Logger.close();
        }
    }
}