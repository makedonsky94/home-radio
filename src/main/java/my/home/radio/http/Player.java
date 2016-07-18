package my.home.radio.http;


import my.home.radio.Configuration;
import my.home.radio.application.Application;
import my.home.radio.http.api.Auth;
import my.home.radio.http.api.Track;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import javax.sound.sampled.*;
import java.io.*;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Main player of the application
 */
public class Player {
    private static final Logger LOGGER = Logger.getLogger(Player.class);

    private String src;
    private String result;
    private Application.Console console;

    private List<Track> trackHistory;

    public Player(Application.Console console) {
        this.console = console;
        this.trackHistory = new ArrayList<>();
    }

    /**
     * Play track
     * @throws IOException
     */
    public void play(Auth auth, Track track) throws IOException {
        LOGGER.info("\u001b[0;31m" + "Current track: " + track.toString() + "\u001b[m");
        String path = Configuration.DOMAIN + Configuration.API_VERSION
                + "/handlers/track/" +track.getId() + ":" + track.getAlbumId() + "/radio-web-genre-"+track.getGenre()+"-direct/download/m?hq=0&external-domain=radio.yandex.ru&overembed=no";
        String json = Manager.getInstance().get(path, null);

        JSONObject jsonObject = new JSONObject(json);
        src = jsonObject.getString("src") + "&format=json";

        Manager.Config config = Manager.Config.defaultGetConfig();
        config.parameters.add("Referer", "https://radio.yandex.ru/genre/" + track.getGenre());
        config.parameters.add("X-Retpath-Y", "https://radio.yandex.ru/genre/" + track.getGenre());
        config.replace("Referer", "https://radio.yandex.ru/mood/calm");
        config.replace("Host", "storage.mds.yandex.net");
        config.remove("X-Retpath-Y");

        result = Manager.getInstance().get(src, config);

        JSONObject downloadInformation = new JSONObject(result);
        DownloadInfo info = DownloadInfo.fromJSON(downloadInformation);
        String downloadPath = info.getSrc();

        URL url = new URL(downloadPath);

        URLConnection uc = url.openConnection();

        InputStream in = new BufferedInputStream(uc.getInputStream());

        try {
            if(trackHistory.indexOf(track) == -1) {
                trackHistory.add(track);
            }

            LOGGER.info("Start playing music");
            AudioInputStream stream = AudioSystem.getAudioInputStream(in);
            AudioFormat baseFormat = stream.getFormat();
            AudioFormat decodedFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false);
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(decodedFormat, stream);
            play(auth, track, decodedFormat, audioInputStream);
            in.close();
        } catch (UnsupportedAudioFileException | LineUnavailableException e) {
            e.printStackTrace();
        }
    }


    private void play(Auth auth, Track track, AudioFormat targetFormat, AudioInputStream inputStream) throws IOException, LineUnavailableException {
        byte[] data = new byte[4096];
        SourceDataLine line = getLine(targetFormat);
        if (line != null)
        {
            line.start();
            playLine(auth, track, data, line, inputStream);
            closeLine(auth, track, line, inputStream);
        }
    }

    private void playLine(Auth auth, Track track, byte[] data, SourceDataLine line, AudioInputStream inputStream) throws IOException {
        int nBytesRead = 0;
        while (nBytesRead != -1)
        {
            if(console.hasCommands()) {
                String command = console.next().toLowerCase();
                if(command.equals("next")) {
                    break;
                }
                if(command.equals("previous")) {
                    closeLine(auth, track, line, inputStream);
                    int previousIndex = trackHistory.size() > 1 ? trackHistory.size() - 2 : 0;
                    Track previousTrack = trackHistory.get(previousIndex);
                    play(auth, previousTrack);
                    break;
                }
                if(command.equals("dislike")) {
                    Manager.getInstance().dislikeTrack(auth, track);
                    break;
                }
                if(command.equals("increase_volume")) {
                    FloatControl floatControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                    float value = floatControl.getValue() + 1f;
                    value = value > 6f ? 6f : value;
                    floatControl.setValue(value);
                    LOGGER.info("Current volume value: " + value);
                }
                if(command.equals("decrease_volume")) {
                    FloatControl floatControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                    float value = floatControl.getValue() - 1f;
                    value = value < -80.0f ? -80.0f : value;
                    floatControl.setValue(value);
                    LOGGER.info("Current volume value: " + value);
                }
                if(command.equals("mute")) {
                    BooleanControl booleanControl = (BooleanControl) line.getControl(BooleanControl.Type.MUTE);
                    booleanControl.setValue(!booleanControl.getValue());
                    LOGGER.info("Muted: " + booleanControl.getValue());
                }
            }
            nBytesRead = inputStream.read(data, 0, data.length);
            if (nBytesRead != -1) {
                line.write(data, 0, nBytesRead);
            }
        }
    }

    private void closeLine(Auth auth, Track track, SourceDataLine line, AudioInputStream inputStream) throws IOException {
        double duration = line.getMicrosecondPosition() / (double) 1000 / (double) 1000;
        line.drain();
        line.stop();
        line.close();
        inputStream.close();
        Manager.getInstance().endTrack(auth, track, duration);
    }

    private SourceDataLine getLine(AudioFormat audioFormat) throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
        SourceDataLine res = (SourceDataLine) AudioSystem.getLine(info);
        res.open(audioFormat);
        return res;
    }

    @Override
    public String toString() {
        return "Player{" +
                ", result='" + result + '\'' +
                ", src='" + src + '\'' +
                '}';
    }

    /**
     * Information about downloaded track
     */
    private static class DownloadInfo {
        String s;
        String ts;
        String path;
        String host;
        static final String SALT = "XGRlBW9FXlekgbPrRHuSiA";

        public static DownloadInfo fromJSON(JSONObject jsonObject) {
            DownloadInfo info = new DownloadInfo();
            info.s = jsonObject.getString("s");
            info.ts = jsonObject.getString("ts");
            info.path = jsonObject.getString("path");
            info.host = jsonObject.getString("host");
            return info;
        }

        /**
         * Generating path to track
         */
        public String getSrc() {
            try {
                String toHash = SALT + path.substring(1) + s;
                byte[] toHashBytes = toHash.getBytes();
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] hashBytes = md.digest(toHashBytes);
                BigInteger bigInt = new BigInteger(1, hashBytes);
                String md5Hex = bigInt.toString(16);
                while( md5Hex.length() < 32 ){
                    md5Hex = "0" + md5Hex;
                }
                return "https://" + host + "/get-mp3/" + md5Hex + "/" + ts + path;
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
            return "";
        }
    }
}
