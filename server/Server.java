import java.io.*;
import java.net.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Font;
import org.json.*;
import java.util.Base64;

public class Server {
    private static final int PORT = 12345;
    private static final String MUSIC_DIR = "music";
    private static final String COVERS_DIR = "covers";
    private static final String DB_FILE = "music_db.json";
    private static List<MusicRecord> musicData = new ArrayList<>();
    
    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("Сервер завершает работу...");
            Logger.close();
        }));
        
        Logger.info("Запуск сервера...");
        
        new File(MUSIC_DIR).mkdirs();
        new File(COVERS_DIR).mkdirs();
        
        loadDatabase();
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            Logger.info("Сервер запущен на порту " + PORT);
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                Logger.info("Новое подключение: " + clientSocket.getInetAddress());
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            Logger.error("Ошибка в основном цикле сервера", e);
        } finally {
            Logger.close();
        }
    }
    
    private static void loadDatabase() {
        try {
            File file = new File(DB_FILE);
            if (!file.exists()) {
                Logger.warning("База данных отсутствует, создаем пустую...");
                saveDatabase();
                return;
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
            Logger.info("База данных загружена. Записей: " + musicData.size());
        } catch (IOException e) {
            Logger.error("Ошибка чтения базы данных: " + e.getMessage(), e);
        } catch (JSONException e) {
            Logger.error("Ошибка парсинга JSON: " + e.getMessage(), e);
        }
    }
    
    private static synchronized void saveDatabase() {
        try {
            JSONArray jsonArray = new JSONArray();
            for (MusicRecord record : musicData) {
                JSONObject jsonRecord = new JSONObject();
                jsonRecord.put("id", record.getId());
                jsonRecord.put("title", record.getTitle());
                jsonRecord.put("duration", record.getDuration());
                jsonRecord.put("artist", record.getArtist());
                jsonRecord.put("audioFilename", record.getAudioFilename());
                jsonRecord.put("coverFilename", record.getCoverFilename());
                jsonArray.put(jsonRecord);
            }
            
            try (FileWriter writer = new FileWriter(DB_FILE)) {
                writer.write(jsonArray.toString(2));
            }
            Logger.info("База данных сохранена. Записей: " + musicData.size());
        } catch (IOException e) {
            Logger.error("Ошибка сохранения базы данных: " + e.getMessage(), e);
        }
    }
    
    private static MusicRecord findRecordById(String id) {
        for (MusicRecord record : musicData) {
            if (record.getId().equals(id)) {
                return record;
            }
        }
        return null;
    }
    
    private static boolean deleteFile(String directory, String filename) {
        if (filename == null || filename.equals("-")) {
            return true;
        }
        
        File file = new File(directory + File.separator + filename);
        if (file.exists()) {
            boolean deleted = file.delete();
            if (deleted) {
                Logger.info("Файл удален: " + file.getAbsolutePath());
            } else {
                Logger.warning("Не удалось удалить файл: " + file.getAbsolutePath());
            }
            return deleted;
        }
        return true;
    }
    
    private static String generateUniqueId() {
        int maxId = 0;
        for (MusicRecord record : musicData) {
            try {
                int id = Integer.parseInt(record.getId());
                if (id > maxId) {
                    maxId = id;
                }
            } catch (NumberFormatException e) {
                // Пропускаем нечисловые ID
            }
        }
        return String.valueOf(maxId + 1);
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
                    Logger.debug("Получен JSON запрос: " + request);
                    
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
                                return;
                                
                            case "GET_COVER":
                                handleGetCover(jsonRequest.getString("coverFilename"));
                                return;
                                
                            case "GET_FILE_INFO":
                                handleGetFileInfo(jsonRequest.getString("id"), response);
                                break;
                                
                            case "RELOAD":
                                loadDatabase();
                                response.put("status", "OK");
                                response.put("message", "База данных перезагружена");
                                break;
                                
                            case "ADD_TRACK":
                                handleAddTrack(jsonRequest, response);
                                break;
                                
                            case "UPDATE_TRACK":
                                handleUpdateTrack(jsonRequest, response);
                                break;
                                
                            case "DELETE_TRACK":
                                handleDeleteTrack(jsonRequest, response);
                                break;
                                
                            case "GET_NEXT_ID":
                                response.put("status", "OK");
                                response.put("nextId", generateUniqueId());
                                break;
                                
                            default:
                                response.put("status", "ERROR");
                                response.put("message", "Неизвестная команда: " + command);
                        }
                        
                        out.println(response.toString());
                        Logger.debug("Отправлен JSON ответ: " + response.toString());
                        
                    } catch (JSONException e) {
                        JSONObject errorResponse = new JSONObject();
                        errorResponse.put("status", "ERROR");
                        errorResponse.put("message", "Некорректный JSON запрос: " + e.getMessage());
                        out.println(errorResponse.toString());
                        Logger.warning("Некорректный JSON запрос: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                Logger.error("Ошибка при обработке клиента: " + e.getMessage(), e);
            } finally {
                try {
                    socket.close();
                    Logger.debug("Соединение с клиентом закрыто");
                } catch (IOException e) {
                    Logger.error("Ошибка при закрытии сокета", e);
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
            Logger.debug("Обработан запрос GET_ALL, возвращено " + tracksArray.length() + " записей");
        }
        
        private void handleGetFileInfo(String id, JSONObject response) {
            MusicRecord record = findRecordById(id);
            if (record != null) {
                response.put("status", "OK");
                response.put("audioFilename", record.getAudioFilename());
                response.put("coverFilename", record.getCoverFilename());
                Logger.debug("Найдена информация о файле с ID: " + id);
                return;
            }
            
            response.put("status", "ERROR");
            response.put("message", "Запись с ID " + id + " не найдена");
            Logger.warning("Запись с ID " + id + " не найдена");
        }
        
        private void handleGetFileById(String id) {
            try {
                MusicRecord record = findRecordById(id);
                
                if (record == null) {
                    JSONObject errorResponse = new JSONObject();
                    errorResponse.put("status", "ERROR");
                    errorResponse.put("message", "Файл с ID " + id + " не найден");
                    out.println(errorResponse.toString());
                    Logger.warning("Файл с ID " + id + " не найден");
                    return;
                }
                
                sendFile(MUSIC_DIR, record.getAudioFilename());
                
            } catch (Exception e) {
                Logger.error("Ошибка при отправке файла", e);
            }
        }
        
        private void handleGetCover(String coverFilename) {
            try {
                if (coverFilename == null || coverFilename.equals("-")) {
                    createAndSendDefaultCover("Default Cover", "No Artist");
                } else {
                    sendFile(COVERS_DIR, coverFilename);
                }
            } catch (Exception e) {
                Logger.error("Ошибка при отправке обложки", e);
            }
        }
        
        private void handleAddTrack(JSONObject request, JSONObject response) {
            try {
                String id = request.optString("id", "");
                if (id.isEmpty()) {
                    id = generateUniqueId();
                }
                
                // Проверяем, существует ли уже такой ID
                if (findRecordById(id) != null) {
                    response.put("status", "ERROR");
                    response.put("message", "Трек с ID " + id + " уже существует");
                    Logger.warning("Попытка добавить трек с существующим ID: " + id);
                    return;
                }
                
                String title = request.getString("title");
                String duration = request.getString("duration");
                String artist = request.getString("artist");
                String audioFilename = request.getString("audioFilename");
                String coverFilename = request.optString("coverFilename", "-");
                
                // Сохраняем аудиофайл
                if (request.has("audioData")) {
                    String audioDataBase64 = request.getString("audioData");
                    byte[] audioData = Base64.getDecoder().decode(audioDataBase64);
                    File audioFile = new File(MUSIC_DIR + File.separator + audioFilename);
                    
                    try (FileOutputStream fos = new FileOutputStream(audioFile)) {
                        fos.write(audioData);
                    }
                    Logger.info("Аудиофайл сохранен: " + audioFile.getAbsolutePath());
                }
                
                // Сохраняем обложку
                if (request.has("coverData") && !request.getString("coverData").isEmpty()) {
                    String coverDataBase64 = request.getString("coverData");
                    byte[] coverData = Base64.getDecoder().decode(coverDataBase64);
                    File coverFile = new File(COVERS_DIR + File.separator + coverFilename);
                    
                    try (FileOutputStream fos = new FileOutputStream(coverFile)) {
                        fos.write(coverData);
                    }
                    Logger.info("Обложка сохранена: " + coverFile.getAbsolutePath());
                }
                
                // Создаем запись
                MusicRecord record = new MusicRecord(id, title, duration, artist, audioFilename, coverFilename);
                musicData.add(record);
                
                // Сохраняем базу
                saveDatabase();
                
                response.put("status", "OK");
                response.put("message", "Трек успешно добавлен");
                response.put("id", id);
                Logger.info("Добавлен новый трек: " + title + " (ID: " + id + ")");
                
            } catch (Exception e) {
                response.put("status", "ERROR");
                response.put("message", "Ошибка при добавлении трека: " + e.getMessage());
                Logger.error("Ошибка при добавлении трека", e);
            }
        }
        
        private void handleUpdateTrack(JSONObject request, JSONObject response) {
            try {
                String id = request.getString("id");
                MusicRecord record = findRecordById(id);
                
                if (record == null) {
                    response.put("status", "ERROR");
                    response.put("message", "Трек с ID " + id + " не найден");
                    Logger.warning("Попытка обновить несуществующий трек с ID: " + id);
                    return;
                }
                
                // Сохраняем старые имена файлов
                String oldAudioFilename = record.getAudioFilename();
                String oldCoverFilename = record.getCoverFilename();
                String newAudioFilename = request.getString("audioFilename");
                String newCoverFilename = request.optString("coverFilename", "-");
                
                // Обновляем аудиофайл
                if (request.has("audioData") && !request.getString("audioData").isEmpty()) {
                    String audioDataBase64 = request.getString("audioData");
                    byte[] audioData = Base64.getDecoder().decode(audioDataBase64);
                    
                    // Удаляем старый файл, если имя изменилось
                    if (!oldAudioFilename.equals(newAudioFilename)) {
                        deleteFile(MUSIC_DIR, oldAudioFilename);
                    }
                    
                    File audioFile = new File(MUSIC_DIR + File.separator + newAudioFilename);
                    try (FileOutputStream fos = new FileOutputStream(audioFile)) {
                        fos.write(audioData);
                    }
                    Logger.info("Аудиофайл обновлен: " + audioFile.getAbsolutePath());
                }
                
                // Обновляем обложку
                if (request.has("coverData")) {
                    String coverDataBase64 = request.getString("coverData");
                    if (!coverDataBase64.isEmpty()) {
                        byte[] coverData = Base64.getDecoder().decode(coverDataBase64);
                        
                        // Удаляем старый файл, если имя изменилось
                        if (!oldCoverFilename.equals(newCoverFilename)) {
                            deleteFile(COVERS_DIR, oldCoverFilename);
                        }
                        
                        File coverFile = new File(COVERS_DIR + File.separator + newCoverFilename);
                        try (FileOutputStream fos = new FileOutputStream(coverFile)) {
                            fos.write(coverData);
                        }
                        Logger.info("Обложка обновлена: " + coverFile.getAbsolutePath());
                    }
                }
                
                // Обновляем запись
                record.setTitle(request.getString("title"));
                record.setDuration(request.getString("duration"));
                record.setArtist(request.getString("artist"));
                record.setAudioFilename(newAudioFilename);
                record.setCoverFilename(newCoverFilename);
                
                // Сохраняем базу
                saveDatabase();
                
                response.put("status", "OK");
                response.put("message", "Трек успешно обновлен");
                Logger.info("Трек обновлен: " + record.getTitle() + " (ID: " + id + ")");
                
            } catch (Exception e) {
                response.put("status", "ERROR");
                response.put("message", "Ошибка при обновлении трека: " + e.getMessage());
                Logger.error("Ошибка при обновлении трека", e);
            }
        }
        
        private void handleDeleteTrack(JSONObject request, JSONObject response) {
            try {
                String id = request.getString("id");
                MusicRecord record = findRecordById(id);
                
                if (record == null) {
                    response.put("status", "ERROR");
                    response.put("message", "Трек с ID " + id + " не найден");
                    Logger.warning("Попытка удалить несуществующий трек с ID: " + id);
                    return;
                }
                
                // Удаляем файлы
                boolean audioDeleted = deleteFile(MUSIC_DIR, record.getAudioFilename());
                boolean coverDeleted = deleteFile(COVERS_DIR, record.getCoverFilename());
                
                // Удаляем запись
                musicData.remove(record);
                
                // Сохраняем базу
                saveDatabase();
                
                response.put("status", "OK");
                response.put("message", "Трек успешно удален");
                response.put("audioDeleted", audioDeleted);
                response.put("coverDeleted", coverDeleted);
                Logger.info("Трек удален: " + record.getTitle() + " (ID: " + id + ")");
                
            } catch (Exception e) {
                response.put("status", "ERROR");
                response.put("message", "Ошибка при удалении трека: " + e.getMessage());
                Logger.error("Ошибка при удалении трека", e);
            }
        }
        
        private void sendFile(String directory, String filename) throws IOException {
            File file = new File(directory + File.separator + filename);
            if (!file.exists()) {
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("status", "ERROR");
                errorResponse.put("message", "Файл не найден: " + filename);
                out.println(errorResponse.toString());
                Logger.warning("Файл не найден: " + filename);
                return;
            }
            
            Logger.info("Отправка файла: " + file.getName() + " размер: " + file.length() + " байт");
            
            JSONObject fileInfo = new JSONObject();
            fileInfo.put("status", "FILE");
            fileInfo.put("filename", filename);
            fileInfo.put("size", file.length());
            out.println(fileInfo.toString());
            
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
                Logger.info("Файл отправлен успешно. Отправлено: " + totalSent + " байт");
            }
        }
        
        private void createAndSendDefaultCover(String title, String artist) throws IOException {
            Logger.debug("Создание обложки по умолчанию");
            
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
            String text = title.length() > 15 ? title.substring(0, 12) + "..." : title;
            int textWidth = g2d.getFontMetrics().stringWidth(text);
            int x = (width - textWidth) / 2;
            int y = height / 2;
            g2d.drawString(text, x, y);
            
            g2d.setFont(new Font("Arial", Font.PLAIN, 16));
            g2d.drawString(artist, x, y + 30);
            
            g2d.dispose();
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] imageData = baos.toByteArray();
            
            JSONObject fileInfo = new JSONObject();
            fileInfo.put("status", "FILE");
            fileInfo.put("filename", "default_cover.png");
            fileInfo.put("size", imageData.length);
            out.println(fileInfo.toString());
            
            OutputStream socketOut = socket.getOutputStream();
            socketOut.write(imageData, 0, imageData.length);
            socketOut.flush();
            Logger.info("Обложка по умолчанию отправлена. Размер: " + imageData.length + " байт");
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
        
        public void setTitle(String title) { this.title = title; }
        public void setDuration(String duration) { this.duration = duration; }
        public void setArtist(String artist) { this.artist = artist; }
        public void setAudioFilename(String audioFilename) { this.audioFilename = audioFilename; }
        public void setCoverFilename(String coverFilename) { this.coverFilename = coverFilename; }
    }
}
