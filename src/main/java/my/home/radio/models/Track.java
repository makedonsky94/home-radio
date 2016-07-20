package my.home.radio.models;


import org.json.JSONArray;
import org.json.JSONObject;

public class Track {
    private int id;
    private int albumId;
    private String title;
    private String batchId;
    private String genre;
    private String artist;

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

        JSONArray artists = track.getJSONArray("artists");
        StringBuilder artistNameBuilder = new StringBuilder();
        for(int i = 0; i < artists.length(); i++) {
            artistNameBuilder.append(artists.getJSONObject(i).getString("name"));
            if(i != artists.length() - 1) {
                artistNameBuilder.append(", ");
            }
        }

        artist = artistNameBuilder.toString();
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
                ", artist='" + artist + '\'' +
                ", title='" + title + '\'' +
                ", batchId='" + batchId + '\'' +
                ", genre='" + genre + '\'' +
                '}';
    }

    public String getGenre() {
        return genre;
    }
}
