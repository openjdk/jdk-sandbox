
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.KeyStore;
import java.util.Base64;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.remote.rest.PlatformRestAdapter;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.testng.annotations.Test;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @test @modules java.logging java.management java.management.rest
 * @run testng/othervm RestAdapterTest
 *
 */
@Test
public class RestAdapterTest {

    private static final String CHARSET = "UTF-8";
    private static String sslAgentConfig;
    private static String sslClientConfig;
    private static String passwordFile;
    private static String configFile;

    private void createAgentSslConfigFile(String fileName) throws IOException {
        File f = new File(fileName);
        if (f.exists()) {
            return;
        }
        Properties props = new Properties();
        String testDir = System.getProperty("test.src");
        props.setProperty("javax.net.ssl.keyStore", testDir + File.separator + "keystoreAgent");
        props.setProperty("javax.net.ssl.keyStorePassword", "glopglop");
        props.setProperty("javax.net.ssl.trustStore", testDir + File.separator + "truststoreAgent");
        props.setProperty("javax.net.ssl.trustStorePassword", "glopglop");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            props.store(writer, "");
        }
    }

    private void createClientSslConfigFile(String fileName) throws IOException {
        File f = new File(fileName);
        if (f.exists()) {
            return;
        }
        Properties props = new Properties();
        String testDir = System.getProperty("test.src");
        props.setProperty("javax.net.ssl.keyStore", testDir + File.separator + "keystoreClient");
        props.setProperty("javax.net.ssl.keyStorePassword", "glopglop");
        props.setProperty("javax.net.ssl.trustStore", testDir + File.separator + "truststoreClient");
        props.setProperty("javax.net.ssl.trustStorePassword", "glopglop");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            props.store(writer, "");
        }
    }

    private void setupMgmtConfig(String fileName) throws IOException {
        Properties props = new Properties();

        props.setProperty("com.sun.management.jmxremote.ssl", "true");
        props.setProperty("com.sun.management.jmxremote.ssl.config.file", sslAgentConfig);
        props.setProperty("com.sun.management.jmxremote.password.file", passwordFile);
        props.setProperty("com.sun.management.jmxremote.rest.port", "0");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            props.store(writer, "");
        }
    }

    private void setupConfig() throws Exception {
        String testSrcRoot = System.getProperty("test.src") + File.separator;
        sslAgentConfig = testSrcRoot + "sslConfigAgent";
        sslClientConfig = testSrcRoot + "sslConfigClient";
        passwordFile = testSrcRoot + "password.properties";
        configFile = testSrcRoot + "mgmt.properties";
        createAgentSslConfigFile(sslAgentConfig);
        createClientSslConfigFile(sslClientConfig);
        setupMgmtConfig(configFile);
    }

    @BeforeClass
    public void setupAdapter() throws Exception {
        setupConfig();
        File file = new File(configFile);
        Properties props = new Properties();
        props.load(new FileInputStream(file));
        if (props.get("com.sun.management.jmxremote.rest.port") != null) {
            PlatformRestAdapter.init((String) props.get("com.sun.management.jmxremote.rest.port"), props);
        }
        SSLContext ctx = getSSlContext(sslClientConfig);
        HttpsURLConnection.setDefaultSSLSocketFactory(ctx.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier(
                (String hostname, javax.net.ssl.SSLSession sslSession) -> hostname.equals("Harsha-Wardhana-B"));
    }

    @AfterClass
    public void tearDown() {
        PlatformRestAdapter.stop();
    }

    private static SSLContext getSSlContext(String sslConfigFileName) {
        final String keyStore, keyStorePassword, trustStore, trustStorePassword;

        try {
            Properties p = new Properties();
            BufferedInputStream bin = new BufferedInputStream(new FileInputStream(sslConfigFileName));
            p.load(bin);
            keyStore = p.getProperty(PlatformRestAdapter.PropertyNames.SSL_KEYSTORE_FILE);
            keyStorePassword = p.getProperty(PlatformRestAdapter.PropertyNames.SSL_KEYSTORE_PASSWORD);
            trustStore = p.getProperty(PlatformRestAdapter.PropertyNames.SSL_TRUSTSTORE_FILE);
            trustStorePassword = p.getProperty(PlatformRestAdapter.PropertyNames.SSL_TRUSTSTORE_PASSWORD);

            char[] keyStorePasswd = null;
            if (keyStorePassword.length() != 0) {
                keyStorePasswd = keyStorePassword.toCharArray();
            }

            char[] trustStorePasswd = null;
            if (trustStorePassword.length() != 0) {
                trustStorePasswd = trustStorePassword.toCharArray();
            }

            KeyStore ks = null;
            if (keyStore != null) {
                ks = KeyStore.getInstance(KeyStore.getDefaultType());
                FileInputStream ksfis = new FileInputStream(keyStore);
                ks.load(ksfis, keyStorePasswd);
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, keyStorePasswd);

            KeyStore ts = null;
            if (trustStore != null) {
                ts = KeyStore.getInstance(KeyStore.getDefaultType());
                FileInputStream tsfis = new FileInputStream(trustStore);
                ts.load(tsfis, trustStorePasswd);
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ts);

            SSLContext ctx = SSLContext.getInstance("SSL");
            ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            return ctx;
        } catch (Exception ex) {
            Logger.getLogger(PlatformRestAdapter.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    private String executeHttpRequest(String inputUrl, String charset) throws IOException {
        if (inputUrl != null) {
            URL url = new URL(PlatformRestAdapter.getBaseURL() + (charset != null ? URLEncoder.encode(inputUrl, charset) : inputUrl));
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.setDoOutput(false);
            String userCredentials = "username1:password1";
            String basicAuth = "Basic " + Base64.getEncoder().encodeToString(userCredentials.getBytes());
            con.setRequestProperty("Authorization", basicAuth);
            if (charset != null && !charset.isEmpty()) {
                con.setRequestProperty("Content-Type", "application/json; charset=" + charset);
            }
            try {
                int status = con.getResponseCode();
                if (status == 200) {

                    StringBuilder sbuf;
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(con.getInputStream()))) {
                        sbuf = new StringBuilder();
                        String input;
                        while ((input = br.readLine()) != null) {
                            sbuf.append(charset != null ? URLDecoder.decode(input, charset) : input);
                        }
                    }
                    return sbuf.toString();
                } else {
                    StringBuilder sbuf;
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(con.getErrorStream()))) {
                        sbuf = new StringBuilder();
                        String input;
                        while ((input = br.readLine()) != null) {
                            sbuf.append(charset != null ? URLDecoder.decode(input, charset) : input);
                        }
                    }
                    return sbuf.toString();
                }
            } catch (IOException e) {
            }
        }
        return null;
    }
    
    @Test
    public void testMBeanServerCollection() throws IOException {
        System.out.println(executeHttpRequest("/platform/mbeans/java.lang:type=Runtime",null));
//        System.out.println(executeHttpRequest("/platform/mbeans/java.lang:name=CodeHeap%20'non-nmethods',type=MemoryPool/info", null));
    }
}
