import java.io.*;
import java.net.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Font;
import org.json.JSONArray;
import org.json.JSONObject;

// Класс для представления записи о музыке
class MusicRecord {
    private String id;
    private String title;
    private String duration;
    private String artist;
    private String audioFilename;
    private String coverFilename;
    
    public MusicRecord() {}
    
    public MusicRecord(String id, String title, String duration, String artist, 
                      String audioFilename, String coverFilename) {
        this.id = id;
        this.title = title;
        this.duration = duration;
        this.artist = artist;
        this.audioFilename = audioFilename;
        this.coverFilename = coverFilename;
    }
    
    // Конструктор из JSONObject
    public MusicRecord(JSONObject json) {
        this.id = json.getString("id");
        this.title = json.getString("title");
        this.duration = json.getString("duration");
        this.artist = json.getString("artist");
        this.audioFilename = json.getString("audioFilename");
        this.coverFilename = json.optString("coverFilename", "-");
    }
    
    // Преобразование в JSONObject
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("title", title);
        json.put("duration", duration);
        json.put("artist", artist);
        json.put("audioFilename", audioFilename);
        json.put("coverFilename", coverFilename);
        return json;
    }
    
    // Геттеры и сеттеры
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }
    
    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }
    
    public String getAudioFilename() { return audioFilename; }
    public void setAudioFilename(String audioFilename) { this.audioFilename = audioFilename; }
    
    public String getCoverFilename() { return coverFilename; }
    public void setCoverFilename(String coverFilename) { this.coverFilename = coverFilename; }
    
    // Метод для преобразования в старый формат (для совместимости с клиентом)
    public String toOldFormat() {
        return id + ":" + title + ":" + duration + ":" + artist + ":" + audioFilename + ":" + coverFilename;
    }
}

// Класс для работы с базой данных на JSON
class MusicDatabase {
    private static final String DB_FILE = "music_db.json";
    private List<MusicRecord> records;
    
    public MusicDatabase() throws IOException {
        this.records = new ArrayList<>();
        loadFromFile();
    }
    
    private void loadFromFile() throws IOException {
        File file = new File(DB_FILE);
        if (!file.exists()) {
            throw new FileNotFoundException("Файл базы данных JSON не найден: " + DB_FILE);
        }
        
        records.clear();
        try (BufferedReader reader = new BufferedReader(new FileReader(DB_FILE))) {
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            
            JSONArray jsonArray = new JSONArray(jsonContent.toString());
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonRecord = jsonArray.getJSONObject(i);
                MusicRecord record = new MusicRecord(jsonRecord);
                records.add(record);
            }
        } catch (org.json.JSONException e) {
            throw new IOException("Ошибка парсинга JSON файла: " + e.getMessage());
        }
        System.out.println("База данных JSON загружена. Записей: " + records.size());
    }
    
    public List<String> getAllRecordsAsStrings() {
        List<String> result = new ArrayList<>();
        for (MusicRecord record : records) {
            result.add(record.toOldFormat());
        }
        return result;
    }
    
    public String[] getFileInfo(String id) {
        for (MusicRecord record : records) {
            if (record.getId().equals(id)) {
                return new String[]{record.getAudioFilename(), record.getCoverFilename()};
            }
        }
        return null;
    }
    
    // Получение аудиофайла по ID
    public String getAudioFilenameById(String id) {
        for (MusicRecord record : records) {
            if (record.getId().equals(id)) {
                return record.getAudioFilename();
            }
        }
        return null;
    }
    
    public void reload() throws IOException {
        loadFromFile();
    }
}

// Основной класс сервера
public class Server {
    private static final int PORT = 12345;
    private static final String MUSIC_DIR = "music";
    private static final String COVERS_DIR = "covers";
    private static MusicDatabase musicDatabase;
    
    public static void main(String[] args) {
        // Создаем необходимые директории
        new File(MUSIC_DIR).mkdir();
        new File(COVERS_DIR).mkdir();
        
        try {
            musicDatabase = new MusicDatabase();
        } catch (FileNotFoundException e) {
            System.err.println("ФАТАЛЬНАЯ ОШИБКА: Файл базы данных JSON не найден!");
            System.err.println("Создайте файл " + new File("music_db.json").getAbsolutePath() + " с данными о музыке.");
            System.err.println("Формат JSON массива объектов с полями: id, title, duration, artist, audioFilename, coverFilename");
            System.err.println("Пример содержимого:");
            System.err.println("[\n  {\n    \"id\": \"1\",\n    \"title\": \"Bohemian Rhapsody\",\n    \"duration\": \"6:07\",\n    \"artist\": \"Queen\",\n    \"audioFilename\": \"queen_bohemian.mp3\",\n    \"coverFilename\": \"-\"\n  }\n]");
            System.exit(1);
            return;
        } catch (IOException e) {
            System.err.println("ФАТАЛЬНАЯ ОШИБКА: Не удалось загрузить базу данных JSON!");
            e.printStackTrace();
            System.exit(1);
            return;
        }
        
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
                        for (String record : musicDatabase.getAllRecordsAsStrings()) {
                            out.println(record);
                        }
                        out.println("END");
                    } else if (request.startsWith("GET_FILE_BY_ID:")) {
                        // Получаем файл по ID: находим имя файла в базе и отправляем его
                        String id = request.substring(15);
                        String audioFilename = musicDatabase.getAudioFilenameById(id);
                        if (audioFilename != null) {
                            // Отправляем файл с именем, которое нашли в базе
                            sendFile(MUSIC_DIR, audioFilename, socket.getOutputStream());
                        } else {
                            out.println("ERROR:File not found for ID " + id);
                        }
                        break;
                    } else if (request.startsWith("GET_COVER:")) {
                        String coverFilename = request.substring(10);
                        sendCover(coverFilename, socket.getOutputStream());
                        break;
                    } else if (request.startsWith("GET_FILE_INFO:")) {
                        String id = request.substring(14);
                        String[] fileInfo = musicDatabase.getFileInfo(id);
                        if (fileInfo != null) {
                            // Возвращаем в формате: <filename>:<cover>
                            out.println(fileInfo[0] + ":" + fileInfo[1]);
                        } else {
                            out.println("ERROR:File not found");
                        }
                    } else if (request.equals("RELOAD")) {
                        try {
                            musicDatabase.reload();
                            out.println("OK");
                        } catch (IOException e) {
                            out.println("ERROR:Failed to reload database: " + e.getMessage());
                        }
                    } else if (request.startsWith("GET_FILE:")) {
                        // Старая команда для совместимости (по имени файла)
                        String filename = request.substring(9);
                        sendFile(MUSIC_DIR, filename, socket.getOutputStream());
                        break;
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
        
        private void sendFile(String directory, String filename, OutputStream socketOut) {
            try {
                File file = new File(directory + File.separator + filename);
                if (!file.exists()) {
                    System.out.println("Файл не найден: " + file.getAbsolutePath());
                    PrintWriter out = new PrintWriter(socketOut, true);
                    out.println("ERROR:File not found: " + filename);
                    return;
                }
                
                System.out.println("Отправка файла: " + file.getName() + " размер: " + file.length());
                
                PrintWriter out = new PrintWriter(socketOut, true);
                out.println("FILE_SIZE:" + file.length());
                
                try (FileInputStream fis = new FileInputStream(file);
                     BufferedInputStream bis = new BufferedInputStream(fis)) {
                    
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
                
            } catch (IOException e) {
                System.out.println("Ошибка при отправке файла: " + e.getMessage());
            }
        }
        
        private void sendCover(String coverFilename, OutputStream socketOut) {
            try {
                File coverFile = new File(COVERS_DIR + File.separator + coverFilename);
                
                if (coverFile.exists()) {
                    sendFile(COVERS_DIR, coverFilename, socketOut);
                } else {
                    createAndSendDefaultCover(socketOut);
                }
                
            } catch (Exception e) {
                System.out.println("Ошибка при отправке обложки: " + e.getMessage());
            }
        }
        
        private void createAndSendDefaultCover(OutputStream socketOut) throws IOException {
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
            
            PrintWriter out = new PrintWriter(socketOut, true);
            out.println("FILE_SIZE:" + imageData.length);
            socketOut.write(imageData, 0, imageData.length);
            socketOut.flush();
        }
    }
}
