import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private static final int PORT = 12345;
    private static final String DB_FILE = "music_db.txt";
    private static List<String> musicData = new ArrayList<>();

    public static void main(String[] args) {
        loadDatabase();
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Сервер запущен на порту " + PORT);
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Новое подключение: " + clientSocket.getInetAddress());
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static void loadDatabase() {
        try {
            File file = new File(DB_FILE);
            if (!file.exists()) {
                createSampleData();
            }
            
            musicData.clear();
            try (BufferedReader reader = new BufferedReader(new FileReader(DB_FILE))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        musicData.add(line);
                    }
                }
            }
            System.out.println("База данных загружена. Записей: " + musicData.size());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static void createSampleData() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(DB_FILE))) {
            writer.println("1:Bohemian Rhapsody:6:07:Queen");
            writer.println("2:Hotel California:6:30:Eagles");
            writer.println("3:Stairway to Heaven:8:02:Led Zeppelin");
            writer.println("4:Smooth Criminal:4:17:Michael Jackson");
            writer.println("5:Smells Like Teen Spirit:5:01:Nirvana");
            writer.println("6:Billie Jean:4:54:Michael Jackson");
            writer.println("7:Like a Rolling Stone:6:13:Bob Dylan");
            writer.println("8:Imagine:3:03:John Lennon");
        }
    }
    
    private static class ClientHandler extends Thread {
        private Socket socket;
        
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }
        
        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                
                String request;
                while ((request = in.readLine()) != null) {
                    System.out.println("Получен запрос: " + request);
                    
                    if (request.equals("GET_ALL")) {
                        for (String record : musicData) {
                            out.println(record);
                        }
                        out.println("END");
                    } else if (request.startsWith("GET_BY_ID:")) {
                        String id = request.substring(10);
                        for (String record : musicData) {
                            if (record.startsWith(id + ":")) {
                                out.println(record);
                                break;
                            }
                        }
                        out.println("END");
                    } else if (request.equals("RELOAD")) {
                        loadDatabase();
                        out.println("OK");
                    } else {
                        out.println("ERROR: Unknown command");
                    }
                }
            } catch (IOException e) {
                System.out.println("Ошибка при обработке клиента: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}