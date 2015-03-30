package sample;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import com.google.common.base.Joiner;

import static java.lang.System.err;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class Utils {
    public static URL url(String s) {
        try {
            return new URL(s);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static URI uri(String s) {
        try {
            return new URI(s);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void copy(URI url, Path path) {
        try {
            Files.copy(url.toURL().openStream(), path, REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void log(Object... os) {
        err.printf("%s,%s\n", Thread.currentThread().getName(), Joiner.on(",").join(os));
    }
}
