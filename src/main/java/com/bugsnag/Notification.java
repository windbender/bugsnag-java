package com.bugsnag;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import com.bugsnag.utils.StringUtils;
import com.bugsnag.utils.JSONUtils;

public class Notification {
    private static final String NOTIFIER_NAME = "Java Bugsnag Notifier";
    private static final String NOTIFIER_VERSION = "2.0.0";
    private static final String NOTIFIER_URL = "https://bugsnag.com";

    private Configuration config;
    private List<Error> errorList = new ArrayList<Error>();
    private List<String> errorStrings = new ArrayList<String>();

    public Notification(Configuration config) {
        this.config = config;
    }

    public Notification(Configuration config, Error error) {
        this(config);
        addError(error);
    }

    public void addError(Error error) {
        errorList.add(error);
    }

    public void addError(String errorString) {
        errorStrings.add(errorString);
    }

    public JSONObject toJSON() {
        // Outer payload
        JSONObject notification = new JSONObject();
        JSONUtils.safePut(notification, "apiKey", config.getApiKey());

        // Notifier info
        JSONObject notifier = new JSONObject();
        JSONUtils.safePut(notifier, "name", NOTIFIER_NAME);
        JSONUtils.safePut(notifier, "version", NOTIFIER_VERSION);
        JSONUtils.safePut(notifier, "url", NOTIFIER_URL);
        JSONUtils.safePut(notification, "notifier", notifier);

        // Error array
        JSONArray errors = new JSONArray();
        for(Error error : errorList) {
            errors.put(error.toJSON());
        }
        for(String errorString : errorStrings) {
            try {
                JSONObject error = new JSONObject(errorString);
                errors.put(error);
            } catch(JSONException e) {
                config.getLogger().warn("Error when parsing error json string", e);
            }
        }
        JSONUtils.safePut(notification, "events", errors);

        return notification;
    }

    public String toString() {
        return toJSON().toString();
    }

    public void deliver() throws IOException {
        if(errorList.isEmpty() && errorStrings.isEmpty())
            return;

        String url = config.getEndpoint();
        request(url, this.toString(), "application/json");

        config.getLogger().info(String.format("Sent %d error(s) to %s", size(), url));
    }

    public int size() {
        return errorList.size() + errorStrings.size();
    }

    private void request(String urlString, String payload, String contentType) throws IOException {
        request(urlString, StringUtils.stringToByteArray(payload), contentType);
    }

    private void request(String urlString, byte[] payload, String contentType) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true); 
            conn.setFixedLengthStreamingMode(payload.length);

            // Set the content type header
            if(contentType != null) {
                conn.addRequestProperty("Content-Type", contentType);
            }

            // Send request headers and body
            conn.getOutputStream().write(payload);

            // End the request, get the response code
            int status = conn.getResponseCode();
            if(status / 100 != 2) {
                config.getLogger().warn(String.format("Got non-200 response code from %s: %d", urlString, status));
            }
        } finally {
            if(conn != null) {
                conn.disconnect();
            }
        }
    }
}