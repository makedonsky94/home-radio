package my.home.radio.http.api;

import my.home.radio.Configuration;
import my.home.radio.http.Manager;
import org.apache.log4j.Logger;

import java.io.IOException;

/**
 * Api settings
 */
public class Settings implements ApiObject<Settings> {
    private static final Logger LOGGER = Logger.getLogger(Settings.class);
    private static final String URL_PATH = Configuration.DOMAIN + Configuration.API_VERSION + "/handlers/radio/genre/electronics/settings";

    @Override
    public Settings call() throws IOException {
        String json = Manager.getInstance().get(URL_PATH, null);
        LOGGER.info(json);
        return this;
    }
}
