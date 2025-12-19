import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
    private static final String LOG_FILE = "client.log";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private static PrintWriter logWriter;
    
    static {
        try {
            logWriter = new PrintWriter(new FileWriter(LOG_FILE, true), true);
        } catch (IOException e) {
            System.err.println("Не удалось создать файл лога: " + e.getMessage());
            logWriter = new PrintWriter(System.out, true);
        }
    }
    
    public enum Level {
        INFO, WARNING, ERROR, DEBUG
    }
    
    private static void log(Level level, String message, Throwable throwable) {
        String timestamp = DATE_FORMAT.format(new Date());
        String threadName = Thread.currentThread().getName();
        String logMessage = String.format("[%s] [%s] [%s] %s", 
            timestamp, threadName, level, message);
        
        logWriter.println(logMessage);
        
        if (level != Level.DEBUG) {
            System.out.println(logMessage);
        }
        
        if (throwable != null) {
            throwable.printStackTrace(logWriter);
            if (level != Level.DEBUG) {
                throwable.printStackTrace(System.out);
            }
        }
        
        logWriter.flush();
    }
    
    public static void info(String message) {
        log(Level.INFO, message, null);
    }
    
    public static void warning(String message) {
        log(Level.WARNING, message, null);
    }
    
    public static void error(String message) {
        log(Level.ERROR, message, null);
    }
    
    public static void error(String message, Throwable throwable) {
        log(Level.ERROR, message, throwable);
    }
    
    public static void debug(String message) {
        if (isDebugEnabled()) {
            log(Level.DEBUG, message, null);
        }
    }
    
    private static boolean isDebugEnabled() {
        return "true".equals(System.getProperty("client.debug", "false"));
    }
    
    public static void close() {
        if (logWriter != null) {
            logWriter.flush();
            logWriter.close();
        }
    }
}

