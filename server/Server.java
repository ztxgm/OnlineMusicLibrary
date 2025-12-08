import java.io.*;
import java.net.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Font;

public class Server {
    private static final int PORT = 12345;
    private static final String DB_FILE = "music_db.txt";
    private static final String MUSIC_DIR = "music";
    private static final String COVERS_DIR = "covers";
    private static List<String> musicData = new ArrayList<>();

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
    
    private static void createSampleData() throws IOException { // !
        try (PrintWriter writer = new PrintWriter(new FileWriter(DB_FILE))) {
            writer.println("1:Bohemian Rhapsody:6:07:Queen:queen_bohemian.mp3:-");
            writer.println("2:Hotel California:6:30:Eagles:eagles_hotel.mp3:-");
            writer.println("3:Stairway to Heaven:8:02:Led Zeppelin:zeppelin_stairway.mp3:-");
            writer.println("4:Smooth Criminal:4:17:Michael Jackson:jackson_smooth.mp3:-");
            writer.println("5:Smells Like Teen Spirit:5:01:Nirvana:nirvana_teen.mp3:-");
            writer.println("6:Billie Jean:4:54:Michael Jackson:jackson_billie.mp3:-");
            writer.println("7:Like a Rolling Stone:6:13:Bob Dylan:dylan_rolling.mp3:-");
            writer.println("8:Imagine:3:03:John Lennon:lennon_imagine.mp3:-");
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
                    } else if (request.startsWith("GET_FILE:")) {
                        String filename = request.substring(9);
                        sendFile(MUSIC_DIR, filename, socket.getOutputStream());
                        break;
                    } else if (request.startsWith("GET_COVER:")) {
                        String coverFilename = request.substring(10);
                        sendCover(coverFilename, socket.getOutputStream());
                        break;
                    } else if (request.startsWith("GET_FILE_INFO:")) {
                        String id = request.substring(14);
                        String[] fileInfo = getFileInfo(id);
                        if (fileInfo != null) {
                            out.println(fileInfo[0] + ":" + fileInfo[1]);
                        } else {
                            out.println("ERROR:File not found");
                        }
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
        
        private void sendFile(String directory, String filename, OutputStream socketOut) {
            try {
                File file = new File(directory + File.separator + filename);
                if (!file.exists()) {
                    System.out.println("Файл не найден: " + file.getAbsolutePath());
                    PrintWriter out = new PrintWriter(socketOut, true);
                    out.println("ERROR:File not found");
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
                    // Если обложки нет, создаем обложку по умолчанию
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
            
            // Градиентный фон
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int r = 40 + (x * 60) / width;
                    int g = 40 + (y * 60) / height;
                    int b = 100;
                    int rgb = (r << 16) | (g << 8) | b;
                    image.setRGB(x, y, rgb);
                }
            }
            
            // Текст
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
        
        private String[] getFileInfo(String id) {
            for (String record : musicData) {
                String[] parts = record.split(":");
                if (parts.length >= 6 && parts[0].equals(id)) {
                    String audioFile = parts[5];
                    String coverFile = parts.length >= 7 ? parts[6] : "-";
                    return new String[]{audioFile, coverFile};
                }
            }
            return null;
        }
    }
}