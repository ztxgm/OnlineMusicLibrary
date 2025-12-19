public class MusicRecord {
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