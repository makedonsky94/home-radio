package my.home.radio.http;

import my.home.radio.Configuration;
import my.home.radio.help.Set;
import my.home.radio.http.api.Auth;
import my.home.radio.http.api.Track;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Manager for http queries
 */
public class Manager {
    private static final Logger LOGGER = Logger.getLogger(Manager.class);

    private static Manager instance;
    public static Manager getInstance() {
        if(instance == null) {
            instance = new Manager();
        }
        return instance;
    }

    private CookieStore store;
    private HttpClient client;

    /**
     * Test variable.
     * Contains name of genre.
     * List of genres:
     * -pop
     * -disco
     * -indie
     * -local-indie
     * -rock
     * -metal
     * -alternative
     * -lounge
     * -electronics
     */
    private String genre = "disco";

    public Manager() {
        store = getCookieStore();
        client = getClient(store);
    }

    public String get(String urlString, Config config) throws IOException {
        HttpGet httpGet = new HttpGet(urlString);

        String cookies = getCookiesString();

        if(config == null) {
            config = Config.defaultGetConfig();
            config.parameters.add("Referer", "https://radio.yandex.ru/genre/" + genre);
            config.parameters.add("X-Retpath-Y", "https://radio.yandex.ru/genre/" + genre);
            config.parameters.add("Cookie", cookies);
        }

        for(int i = 0; i < config.parameters.size(); i++) {
            Set.Element<String, String> element = config.parameters.get(i);
            httpGet.setHeader(element.getKey(), element.getValue());
        }

        HttpResponse response = client.execute(httpGet);
        LOGGER.debug(httpGet);
        LOGGER.debug(response);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(response.getEntity().getContent()));
        String inputLine;
        StringBuilder result = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            result.append(inputLine);
        }
        in.close();


        saveCookies(store.getCookies());

        return result.toString();
    }

    public String post(String urlString, Config config, HttpEntity data, String contentType) throws IOException {
        HttpPost httpPost = new HttpPost(urlString);

        String cookies = getCookiesString();

        if(config == null) {
            config = Config.defaultPostConfig();
            config.parameters.add("Referer", "https://radio.yandex.ru/genre/" + genre);
            config.parameters.add("X-Retpath-Y", "https://radio.yandex.ru/genre/" + genre);
            config.parameters.add("Cookie", cookies);
            config.parameters.add("Content-Type", contentType);
        }

        for(int i = 0; i < config.parameters.size(); i++) {
            Set.Element<String, String> element = config.parameters.get(i);
            httpPost.setHeader(element.getKey(), element.getValue());
        }

        httpPost.setEntity(data);

        HttpResponse response = client.execute(httpPost);
        LOGGER.debug(httpPost);
        LOGGER.debug(data);
        LOGGER.debug(response);

        BufferedReader rd = new BufferedReader(
                new InputStreamReader(response.getEntity().getContent()));

        StringBuilder result = new StringBuilder();
        String line;
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }
        rd.close();

        saveCookies(store.getCookies());

        return result.toString();
    }

    public boolean isAvailable() throws IOException {
        String path = Configuration.DOMAIN + Configuration.API_VERSION + "/handlers/radio/genre/"+genre+"/available";
        String jsonString = get(path, null);
        JSONObject jsonObject = new JSONObject(jsonString);
        return jsonObject.getBoolean("available");
    }

    public List<Track> getTracks() throws IOException {
        String result = get("https://radio.yandex.ru/api/v2.1/handlers/radio/genre/"+genre+"/tracks?queue=", null);
        JSONObject tracks = new JSONObject(result);
        JSONArray array = tracks.getJSONArray("tracks");
        List<Track> trackList = new ArrayList<>();
        LOGGER.debug("Track list: " + array.toString());
        for(int i = 0; i < array.length(); i++) {
            JSONObject trackObject = array.getJSONObject(i);
            if(trackObject.getString("type").equals("track")) {
                trackList.add(new Track(trackObject, genre));
            }
        }
        return trackList;
    }

    public String startTrack(Auth auth, Track track) throws IOException {
        String path = Configuration.DOMAIN + Configuration.API_VERSION + "/handlers/radio/genre/"+genre+"/feedback/trackStarted/"
                + track.getId() + ":" + track.getAlbumId();

        List<BasicNameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair("timestamp", String.valueOf(new Date().getTime())));
        urlParameters.add(new BasicNameValuePair("from", "radio-web-genre-"+genre+"-direct"));
        urlParameters.add(new BasicNameValuePair("sign", auth.getHash()));
        urlParameters.add(new BasicNameValuePair("external-domain", "radio.yandex.ru"));
        urlParameters.add(new BasicNameValuePair("overembed", "no"));
        urlParameters.add(new BasicNameValuePair("from", "radio-web-genre-"+genre+"-direct"));
        urlParameters.add(new BasicNameValuePair("batchId", track.getBatchId()));
        urlParameters.add(new BasicNameValuePair("trackId", track.getId()));
        urlParameters.add(new BasicNameValuePair("albumId", track.getAlbumId()));

        return post(path, null, new UrlEncodedFormEntity(urlParameters), "application/x-www-form-urlencoded");
    }

    public String endTrack(Auth auth, Track track, double duration) throws IOException {
        LOGGER.debug("Track duration: " + duration);
        String path = Configuration.DOMAIN + Configuration.API_VERSION + "/handlers/radio/genre/"+genre+"/feedback/trackFinished/"
                + track.getId() + ":" + track.getAlbumId();

        List<BasicNameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair("timestamp", String.valueOf(new Date().getTime())));
        urlParameters.add(new BasicNameValuePair("from", "radio-web-genre-"+genre+"-direct"));
        urlParameters.add(new BasicNameValuePair("sign", auth.getHash()));
        urlParameters.add(new BasicNameValuePair("external-domain", "radio.yandex.ru"));
        urlParameters.add(new BasicNameValuePair("overembed", "no"));
        urlParameters.add(new BasicNameValuePair("from", "radio-web-genre-"+genre+"-direct"));
        urlParameters.add(new BasicNameValuePair("batchId", track.getBatchId()));
        urlParameters.add(new BasicNameValuePair("trackId", track.getId()));
        urlParameters.add(new BasicNameValuePair("albumId", track.getAlbumId()));
        urlParameters.add(new BasicNameValuePair("totalPlayed", String.valueOf(duration)));

        return post(path, null, new UrlEncodedFormEntity(urlParameters), "application/x-www-form-urlencoded");
    }

    public String dislikeTrack(Auth auth, Track track) throws IOException {
        String path = Configuration.DOMAIN + Configuration.API_VERSION + "/handlers/radio/genre/"+genre+"/feedback/dislike/"
                + track.getId() + ":" + track.getAlbumId();

        List<BasicNameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair("timestamp", String.valueOf(new Date().getTime())));
        urlParameters.add(new BasicNameValuePair("from", "radio-web-genre-"+genre+"-direct"));
        urlParameters.add(new BasicNameValuePair("sign", auth.getHash()));
        urlParameters.add(new BasicNameValuePair("external-domain", "radio.yandex.ru"));
        urlParameters.add(new BasicNameValuePair("overembed", "no"));
        urlParameters.add(new BasicNameValuePair("from", "radio-web-genre-"+genre+"-direct"));
        urlParameters.add(new BasicNameValuePair("batchId", track.getBatchId()));
        urlParameters.add(new BasicNameValuePair("trackId", track.getId()));
        urlParameters.add(new BasicNameValuePair("albumId", track.getAlbumId()));

        return post(path, null, new UrlEncodedFormEntity(urlParameters), "application/x-www-form-urlencoded");
    }


    private void saveCookies(List<Cookie> cookies) {
        JSONArray array = new JSONArray();
        for(Cookie cookie : cookies) {
            JSONObject object = new JSONObject();
            object.put("name", cookie.getName());
            object.put("value", cookie.getValue());
            object.put("comment", cookie.getComment());
            object.put("commentUrl", cookie.getCommentURL());
            object.put("domain", cookie.getDomain());
            object.put("expiry", cookie.getExpiryDate());
            object.put("path", cookie.getPath());
            object.put("ports", cookie.getPorts());
            object.put("version", cookie.getVersion());
            array.put(object);
        }
        try {
            Files.write(Paths.get("cookies.json"), array.toString().getBytes("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private CookieStore getCookieStore() {
        CookieStore cookieStore = new BasicCookieStore();
        List<Cookie> cookies = getCookies();
        cookies.forEach(cookieStore::addCookie);
        return cookieStore;
    }

    private HttpClient getClient(CookieStore cookieStore) {
        RequestConfig requestConfig = RequestConfig
                .custom()
                .setCookieSpec(CookieSpecs.STANDARD)
                .setSocketTimeout(5000)
                .setConnectTimeout(5000)
                .setConnectionRequestTimeout(5000)
                .build();
        HttpClientBuilder builder = HttpClientBuilder
                .create()
                .setDefaultCookieStore(cookieStore)
                .setDefaultRequestConfig(requestConfig);
        return builder.build();
    }

    private List<Cookie> getCookies() {
        List<Cookie> cookies = new ArrayList<>();
        try {
            byte[] bytes = Files.readAllBytes(Paths.get("cookies.json"));
            String json = new String(bytes);
            JSONArray array = new JSONArray(json);
            for(int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                BasicClientCookie cookie = new BasicClientCookie(object.getString("name"), object.getString("value"));
                cookie.setDomain(object.getString("domain"));
                DateFormat dateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.US);
                cookie.setExpiryDate(dateFormat.parse(object.getString("expiry")));
                cookie.setPath(object.getString("path"));
                cookie.setVersion(object.getInt("version"));
                cookies.add(cookie);
            }
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
        return cookies;
    }

    private String getCookiesString() {
        List<Cookie> cookies = getCookies();
        StringBuilder cookiesString = new StringBuilder();
        for(Cookie cookie : cookies) {
            cookiesString
                    .append(cookie.getName())
                    .append("=")
                    .append(cookie.getValue())
                    .append(";");
        }
        return cookiesString.toString();
    }

    public static class Config {
        Set<String, String> parameters;

        public void replace(String key, String newValue) {
            parameters.change(key, newValue);
        }

        public void remove(String key) {
            parameters.remove(key);
        }

        public Config(Set<String, String> parameters) {
            this.parameters = parameters;
        }

        public static Config defaultGetConfig() {
            Set<String, String> parameters = new Set<>();
            parameters.add("Accept", "application/json; q=1.0, text/*; q=0.8, */*; q=0.1");
            parameters.add("Accept-Encoding", "gzip, deflate, sdch, br");
            parameters.add("Accept-Language", "ru-RU,ru;q=0.8,en-US;q=0.6,en;q=0.4");
            parameters.add("Cache-Control", "max-age=0");
            parameters.add("Connection", "keep-alive");
            parameters.add("Host", "radio.yandex.ru");
            parameters.add("Save-Data", "on");
            parameters.add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.103 Safari/537.36");
            parameters.add("X-Requested-With", "XMLHttpRequest");
            return new Config(parameters);
        }

        public static Config defaultPostConfig() {
            Set<String, String> parameters = new Set<>();
            parameters.add("Accept", "application/json; q=1.0, text/*; q=0.8, */*; q=0.1");
            parameters.add("Accept-Encoding", "gzip, deflate, br");
            parameters.add("Accept-Language", "ru-RU,ru;q=0.8,en-US;q=0.6,en;q=0.4");
            parameters.add("Cache-Control", "max-age=0");
            parameters.add("Connection", "keep-alive");
            parameters.add("Host", "radio.yandex.ru");
            parameters.add("Origin", "https://radio.yandex.ru");
            parameters.add("Save-Data", "on");
            parameters.add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.103 Safari/537.36");
            parameters.add("X-Requested-With", "XMLHttpRequest");
            return new Config(parameters);
        }
    }
}
