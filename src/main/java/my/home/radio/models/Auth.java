package my.home.radio.models;


import my.home.radio.Configuration;
import my.home.radio.http.Manager;
import org.json.JSONObject;

import java.io.IOException;

public class Auth implements ApiObject<Auth> {
    private static final String URL_PATH = Configuration.DOMAIN + Configuration.API_VERSION + "/handlers/auth";

    private String hash;
    private String deviceId;

    @Override
    public Auth call() throws IOException {
        String json = Manager.getInstance().get(URL_PATH, null);
        JSONObject jsonObject = new JSONObject(json);
        hash = jsonObject.getString("csrf");
        deviceId = jsonObject.getString("device_id");
        return this;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getHash() {
        return hash;
    }

    @Override
    public String toString() {
        return "Auth{" +
                "hash='" + hash + '\'' +
                ", deviceId='" + deviceId + '\'' +
                '}';
    }
}
