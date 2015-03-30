package sample;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.net.URI;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Key;
import lombok.Data;
import lombok.SneakyThrows;
import rx.Observable;
import rx.subjects.AsyncSubject;
import rx.subjects.PublishSubject;

import static sample.Utils.uri;

/**
 * 発言をひたすら通知します
 */
@ClientEndpoint
public class SlackClient {
    private PublishSubject<String> subject = PublishSubject.create();
    private ObjectMapper mapper = new ObjectMapper();

    @Data
    public static class BaseEvent {
        private String type;
    }

    @Data
    public static class MessageEvent {
        private String channel;
        private String user;
        private String text;
    }

    @Data
    public static class RtmStart {
        private String url;
        private boolean ok;
    }

    public SlackClient(HttpTransport httpTransport, String token) {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        connect(httpTransport, token);
    }

    @SneakyThrows
    private void connect(HttpTransport httpTransport, String token) {
        log("WS 接続情報を取得します");
        HttpRequestFactory requestFactory = httpTransport.createRequestFactory();
        GenericUrl url = new GenericUrl("https://slack.com/api/rtm.start?token=" + token);

        HttpRequest req = requestFactory.buildGetRequest(url);
        HttpResponse res = req.execute();

        URI wsUri;
        try {
            String resStr = res.parseAsString();
            log(resStr);
            RtmStart startRes = mapper.readValue(resStr, RtmStart.class);
            if (!startRes.isOk()) {
                throw new RuntimeException("接続失敗");
            }
            log("WS 接続情報を取得しました");
            wsUri = uri(startRes.getUrl());
        } finally {
            res.disconnect();
        }

        log("WS 接続します");
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.connectToServer(this, wsUri);
    }

    /**
     * 発言のストリーム
     */
    public Observable<String> getMessages() {
        return subject;
    }

    private void log(String log) {
        System.out.println(log);
    }

    @OnOpen
    public void onOpen(final Session userSession) {
    }

    @OnClose
    public void onClose(final Session userSession, final CloseReason reason) {
        log("Slack との WS がクローズしました");
    }

    @OnMessage
    @SneakyThrows
    public void onMessage(final String message) {
        log("受信: " + message);

        BaseEvent a = mapper.readValue(message, BaseEvent.class);
        switch (a.getType()) {
        case "message":
            MessageEvent messageEvent = mapper.readValue(message, MessageEvent.class);
            subject.onNext(messageEvent.getText());
            break;
        default:
            break;
        }
    }

}