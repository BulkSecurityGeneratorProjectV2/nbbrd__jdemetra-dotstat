package internal.favicon;

import com.google.common.net.InternetDomainName;
import ec.tstoolkit.design.VisibleForTesting;
import internal.util.http.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public final class GoogleSupplier implements FaviconSupplier {

    private final HttpClient client;

    public GoogleSupplier() {
        this(new DefaultHttpClient(HttpContext.builder().build()));
    }

    @VisibleForTesting
    GoogleSupplier(HttpClient client) {
        this.client = client;
    }

    @Override
    public String getName() {
        return "Google";
    }

    @Override
    public Image getFaviconOrNull(URL url) throws IOException {
        try (HttpResponse response = client.send(getFaviconRequest(url))) {
            try (InputStream stream = response.getBody()) {
                return ImageIO.read(stream);
            }
        } catch (HttpResponseException ex) {
            if (isDefaultFavicon(ex)) {
                return null;
            }
            throw ex;
        }
    }

    private HttpRequest getFaviconRequest(URL url) throws MalformedURLException {
        InternetDomainName domainName = InternetDomainName.from(url.getHost());
        return HttpRequest
                .builder()
                .query(new URL("https://www.google.com/s2/favicons?domain=" + domainName))
                .build();
    }

    private static boolean isDefaultFavicon(HttpResponseException ex) {
        return ex.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND;
    }
}
