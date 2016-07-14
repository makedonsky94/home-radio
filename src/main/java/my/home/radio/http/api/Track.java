package my.home.radio.http.api;


import org.json.JSONObject;

public class Track {
    private int id;
    private int albumId;
    private String title;
    private String batchId;
    private String genre;

    public Track(JSONObject jsonObject, String genre) {
        this.genre = genre;
        JSONObject track = jsonObject.getJSONObject("track");
        id = track.getInt("id");
        albumId = track
                .getJSONArray("albums")
                .getJSONObject(0)
                .getInt("id");
        batchId = track.getString("batchId");
        title = track.getString("title");
    }

    public String getId() {
        return String.valueOf(id);
    }

    public String getAlbumId() {
        return String.valueOf(albumId);
    }

    public String getBatchId() {
        return batchId;
    }

    @Override
    public String toString() {
        return "Track{" +
                "id=" + id +
                ", albumId=" + albumId +
                ", title='" + title + '\'' +
                ", batchId='" + batchId + '\'' +
                '}';
    }

    public String getGenre() {
        return genre;
    }
}
