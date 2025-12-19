import java.io.*;
import java.net.*;
import java.util.Base64;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Font;
import org.json.*;

public class ClientHandler extends Thread {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private DatabaseManager dbManager;
    private volatile boolean isConnectionClosed = false;
    
    public ClientHandler(Socket socket, DatabaseManager dbManager) {
        this.socket = socket;
        this.dbManager = dbManager;
    }
    
    @Override
    public void run() {
        String clientAddress = null;
        try {
            clientAddress = socket.getInetAddress().toString();
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            
            Logger.info("Обработчик клиента запущен для: " + clientAddress);
            
            String request;
            while ((request = in.readLine()) != null && !isConnectionClosed) {
                Logger.debug("Получен JSON запрос от " + clientAddress + ": " + request);
                
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
                            dbManager.loadDatabase();
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
                            response.put("nextId", dbManager.generateUniqueId());
                            break;
                            
                        default:
                            response.put("status", "ERROR");
                            response.put("message", "Неизвестная команда: " + command);
                    }
                    
                    // Отправка ответа теперь ВНУТРИ try-блока
                    if (!isConnectionClosed) {
                        out.println(response.toString());
                        Logger.debug("Отправлен JSON ответ клиенту " + clientAddress + ": " + response.toString());
                    } else {
                        Logger.debug("Соединение закрыто, пропускаем отправку ответа");
                    }
                    
                } catch (JSONException e) {
                    if (!isConnectionClosed) {
                        JSONObject errorResponse = new JSONObject();
                        errorResponse.put("status", "ERROR");
                        errorResponse.put("message", "Некорректный JSON запрос: " + e.getMessage());
                        out.println(errorResponse.toString());
                    }
                    Logger.warning("Некорректный JSON запрос от " + clientAddress + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            if (e.getMessage() != null && 
                (e.getMessage().contains("Broken pipe") || 
                 e.getMessage().contains("Connection reset") ||
                 e.getMessage().contains("Socket closed"))) {
                Logger.debug("Клиент " + clientAddress + " разорвал соединение: " + e.getMessage());
            } else if (e.getMessage() != null && e.getMessage().contains("Connection refused")) {
                Logger.debug("Клиент " + clientAddress + " отказал в соединении");
            } else {
                Logger.error("Ошибка ввода-вывода у клиента " + clientAddress, e);
            }
        } catch (Exception e) {
            Logger.error("Неожиданная ошибка у клиента " + clientAddress, e);
        } finally {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                Logger.debug("Соединение с клиентом " + clientAddress + " закрыто");
            } catch (IOException e) {
                Logger.debug("Ошибка при закрытии сокета клиента " + clientAddress + ": " + e.getMessage());
            }
        }
    }
    
    private void handleGetAll(JSONObject response) {
        JSONArray tracksArray = new JSONArray();
        
        for (MusicRecord record : dbManager.getMusicData()) {
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
        MusicRecord record = dbManager.findRecordById(id);
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
            MusicRecord record = dbManager.findRecordById(id);
            
            if (record == null) {
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("status", "ERROR");
                errorResponse.put("message", "Файл с ID " + id + " не найден");
                if (!isConnectionClosed) {
                    out.println(errorResponse.toString());
                }
                Logger.warning("Файл с ID " + id + " не найден");
                return;
            }
            
            sendFile(ServerConfig.MUSIC_DIR, record.getAudioFilename());
            
        } catch (Exception e) {
            if (e instanceof SocketException && 
                e.getMessage() != null && 
                (e.getMessage().contains("Broken pipe") || 
                 e.getMessage().contains("Connection reset") ||
                 e.getMessage().contains("Socket closed"))) {
                Logger.debug("Клиент разорвал соединение при запросе файла ID: " + id);
            } else {
                Logger.error("Ошибка при отправке файла ID: " + id, e);
            }
        }
    }
    
    private void handleGetCover(String coverFilename) {
        try {
            if (coverFilename == null || coverFilename.equals("-")) {
                createAndSendDefaultCover("Default Cover", "No Artist");
            } else {
                sendFile(ServerConfig.COVERS_DIR, coverFilename);
            }
        } catch (Exception e) {
            if (e instanceof SocketException && 
                e.getMessage() != null && 
                (e.getMessage().contains("Broken pipe") || 
                 e.getMessage().contains("Connection reset") ||
                 e.getMessage().contains("Socket closed"))) {
                Logger.debug("Клиент разорвал соединение при запросе обложки: " + coverFilename);
            } else {
                Logger.error("Ошибка при отправке обложки: " + coverFilename, e);
            }
        }
    }
    
    private void sendFile(String directory, String filename) throws IOException {
        if (isConnectionClosed) {
            Logger.debug("Соединение уже закрыто, пропускаем отправку файла: " + filename);
            return;
        }
        
        File file = new File(directory + File.separator + filename);
        if (!file.exists()) {
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("status", "ERROR");
            errorResponse.put("message", "Файл не найден: " + filename);
            if (!isConnectionClosed) {
                out.println(errorResponse.toString());
            }
            Logger.warning("Файл не найден: " + filename);
            return;
        }
        
        Logger.info("Отправка файла: " + file.getName() + " размер: " + file.length() + " байт");
        
        JSONObject fileInfo = new JSONObject();
        fileInfo.put("status", "FILE");
        fileInfo.put("filename", filename);
        fileInfo.put("size", file.length());
        
        if (!isConnectionClosed) {
            out.println(fileInfo.toString());
            Logger.debug("Отправлен заголовок файла: " + filename);
        } else {
            Logger.debug("Соединение закрыто, пропускаем отправку заголовка");
            return;
        }
        
        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis);
             OutputStream socketOut = socket.getOutputStream()) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalSent = 0;
            
            while ((bytesRead = bis.read(buffer)) != -1) {
                if (isConnectionClosed) {
                    Logger.debug("Соединение закрыто, прерываем отправку файла: " + filename);
                    break;
                }
                
                try {
                    socketOut.write(buffer, 0, bytesRead);
                    totalSent += bytesRead;
                } catch (SocketException e) {
                    if (e.getMessage() != null && 
                        (e.getMessage().contains("Broken pipe") || 
                         e.getMessage().contains("Connection reset") ||
                         e.getMessage().contains("Socket closed"))) {
                        Logger.debug("Клиент разорвал соединение во время отправки файла: " + filename);
                        isConnectionClosed = true;
                        break;
                    } else {
                        throw e;
                    }
                }
            }
            
            if (!isConnectionClosed) {
                try {
                    socketOut.flush();
                    Logger.info("Файл успешно отправлен. Отправлено: " + totalSent + " байт из " + file.length());
                } catch (SocketException e) {
                    if (e.getMessage() != null && 
                        (e.getMessage().contains("Broken pipe") || 
                         e.getMessage().contains("Connection reset") ||
                         e.getMessage().contains("Socket closed"))) {
                        Logger.debug("Клиент разорвал соединение при завершении отправки файла: " + filename);
                        isConnectionClosed = true;
                    }
                }
            }
            
        } catch (SocketException e) {
            if (e.getMessage() != null && 
                (e.getMessage().contains("Broken pipe") || 
                 e.getMessage().contains("Connection reset") ||
                 e.getMessage().contains("Socket closed"))) {
                Logger.debug("Клиент разорвал соединение при отправке файла: " + filename);
                isConnectionClosed = true;
            } else {
                throw e;
            }
        }
    }
    
    private void createAndSendDefaultCover(String title, String artist) throws IOException {
        if (isConnectionClosed) {
            Logger.debug("Соединение уже закрыто, пропускаем создание обложки");
            return;
        }
        
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
        
        if (!isConnectionClosed) {
            out.println(fileInfo.toString());
        } else {
            Logger.debug("Соединение закрыто, пропускаем отправку заголовка обложки");
            return;
        }
        
        try {
            if (!isConnectionClosed) {
                OutputStream socketOut = socket.getOutputStream();
                socketOut.write(imageData, 0, imageData.length);
                socketOut.flush();
                Logger.info("Обложка по умолчанию отправлена. Размер: " + imageData.length + " байт");
            }
        } catch (SocketException e) {
            if (e.getMessage() != null && 
                (e.getMessage().contains("Broken pipe") || 
                 e.getMessage().contains("Connection reset") ||
                 e.getMessage().contains("Socket closed"))) {
                Logger.debug("Клиент разорвал соединение при отправке обложки");
                isConnectionClosed = true;
            } else {
                throw e;
            }
        }
    }
    
    private void handleAddTrack(JSONObject request, JSONObject response) {
        try {
            String id = request.optString("id", "");
            if (id.isEmpty()) {
                id = dbManager.generateUniqueId();
            }
            
            // Проверяем, существует ли уже такой ID
            if (dbManager.findRecordById(id) != null) {
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
                File audioFile = new File(ServerConfig.MUSIC_DIR + File.separator + audioFilename);
                
                try (FileOutputStream fos = new FileOutputStream(audioFile)) {
                    fos.write(audioData);
                }
                Logger.info("Аудиофайл сохранен: " + audioFile.getAbsolutePath());
            }
            
            // Сохраняем обложку
            if (request.has("coverData") && !request.getString("coverData").isEmpty()) {
                String coverDataBase64 = request.getString("coverData");
                byte[] coverData = Base64.getDecoder().decode(coverDataBase64);
                File coverFile = new File(ServerConfig.COVERS_DIR + File.separator + coverFilename);
                
                try (FileOutputStream fos = new FileOutputStream(coverFile)) {
                    fos.write(coverData);
                }
                Logger.info("Обложка сохранена: " + coverFile.getAbsolutePath());
            }
            
            // Создаем запись
            MusicRecord record = new MusicRecord(id, title, duration, artist, audioFilename, coverFilename);
            dbManager.addRecord(record);
            
            // Сохраняем базу
            dbManager.saveDatabase();
            
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
            MusicRecord record = dbManager.findRecordById(id);
            
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
                    dbManager.deleteFile(ServerConfig.MUSIC_DIR, oldAudioFilename);
                }
                
                File audioFile = new File(ServerConfig.MUSIC_DIR + File.separator + newAudioFilename);
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
                        dbManager.deleteFile(ServerConfig.COVERS_DIR, oldCoverFilename);
                    }
                    
                    File coverFile = new File(ServerConfig.COVERS_DIR + File.separator + newCoverFilename);
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
            dbManager.saveDatabase();
            
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
            MusicRecord record = dbManager.findRecordById(id);
            
            if (record == null) {
                response.put("status", "ERROR");
                response.put("message", "Трек с ID " + id + " не найден");
                Logger.warning("Попытка удалить несуществующий трек с ID: " + id);
                return;
            }
            
            // Удаляем файлы
            boolean audioDeleted = dbManager.deleteFile(ServerConfig.MUSIC_DIR, record.getAudioFilename());
            boolean coverDeleted = dbManager.deleteFile(ServerConfig.COVERS_DIR, record.getCoverFilename());
            
            // Удаляем запись
            dbManager.removeRecord(record);
            
            // Сохраняем базу
            dbManager.saveDatabase();
            
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
}