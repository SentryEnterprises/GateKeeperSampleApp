package co.blustor.gatekeeper.biometrics;

import com.neurotec.licensing.NLicense;

import java.io.IOException;

public class Licensing {
    public static final String[] LICENSES = {
            "Biometrics.FaceExtraction",
            "Biometrics.FaceDetection",
            "Devices.Cameras",
            "Biometrics.FaceMatching",
            "Biometrics.FaceMatchingFast"
    };

    private final String sHostAddress;
    private final int sHostPort;

    public Licensing(String hostAddress, int hostPort) {
        sHostAddress = hostAddress;
        sHostPort = hostPort;
    }

    public void obtainLicenses() {
        for (String component : LICENSES) {
            try {
                NLicense.obtainComponents(sHostAddress, sHostPort, component);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException("licenses were not obtained");
            }
        }
    }

    public void releaseLicenses() {
        for (String component : LICENSES) {
            try {
                NLicense.releaseComponents(component);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException("licenses were not released");
            }
        }
    }
}
