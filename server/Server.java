import java.io.*;
import java.net.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Font;
import org.json.*;

public class Server {
    private static final int PORT = 12345;
    private static final String MUSIC_DIR = "music";
    private static final String COVERS_DIR = "covers";
    private static final String DB_FILE = "music_db.json";
    private static List<MusicRecord> musicData = new ArrayList<>();
    
    public static void main(String[] args) {
        new File(MUSIC_DIR).mkdir();
        new File(COVERS_DIR).mkdir();
        
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
                createSampleDatabase();
            }
            
            musicData.clear();
            try (BufferedReader reader = new BufferedReader(new FileReader(DB_FILE))) {
                StringBuilder jsonContent = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonContent.append(line);
                }
                
                JSONArray jsonArray = new JSONArray(jsonContent.toString());
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonRecord = jsonArray.getJSONObject(i);
                    MusicRecord record = new MusicRecord(
                        jsonRecord.getString("id"),
                        jsonRecord.getString("title"),
                        jsonRecord.getString("duration"),
                        jsonRecord.getString("artist"),
                        jsonRecord.getString("audioFilename"),
                        jsonRecord.optString("coverFilename", "-")
                    );
                    musicData.add(record);
                }
            }
            System.out.println("База данных загружена. Записей: " + musicData.size());
        } catch (IOException e) {
            System.err.println("Ошибка чтения базы данных: " + e.getMessage());
            System.exit(1);
        } catch (JSONException e) {
            System.err.println("Ошибка парсинга JSON: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void createSampleDatabase() throws IOException {
        JSONArray jsonArray = new JSONArray();
        
        JSONObject[] sampleRecords = {
            new JSONObject()
                .put("id", "1")
                .put("title", "Chippin' in")
                .put("duration", "3:34")
                .put("artist", "Samurai")
                .put("audioFilename", "Chippin' in.mp3")
                .put("coverFilename", "1.png"),
            new JSONObject()
                .put("id", "2")
                .put("title", "Never Fade Away")
                .put("duration", "3:10")
                .put("artist", "Samurai")
                .put("audioFilename", "Never Fade Away.mp3")
                .put("coverFilename", "2.png"),
            new JSONObject()
                .put("id", "3")
                .put("title", "Black Dog")
                .put("duration", "4:23")
                .put("artist", "Samurai")
                .put("audioFilename", "Black Dog.mp3")
                .put("coverFilename", "4.png"),
            new JSONObject()
                .put("id", "4")
                .put("title", "The Ballad of Buck Ravers")
                .put("duration", "4:28")
                .put("artist", "Samurai")
                .put("audioFilename", "The Ballad of Buck Ravers.mp3")
                .put("coverFilename", "5.png"),
            new JSONObject()
                .put("id", "5")
                .put("title", "A Like Supreme")
                .put("duration", "3:49")
                .put("artist", "Samurai")
                .put("audioFilename", "A Like Supreme.mp3")
                .put("coverFilename", "3.png"),
            new JSONObject()
                .put("id", "6")
                .put("title", "Afraid To Shoot Strangers")
                .put("duration", "6:56")
                .put("artist", "Iron Maiden")
                .put("audioFilename", "afd.mp3")
                .put("coverFilename", "-")
        };
        
        for (JSONObject record : sampleRecords) {
            jsonArray.put(record);
        }
        
        try (FileWriter file = new FileWriter(DB_FILE)) {
            file.write(jsonArray.toString(2));
            System.out.println("Создана новая база данных с тестовыми записями");
        }
    }
    
    private static class ClientHandler extends Thread {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }
        
        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                
                String request;
                while ((request = in.readLine()) != null) {
                    System.out.println("Получен JSON запрос: " + request);
                    
                    try {
                        JSONObject jsonRequest = new JSONObject(request);
                        String command = jsonRequest.getString("command");
                        
                        JSONObject response = new JSONObject();
                        
                        switch (command) {
                            case "GET_ALL":
                                handleGetAll(response);
                                break;
                                
                            case "GET_FILE_BY_ID":
                                handleGetFileById(jsonRequest.getString("id"));
                                return; // После отправки файла закрываем соединение
                                
                            case "GET_COVER":
                                handleGetCover(jsonRequest.getString("coverFilename"));
                                return; // После отправки файла закрываем соединение
                                
                            case "GET_FILE_INFO":
                                handleGetFileInfo(jsonRequest.getString("id"), response);
                                break;
                                
                            case "RELOAD":
                                loadDatabase();
                                response.put("status", "OK");
                                response.put("message", "База данных перезагружена");
                                break;
                                
                            default:
                                response.put("status", "ERROR");
                                response.put("message", "Неизвестная команда: " + command);
                        }
                        
                        out.println(response.toString());
                        
                    } catch (JSONException e) {
                        JSONObject errorResponse = new JSONObject();
                        errorResponse.put("status", "ERROR");
                        errorResponse.put("message", "Некорректный JSON запрос: " + e.getMessage());
                        out.println(errorResponse.toString());
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
        
        private void handleGetAll(JSONObject response) {
            JSONArray tracksArray = new JSONArray();
            
            for (MusicRecord record : musicData) {
                JSONObject trackJson = new JSONObject()
                    .put("id", record.getId())
                    .put("title", record.getTitle())
                    .put("duration", record.getDuration())
                    .put("artist", record.getArtist())
                    .put("audioFilename", record.getAudioFilename())
                    .put("coverFilename", record.getCoverFilename());
                tracksArray.put(trackJson);
            }
            
            response.put("status", "OK");
            response.put("data", tracksArray);
        }
        
        private void handleGetFileInfo(String id, JSONObject response) {
            for (MusicRecord record : musicData) {
                if (record.getId().equals(id)) {
                    response.put("status", "OK");
                    response.put("audioFilename", record.getAudioFilename());
                    response.put("coverFilename", record.getCoverFilename());
                    return;
                }
            }
            
            response.put("status", "ERROR");
            response.put("message", "Запись с ID " + id + " не найдена");
        }
        
        private void handleGetFileById(String id) {
            try {
                String audioFilename = null;
                for (MusicRecord record : musicData) {
                    if (record.getId().equals(id)) {
                        audioFilename = record.getAudioFilename();
                        break;
                    }
                }
                
                if (audioFilename == null) {
                    JSONObject errorResponse = new JSONObject();
                    errorResponse.put("status", "ERROR");
                    errorResponse.put("message", "Файл с ID " + id + " не найден");
                    out.println(errorResponse.toString());
                    return;
                }
                
                sendFile(MUSIC_DIR, audioFilename);
                
            } catch (Exception e) {
                System.out.println("Ошибка при отправке файла: " + e.getMessage());
            }
        }
        
        private void handleGetCover(String coverFilename) {
            try {
                if (coverFilename == null || coverFilename.equals("-")) {
                    createAndSendDefaultCover();
                } else {
                    sendFile(COVERS_DIR, coverFilename);
                }
            } catch (Exception e) {
                System.out.println("Ошибка при отправке обложки: " + e.getMessage());
            }
        }
        
        private void sendFile(String directory, String filename) throws IOException {
            File file = new File(directory + File.separator + filename);
            if (!file.exists()) {
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("status", "ERROR");
                errorResponse.put("message", "Файл не найден: " + filename);
                out.println(errorResponse.toString());
                return;
            }
            
            System.out.println("Отправка файла: " + file.getName() + " размер: " + file.length());
            
            // Отправляем JSON с информацией о файле
            JSONObject fileInfo = new JSONObject();
            fileInfo.put("status", "FILE");
            fileInfo.put("filename", filename);
            fileInfo.put("size", file.length());
            out.println(fileInfo.toString());
            
            // Отправляем сам файл
            try (FileInputStream fis = new FileInputStream(file);
                 BufferedInputStream bis = new BufferedInputStream(fis);
                 OutputStream socketOut = socket.getOutputStream()) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalSent = 0;
                
                while ((bytesRead = bis.read(buffer)) != -1) {
                    socketOut.write(buffer, 0, bytesRead);
                    totalSent += bytesRead;
                }
                socketOut.flush();
                System.out.println("Файл отправлен успешно. Отправлено: " + totalSent + " байт");
            }
        }
        
        private void createAndSendDefaultCover() throws IOException {
            int width = 300;
            int height = 300;
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            
            Graphics2D g2d = image.createGraphics();
            
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int r = 40 + (x * 60) / width;
                    int g = 40 + (y * 60) / height;
                    int b = 100;
                    int rgb = (r << 16) | (g << 8) | b;
                    image.setRGB(x, y, rgb);
                }
            }
            
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 24));
            String text = "Музыка";
            int textWidth = g2d.getFontMetrics().stringWidth(text);
            int x = (width - textWidth) / 2;
            int y = height / 2;
            g2d.drawString(text, x, y);
            
            g2d.dispose();
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] imageData = baos.toByteArray();
            
            // Отправляем JSON с информацией о файле
            JSONObject fileInfo = new JSONObject();
            fileInfo.put("status", "FILE");
            fileInfo.put("filename", "default_cover.png");
            fileInfo.put("size", imageData.length);
            out.println(fileInfo.toString());
            
            // Отправляем изображение
            OutputStream socketOut = socket.getOutputStream();
            socketOut.write(imageData, 0, imageData.length);
            socketOut.flush();
        }
    }
    
    private static class MusicRecord {
        private String id;
        private String title;
        private String duration;
        private String artist;
        private String audioFilename;
        private String coverFilename;
        
        public MusicRecord(String id, String title, String duration, String artist,
                          String audioFilename, String coverFilename) {
            this.id = id;
            this.title = title;
            this.duration = duration;
            this.artist = artist;
            this.audioFilename = audioFilename;
            this.coverFilename = coverFilename;
        }
        
        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getDuration() { return duration; }
        public String getArtist() { return artist; }
        public String getAudioFilename() { return audioFilename; }
        public String getCoverFilename() { return coverFilename; }
    }
}
