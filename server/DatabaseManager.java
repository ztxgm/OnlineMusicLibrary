import java.io.*;
import java.util.*;
import org.json.*;

public class DatabaseManager {
    private String dbFile;
    private List<MusicRecord> musicData;
    
    public DatabaseManager(String dbFile) {
        this.dbFile = dbFile;
        this.musicData = new ArrayList<>();
    }
    
    public void loadDatabase() {
        try {
            File file = new File(dbFile);
            if (!file.exists()) {
                Logger.warning("База данных отсутствует, создаем пустую...");
                saveDatabase();
                return;
            }
            
            musicData.clear();
            try (BufferedReader reader = new BufferedReader(new FileReader(dbFile))) {
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
    
    public void saveDatabase() {
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
            
            try (FileWriter writer = new FileWriter(dbFile)) {
                writer.write(jsonArray.toString(2));
            }
            Logger.info("База данных сохранена. Записей: " + musicData.size());
        } catch (IOException e) {
            Logger.error("Ошибка сохранения базы данных: " + e.getMessage(), e);
        }
    }
    
    public MusicRecord findRecordById(String id) {
        for (MusicRecord record : musicData) {
            if (record.getId().equals(id)) {
                return record;
            }
        }
        return null;
    }
    
    public boolean deleteFile(String directory, String filename) {
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
    
    public String generateUniqueId() {
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
    
    public List<MusicRecord> getMusicData() {
        return musicData;
    }
    
    public void addRecord(MusicRecord record) {
        musicData.add(record);
    }
    
    public void removeRecord(MusicRecord record) {
        musicData.remove(record);
    }
}