package my.home.radio.http;


import my.home.radio.Configuration;
import my.home.radio.application.Application;
import my.home.radio.help.Strings;
import my.home.radio.models.Auth;
import my.home.radio.models.Track;
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
import java.util.Arrays;
import java.util.List;

/**
 * Main player of the application
 */
public class Player {
    private static final Logger LOGGER = Logger.getLogger(Player.class);

    private Application.Console console;
    private Application.Socket socket;

    private List<Track> trackHistory;

    private boolean pause;
    private boolean mute;

    public Player(Application.Console console, Application.Socket socket) {
        this.console = console;
        this.socket = socket;
        this.trackHistory = new ArrayList<>();
    }

    /**
     * Starts playing tracks
     * @throws IOException
     */
    public void start(Auth auth) throws IOException {
        while(true) {
            List<Track> tracks = Manager.getInstance().getTracks();

            if(tracks.size() == 0) {
                LOGGER.warn("Track list is empty");
                return;
            }

            LOGGER.info("Track list has been accepted");
            LOGGER.info(tracks);

            for(Track track : tracks) {
                startTrack(auth, track);
            }
        }
    }

    /**
     * Starts specific track
     * @throws IOException
     */
    private void startTrack(Auth auth, Track track) throws IOException {
        Manager.getInstance().startTrack(auth, track);

        LOGGER.info(Strings.CONSOLE_SEPARATOR);
        LOGGER.info("Current track: " + track.toString());
        LOGGER.info(Strings.CONSOLE_SEPARATOR);

        String path = Configuration.DOMAIN + Configuration.API_VERSION
                + "/handlers/track/" +track.getId() + ":" + track.getAlbumId() + "/radio-web-genre-"+track.getGenre()+"-direct/download/m?hq=0&external-domain=radio.yandex.ru&overembed=no";
        String json = Manager.getInstance().get(path, null);

        JSONObject jsonObject = new JSONObject(json);
        String src = jsonObject.getString("src") + "&format=json";

        Manager.Config config = Manager.Config.defaultGetConfig();
        config.parameters.add("Referer", "https://radio.yandex.ru/genre/" + track.getGenre());
        config.parameters.add("X-Retpath-Y", "https://radio.yandex.ru/genre/" + track.getGenre());
        config.replace("Referer", "https://radio.yandex.ru/mood/calm");
        config.replace("Host", "storage.mds.yandex.net");
        config.remove("X-Retpath-Y");

        String result = Manager.getInstance().get(src, config);

        JSONObject downloadInformation = new JSONObject(result);
        DownloadInfo info = DownloadInfo.fromJSON(downloadInformation);
        String downloadPath = info.getSrc();

        URL url = new URL(downloadPath);

        URLConnection uc = url.openConnection();

        LOGGER.info("File size: " + uc.getContentLengthLong() + " bytes");

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

            SoundBuffer soundBuffer = new SoundBuffer();
            play(auth, track, decodedFormat, audioInputStream, soundBuffer);
            in.close();
        } catch (UnsupportedAudioFileException | LineUnavailableException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Plays music
     * @param targetFormat Decoded music format
     * @param inputStream AudioInputStream which created with {@code targetFormat}
     * @throws IOException
     * @throws LineUnavailableException
     * @throws InterruptedException
     */
    private void play(Auth auth, Track track, AudioFormat targetFormat, AudioInputStream inputStream, SoundBuffer buffer) throws IOException, LineUnavailableException, InterruptedException {
        SourceDataLine line = getLine(targetFormat);
        if (line != null)
        {
            line.start();
            playLine(auth, track, line, inputStream, buffer);
            closeLine(auth, track, line, inputStream, buffer);
        }
    }

    /**
     * Plays SourceDataLine
     * @throws IOException
     * @throws InterruptedException
     */
    private void playLine(Auth auth, Track track, SourceDataLine line, AudioInputStream inputStream, SoundBuffer soundBuffer) throws IOException, InterruptedException {
        setSettings(line);

        ByteReader reader = new ByteReader(inputStream, soundBuffer);
        reader.setDaemon(true);
        reader.start();
        byte[] bytes = new byte[4096];

        while (true)
        {
            String command = null;
            if(console.hasCommands()) {
                command = console.next().toLowerCase().trim();
            }
            if(socket.hasCommands() && command == null) {
                command = socket.next().toLowerCase().trim();
            }
            if(command != null) {
                if(command.equals("next")) {
                    pause = false;
                    soundBuffer.destroy();
                    break;
                }
                if(command.equals("previous")) {
                    pause = false;
                    closeLine(auth, track, line, inputStream, soundBuffer);
                    int previousIndex = trackHistory.size() > 1 ? trackHistory.size() - 2 : 0;
                    Track previousTrack = trackHistory.get(previousIndex);
                    startTrack(auth, previousTrack);
                    break;
                }
                if(command.equals("dislike")) {
                    Manager.getInstance().dislikeTrack(auth, track);
                    break;
                }
                if(command.contains("volume_")) {
                    int value = Integer.valueOf(command.replace("volume_", ""));
                    if(value < 0 || value > 100) {
                        LOGGER.warn("Volume value can't be less than 0 and greater than 100");
                        break;
                    }
                    float onePercent = 86.0f / 100;
                    float increaseValue = onePercent * value;
                    float volume = -80.0f + increaseValue;
                    volume = Math.round(volume);
                    volume = volume > 6f ? 6f : volume;
                    volume = volume < -80.0f ? -80.0f : volume;
                    FloatControl floatControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                    floatControl.setValue(volume);
                    LOGGER.info("Current volume value: " + volume);
                }
                if(command.equals("mute")) {
                    mute = !mute;
                    setSettings(line);
                    LOGGER.info("Muted: " + mute);
                }
                if(command.equals("pause")) {
                    pause = !pause;
                    LOGGER.info("Paused: " + pause);
                }
                if(command.contains("genre_")) {
                    String genre = command.replace("genre_", "");
                    closeLine(auth, track, line, inputStream, soundBuffer);
                    Manager.getInstance().setGenre(genre);
                    start(auth);
                    break;
                }
            }

            if(pause) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    LOGGER.error("Error when called wait method", e);
                }
                continue;
            }

            if(soundBuffer.read(bytes)) {
                line.write(bytes, 0, bytes.length);
            } else {
                break;
            }

        }
    }

    /**
     * Closes SourceDataLine and sends information about ending of track
     * @throws IOException
     */
    private void closeLine(Auth auth, Track track, SourceDataLine line, AudioInputStream inputStream, SoundBuffer buffer) throws IOException {
        if(!line.isOpen()) {
            return;
        }
        double duration = line.getMicrosecondPosition() / (double) 1000 / (double) 1000;
        line.drain();
        line.stop();
        line.close();
        inputStream.close();
        buffer.destroy();
        Manager.getInstance().endTrack(auth, track, duration);
    }

    /**
     * Method helper for {@link Player#play(Auth, Track, AudioFormat, AudioInputStream, SoundBuffer)}
     * @return SourceDataLine which got from {@code audioFormat}
     * @throws LineUnavailableException
     */
    private SourceDataLine getLine(AudioFormat audioFormat) throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
        SourceDataLine res = (SourceDataLine) AudioSystem.getLine(info);
        res.open(audioFormat);
        return res;
    }

    private void setSettings(SourceDataLine line) {
        BooleanControl booleanControl = (BooleanControl) line.getControl(BooleanControl.Type.MUTE);
        booleanControl.setValue(mute);
    }

    /**
     * Thread for reading bytes from stream
     */
    private static class ByteReader extends Thread {
        InputStream inputStream;
        SoundBuffer buffer;

        long timestamp = System.currentTimeMillis();
        final int interval = 1000;
        byte[] data = new byte[4096];

        public ByteReader(InputStream inputStream, SoundBuffer buffer) {
            this.inputStream = inputStream;
            this.buffer = buffer;
        }

        @Override
        public void run() {
            int bytesRead;
            while(true) {
                try {
                    if (buffer.overwrite()) {
                        long now = System.currentTimeMillis();
                        if (timestamp + interval >= now) {
                            Thread.sleep(timestamp + interval - now);
                        }
                        timestamp = now;
                    }

                    bytesRead = inputStream.read(data, 0, data.length);

                    if(bytesRead == -1) {
                        buffer.close();
                        LOGGER.info("Downloading completed.");
                        break;
                    }

                    buffer.write(data);
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
    }

    /**
     * Container for keeping bytes array
     */
    private static class SoundBuffer {
        private static int FRAGMENT_SIZE = 4096;
        private volatile byte[] array = new byte[FRAGMENT_SIZE * 10];
        private int step;
        private int read;
        private boolean end;

        /**
         * read bytes from stream
         * @param out array to read
         * @return true, if it's not end of stream
         * @throws InterruptedException
         */
        public boolean read(byte[] out) throws InterruptedException {
            if (end && read >= step) {
                return false;
            }

            while (read >= step) {
                if(end) {
                    return false;
                }

                Thread.sleep(200);
            }

            System.arraycopy(array, read * FRAGMENT_SIZE, out, 0, FRAGMENT_SIZE);
            read++;
            return true;
        }

        /**
         * write bytes array to buffer
         */
        public void write(byte[] in) {
            if(end) {
                return;
            }

            if ((step + 1) * FRAGMENT_SIZE >= array.length) {
                array = Arrays.copyOf(array, (int) (array.length * 1.5));
            }

            System.arraycopy(in, 0, array, FRAGMENT_SIZE * step, FRAGMENT_SIZE);
            step++;
        }

        /**
         * returns true, if written bytes much more than read
         */
        public boolean overwrite() {
            return step - read > 3000;
        }

        /**
         * close buffer
         */
        public void close() {
            end = true;
        }

        public void destroy() {
            end = true;
            array = null;
        }
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
