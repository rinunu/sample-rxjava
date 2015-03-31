package sample;


import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.GenericData;
import com.google.common.base.Strings;
import lombok.SneakyThrows;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import rx.Observable;
import rx.schedulers.Schedulers;

import static sample.Utils.*;

class Sample {
    private HttpTransport httpTransport = new NetHttpTransport();

    @SneakyThrows
    public void downloagSampleMain() {
        Path downloadDir = Paths.get("download");
        Files.createDirectories(downloadDir);

        Observable<URI> uris = Observable.just(
            uri("http://matome.naver.jp/odai/2134521672725107901"),
            uri("http://nekomemo.com/"));

        uris.flatMap(uri -> getImageUris(uri))
            .flatMap(uri -> {
                return downloadAsync(uri, downloadDir)
                    .onErrorResumeNext(throwable -> {
                        log("error なのでスキップ");
                        return Observable.empty();
                    });
            })
            .subscribe();

        log("wait");
        sleep(1000000);
    }

    /**
     * Slack から URL の入力を受けて、 Slack に書き込むサンプル
     */
    @SneakyThrows
    public void slackSampleMain() {
        getSlackMessages()
                .filter(message -> message.startsWith("get-image"))
                        // URI は <...> という形式に変換されて投稿される
                .map(s -> s.replaceAll(".*<(.*)>.*", "$1"))
                        // URI に変換(変換できないものは無視)
                .flatMap(mes -> Observable.just(uri(mes)))
                .onErrorResumeNext(throwable -> Observable.empty())
                .filter(uri -> uri.isAbsolute()) // 絶対 URI だけ残す
                .flatMap(uri -> getImageUris(uri)
                        .filter(imageUri -> imageUri.toString().toLowerCase().endsWith(".jpg")) // jpg だけ
                        .take(2)) // 最初の2つだけ残す
                .flatMap(uri -> sendToSlack(uri.toString()))
                .subscribe();

        sleep(1000000);
    }

    public Observable<String> readLines() {
        Scanner in = new Scanner(System.in);
        return Observable.create(subscriber -> {
                while (true) {
                    if (subscriber.isUnsubscribed()) {
                        break;
                    }
                    subscriber.onNext(in.next());
                }
            }
        );
    }

    /**
     * 指定した URL の HTML に含まれる image URL のリストを取得します
     */
    public Observable<URI> getImageUris(URI uri) {
        return Observable.create(subscriber -> {
            try {
                log("getImageUris : " + uri);
                Document doc = Jsoup.connect(uri.toString()).get();

                for (Element img : doc.select("img")) {
                    URI imgUri = uri(img.attr("src"));

                    if (!imgUri.isAbsolute()) {
                        subscriber.onNext(uri.resolve(imgUri));
                    } else {
                        subscriber.onNext(imgUri);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 指定された URL のファイルを DL して、ファイルに保存し、その Path を返します
     */
    public Observable<Path> download(URI uri, Path dir) {
        // ファイルシステムで使っていい文字に置換した URL をファイル名にする
        Path safePath = dir.resolve(uri.toString().replaceAll("[^.\\w]", "_"));

        return Observable.create(subscriber -> {
                try {
                    log("download start", uri);
                    copy(uri, safePath);
                    sleep(1000);
                    log("download end,", uri);
                    subscriber.onNext(safePath);
                    subscriber.onCompleted();
                } catch (Exception e) {
                    subscriber.onError(e);
                }
            }
        );
    }

    /**
     * download の非同期版
     */
    public Observable<Path> downloadAsync(URI uri, Path dir) {
        // observable を scheduler 上で動かす
        // http://reactivex.io/documentation/operators/subscribeon.html
        return download(uri, dir).subscribeOn(Schedulers.newThread());
    }

    public Observable<String> getSlackMessages() {
        String token = System.getenv("SLACK_TOKEN");
        if (token == null) {
            throw new RuntimeException("SLACK_TOKEN を設定してね");
        }

        SlackClient client = new SlackClient(httpTransport, token);
        return client.getMessages();
    }

    /**
     * Slack にメッセージ送ってみる
     */
    public Observable<Void> sendToSlack(String message) {
        // curl -X POST --data-urlencode 'payload={"text": "This is posted to <#general> and comes from *monkey-bot*.", "channel": "#general", "username": "monkey-bot", "icon_emoji": ":monkey_face:"}' https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX
        return Observable.create(subscriber -> {
                    try {
                        HttpRequestFactory requestFactory = httpTransport.createRequestFactory();

                        String endpoint = System.getenv("SLACK_ENDPOINT");
                        if (Strings.isNullOrEmpty(endpoint)) {
                            throw new RuntimeException("SLACK_ENDPOINT 環境変数を設定してね");
                        }
                        GenericUrl url = new GenericUrl(endpoint);
                        GenericData data = new GenericData();
                        data.put("text", message);
//                        data.put("channel", "#test_");
                        data.put("channel", "#general");
                        data.put("username", "#rinu-bot");
                        data.put("icon_emoji", ":monkey_face:");

                        HttpContent a = new JsonHttpContent(new JacksonFactory(), data);
                        HttpRequest req = requestFactory.buildPostRequest(url, a);

                        HttpResponse res = req.execute();
                        res.disconnect();
                    } catch (Exception e) {
                        subscriber.onError(e);
                    }
                }
        );
    }
}


public class Main {
    public static void main(String[] args) {
        new Sample().slackSampleMain();
    }
}
