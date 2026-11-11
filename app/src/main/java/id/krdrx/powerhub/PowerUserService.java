package id.krdrx.powerhub;

import android.os.RemoteException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class PowerUserService extends IUserService.Stub {
    public PowerUserService() {}

    @Override
    public String exec(String command) throws RemoteException {
        Process process = null;
        try {
            process = new ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() < 3000) output.append(line).append('\n');
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return output.length() == 0 ? "Perintah berhasil." : output.toString();
            }
            return "Perintah gagal (kode " + exitCode + ").\n" + output;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "Perintah terputus.";
        } catch (Exception exception) {
            return "Kesalahan: " + exception.getMessage();
        } finally {
            if (process != null) process.destroy();
        }
    }
}
