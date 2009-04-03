package bixo.fetcher;

import java.net.URL;
import java.util.Random;

import org.apache.log4j.Logger;

public class FakeHttpFetcher implements IHttpFetcher {
    private static Logger LOGGER = Logger.getLogger(FakeHttpFetcher.class);
    
    private boolean _randomFetching;
    private Random _rand;
    
    public FakeHttpFetcher() {
        this(true);
    }
    
    public FakeHttpFetcher(boolean randomFetching) {
        _randomFetching = randomFetching;
        _rand = new Random();
    }
    
    @Override
    public FetchResult get(String url) {
        try {
            URL theUrl = new URL(url);
            
            int statusCode = 0;
            int contentSize = 10000;
            int bytesPerSecond = 100000;
            
            if (_randomFetching) {
                contentSize = Math.max(0, (int)(_rand.nextGaussian() * 5000.0) + 10000) + 100;
                bytesPerSecond = Math.max(0, (int)(_rand.nextGaussian() * 50000.0) + 100000) + 1000;
            } else {
                String query = theUrl.getQuery();
                if (query != null) {
                    String[] params = query.split("&");
                    for (String param : params) {
                        String[] keyValue = param.split("=");
                        if (keyValue[0].equals("status")) {
                            statusCode = Integer.parseInt(keyValue[1]);
                        } else if (keyValue[0].equals("size")) {
                            contentSize = Integer.parseInt(keyValue[1]);
                        } else if (keyValue[0].equals("speed")) {
                            bytesPerSecond = Integer.parseInt(keyValue[1]);
                        } else {
                            LOGGER.warn("Unknown fake URL parameter: " + keyValue[0]);
                        }
                    }
                }
            }
            
            FetchStatusCode status = new FetchStatusCode(statusCode);
            FetchContent content = new FetchContent(url, url, System.currentTimeMillis(), new byte[contentSize], "text/html");
            
            // Now we want to delay for as long as it would take to fill in the data.
            float duration = (float)contentSize/(float)bytesPerSecond;
            Thread.sleep((long)duration * 1000L);
            return new FetchResult(status, content);
        } catch (Throwable t) {
            LOGGER.error("Exception: " + t.getMessage(), t);
            return new FetchResult(new FetchStatusCode(-1), new FetchContent(url, url, System.currentTimeMillis(), null, null));
        }
    }

}