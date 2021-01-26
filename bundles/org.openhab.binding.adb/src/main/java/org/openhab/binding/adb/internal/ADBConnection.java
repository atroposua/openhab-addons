package org.openhab.binding.adb.internal;

import java.io.File;
import java.net.Socket;
import java.util.concurrent.*;

import javax.xml.bind.DatatypeConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tananaev.adblib.AdbBase64;
import com.tananaev.adblib.AdbConnection;
import com.tananaev.adblib.AdbCrypto;
import com.tananaev.adblib.AdbStream;

public class ADBConnection {
    private String ipAddress;
    private int port;
    AdbBase64 base64;
    AdbCrypto crypto;

    private final String patternMScreenOn = "mScreenOn\\s*=\\s*(\\w+)";
    private final String patternDisplayPower = "Display Power\\s*:\\s*state=\\s*(\\w+)";
    private final Logger logger = LoggerFactory.getLogger(ADBConnection.class);

    public ADBConnection(String ip, int port, String keypath) {
        try {
            this.ipAddress = ip;
            this.port = port;

            base64 = new AdbBase64() {
                @Override
                public String encodeToString(byte[] data) {
                    return DatatypeConverter.printBase64Binary(data);
                }
            };

            File prv = new File(keypath + File.separator + "private_" + ip + ".key");
            File pub = new File(keypath + File.separator + "public_" + ip + ".key");

            if (prv.exists() && pub.exists()) {
                crypto = AdbCrypto.loadAdbKeyPair(base64, prv, pub);
            } else {
                crypto = AdbCrypto.generateAdbKeyPair(base64);
                crypto.saveAdbKeyPair(prv, pub);
            }

            logger.info("Ping: " + ping());

        } catch (Exception ex) {
            logger.error(ex.toString());
        }
    }

    public boolean ping() {
        try {
            String output = shell("echo ping");
            return (output != null && output.trim().contains("ping"));
        } catch (Exception ex) {
            logger.error(ex.toString());
            return false;
        }
    }

    public boolean isDisplayOn() {
        boolean result = false;

        String output = shell("dumpsys power | grep mScreenOn");
        if (output != null && (output.trim().matches(patternMScreenOn)))
            result = output.contains("true");
        else {
            output = shell("dumpsys power | grep 'Display Power'");
            if (output != null && (output.trim().matches(patternDisplayPower)))
                result = output.contains("ON");
        }

        return result;
    }

    public void toggleDisplay() {
        shell("input keyevent 26");
    }

    public void setDisplay(boolean on) {
        boolean isOn = this.isDisplayOn();
        if (on != isOn)
            toggleDisplay();
    }

    public String shell(String command) {

        try {
            synchronized (ADBConnection.class) {
                Callable<String> adbRequest = new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        AdbConnection connection = null;
                        Socket socket = null;
                        AdbStream stream = null;
                        try {
                            socket = new Socket(ADBConnection.this.ipAddress, ADBConnection.this.port);
                            connection = AdbConnection.create(socket, crypto);
                            connection.connect();
                            stream = connection.open("shell:" + command);

                            byte[] b = stream.read();

                            return new String(b);

                        } catch (Exception ex) {
                            logger.error(ex.toString());
                            return null;
                        } finally {
                            stream.close();
                            socket.close();
                            connection.close();
                        }
                    }
                };

                FutureTask<String> request = null;
                ExecutorService executor = null;
                try {
                    request = new FutureTask<>(adbRequest);
                    executor = Executors.newFixedThreadPool(1);
                    executor.execute(request);
                    return request.get(5000, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    logger.error(e.toString());
                    return null;
                } finally {
                    if (request != null && !request.isDone()) {
                        request.cancel(true);
                    }

                    if (executor != null)
                        executor.shutdown();
                }
            }
        } catch (Exception ex) {
            logger.error(ex.toString());
            return null;
        }
    }
}
