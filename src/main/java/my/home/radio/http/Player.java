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

/**
 * Main player of the application
 */
public class Player {
    private static final Logger LOGGER = Logger.getLogger(Player.class);

    private String src;
    private String result;
    private Application.Console console;

    public Player(Application.Console console) {
        this.console = console;
    }

    /**
     * Play track
     * @throws IOException
     */
    public void play(Auth auth, Track track) throws IOException {
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
            AudioInputStream din = AudioSystem.getAudioInputStream(decodedFormat, stream);
            play(auth, track, decodedFormat, din);
            in.close();
        } catch (UnsupportedAudioFileException | LineUnavailableException e) {
            e.printStackTrace();
        }
    }


    private void play(Auth auth ,Track track, AudioFormat targetFormat, AudioInputStream inputStream) throws IOException, LineUnavailableException {
        byte[] data = new byte[4096];
        SourceDataLine line = getLine(targetFormat);
        if (line != null)
        {
            line.start();
            int nBytesRead = 0;
            while (nBytesRead != -1)
            {
                if(console.hasCommands()) {
                    String command = console.next().toLowerCase();
                    if(command.equals("stop")) {
                        break;
                    }
                    if(command.equals("dislike")) {
                        Manager.getInstance().dislikeTrack(auth, track);
                        break;
                    }
                }
                nBytesRead = inputStream.read(data, 0, data.length);
                if (nBytesRead != -1) {
                    line.write(data, 0, nBytesRead);
                }
            }
            line.drain();
            line.stop();
            line.close();
            inputStream.close();
            Manager.getInstance().endTrack(auth, track, 10);
        }
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
