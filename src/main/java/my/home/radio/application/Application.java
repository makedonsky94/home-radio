package my.home.radio.application;

import my.home.radio.http.Manager;
import my.home.radio.http.api.Auth;
import my.home.radio.http.Player;
import my.home.radio.http.api.Track;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Main application class
 */
public class Application {
    private static Logger LOGGER = Logger.getLogger(Application.class);

    private Auth auth;
    private Player player;

    /**
     * Точка входа в приложение
     */
    public void run() {
        try {
            auth = new Auth().call();

            while(!Manager.getInstance().isAvailable()) {
                wait(1000);
                LOGGER.warn("Yandex radio isn't available");
            }

            Console console = new Console();
            console.setDaemon(true);
            console.start();

            player = new Player(console);
            while(true) {
                List<Track> tracks = Manager.getInstance().getTracks();

                if(tracks.size() == 0) {
                    LOGGER.warn("Track list is empty");
                    return;
                }

                LOGGER.info("Track list has been accepted");
                LOGGER.info(tracks);

                for(Track track : tracks) {
                    playMusic(track);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Application has been closed with IOException", e);
        } catch (InterruptedException e) {
            LOGGER.error("Wait call error", e);
        }
    }

    private void playMusic(Track track) throws IOException {
        Manager.getInstance().startTrack(auth, track);

        LOGGER.info("\u001b[0;31m" + "Current track: " + track.toString() + "\u001b[m");
        player.play(auth, track);
    }

    /**
     * List of commands:
     * exit: Close application
     * stop: Stop current music and play next
     * dislike: Stop current music, make 'disabled' mark and play next
     */
    public static class Console extends Thread {
        Scanner sc = new Scanner(System.in);
        List<String> commands = new CopyOnWriteArrayList<>();

        @Override
        public void run() {
            while (true) {
                if(sc.hasNext()) {
                    String string = sc.next();
                    if(string.toLowerCase().equals("exit")) {
                        System.exit(0);
                    }
                    commands.add(string);
                }
            }
        }

        public boolean hasCommands() {
            return commands.size() > 0;
        }

        public String next() {
            if(hasCommands()) {
                String command = commands.get(0);
                commands.remove(0);
                return command;
            }
            return "";
        }
    }
}
