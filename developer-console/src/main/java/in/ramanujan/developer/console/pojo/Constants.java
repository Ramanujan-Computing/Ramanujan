package in.ramanujan.developer.console.pojo;

public class Constants {
    public static String ramanujanDependencyDir = System.getProperty("user.home") + "/.ramanujan/dependencies/";

    public static String getServerUrl() {
        String url = System.getenv("RAMANUJAN_SERVER_URL");
        if (url == null || url.trim().isEmpty()) {
            url = System.getProperty("server.url", "https://server.ramanujan.dev");
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
