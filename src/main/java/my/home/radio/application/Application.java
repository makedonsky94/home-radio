package my.home.radio.application;

import my.home.radio.http.Manager;
import my.home.radio.models.Auth;
import my.home.radio.http.Player;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main application class
 */
public class Application {
    private static Logger LOGGER = Logger.getLogger(Application.class);

    /**
     * Run application
     */
    public void run() throws InterruptedException {
        while(true) {
            try {
                Auth auth = new Auth().call();

                while (!Manager.getInstance().isAvailable()) {
                    Thread.sleep(1000);
                    LOGGER.warn("Yandex radio isn't available");
                }

                Console console = new Console();
                console.setDaemon(true);
                console.start();

                Socket socket = new Socket();
                socket.setDaemon(true);
                socket.start();

                Player player = new Player(console, socket);
                player.start(auth);
            } catch (IOException e) {
                LOGGER.error("Application has been closed with IOException", e);
            }

            LOGGER.info("Restarting application in 10 seconds...");
            Thread.sleep(10000);
        }
    }

    /**
     * List of commands:
     * exit: Close application
     * stop: Stop current music and start next
     * dislike: Stop current music, make 'disabled' mark and start next
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
                        return;
                    }
                    if(string.toLowerCase().equals("help")) {
                        LOGGER.info("Available commands: next, previous, volume_<value>, genre_<value>, pause, mute, exit");
                        continue;
                    }
                    if(string.toLowerCase().equals("genres")) {
                        String[] genres = {
                                "pop",
                                "disco",
                                "indie",
                                "local-indie",
                                "rock",
                                "metal",
                                "alternative",
                                "lounge",
                                "electronics",
                                "dubstep",
                                "industrial",
                                "experimental",
                                "dance",
                                "house",
                                "techno",
                                "trance",
                                "dnb",
                                "rap",
                                "rusrap",
                                "rnb",
                                "urban",
                                "soul",
                                "funk",
                                "jazz",
                                "tradjazz",
                                "conjazz",
                                "soundtrack",
                                "films",
                                "videogame",
                                "relax"
                        };
                        LOGGER.info(Arrays.toString(genres));
                        continue;
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

    public static class Socket extends Thread {
        List<String> commands = new CopyOnWriteArrayList<>();

        @Override
        public void run() {
            try (Selector selector = Selector.open();
                 ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
                LOGGER.info("Server start listener");
                serverSocketChannel.socket().bind(new InetSocketAddress("localhost", 4545));
                serverSocketChannel.configureBlocking(false);
                serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
                runSocketListener(selector, serverSocketChannel);
            } catch(IOException ex) {
                LOGGER.error(ex.getMessage());
            }
        }

        private void runSocketListener(Selector selector, ServerSocketChannel serverSocketChannel) throws IOException {
            while (true) {
                selector.select();
                Set selectedKeys = selector.selectedKeys();
                Iterator iterator = selectedKeys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = (SelectionKey) iterator.next();
                    if (selectionKey.isAcceptable()) {
                        SocketChannel sock = serverSocketChannel.accept();
                        if(sock != null) {
                            sock.configureBlocking(false);
                            sock.register(selector, SelectionKey.OP_READ);
                            LOGGER.info(sock.toString() + " connected");
                        }
                    }

                    if (selectionKey.isReadable()) {
                        SocketChannel socketChannel =
                                (SocketChannel) selectionKey.channel();
                        try {
                            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                            socketChannel.read(byteBuffer);
                            socketChannel.register(selector, SelectionKey.OP_WRITE);

                            String msg = new String(byteBuffer.array());
                            Pattern pattern = Pattern.compile("(?<=command\\s)\\S+");
                            Matcher matcher = pattern.matcher(msg);
                            if(matcher.find()) {
                                String command = matcher.group();

                                if(command.equals("exit")) {
                                    System.exit(0);
                                }

                                LOGGER.info("Added command " + command);
                                commands.add(command);
                            }
                        } catch(IOException e) {
                            e.printStackTrace();
                            socketChannel.register(selector, SelectionKey.OP_WRITE);
                            LOGGER.warn(socketChannel.toString() + " register to write");
                        }
                    }
                    iterator.remove();
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
