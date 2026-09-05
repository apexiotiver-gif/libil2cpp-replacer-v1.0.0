package mts.com.proxy;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class libil2cpp_replacer {

    private static final String PACKAGE = "com.dts.freefiremax";
    private static final String RESOURCE_NAME = "libil2cpp.so";

    /**
     * Extract the bundled libil2cpp.so from the JAR to cache dir.
     * This avoids needing the file in Download folder.
     */
    private static File extractBundledLib(Context context) {
        try {
            InputStream is = libil2cpp_replacer.class.getClassLoader()
                    .getResourceAsStream(RESOURCE_NAME);
            if (is == null) {
                Log.e("LibReplacer", "Bundled " + RESOURCE_NAME + " not found in JAR!");
                return null;
            }

            File outFile = new File(context.getCacheDir(), "bundled_libil2cpp.so");
            FileOutputStream fos = new FileOutputStream(outFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            fos.close();
            is.close();

            // Make it readable for root copy
            execRoot("chmod 666 '" + outFile.getAbsolutePath() + "'");

            Log.i("LibReplacer", "Bundled .so extracted: " + outFile.length() + " bytes");
            return outFile;
        } catch (Exception e) {
            Log.e("LibReplacer", "extractBundledLib: " + e.getMessage());
            return null;
        }
    }

    /**
     * Switch ON: Replace libil2cpp.so
     */
    public static void replaceLib(Context context) {
        try {
            // Extract bundled .so from JAR resource
            File modLib = extractBundledLib(context);
            if (modLib == null || !modLib.exists() || modLib.length() == 0) {
                Log.e("LibReplacer", "ERROR: Could not extract bundled libil2cpp.so from JAR!");
                return;
            }
            Log.i("LibReplacer", "Modified .so: " + modLib.length() + " bytes");

            // Find APK path
            String apkPath = findApkPath();
            if (apkPath == null) {
                Log.e("LibReplacer", "APK not found!");
                return;
            }
            Log.i("LibReplacer", "APK: " + apkPath);

            // Step 1: Copy APK to cache dir using ROOT (Java can't read /data/app/ directly)
            File cacheDir = context.getCacheDir();
            File tempApk = new File(cacheDir, "temp_orig.apk");
            File newApk = new File(cacheDir, "temp_new.apk");

            // Use su to copy APK to cache, make it readable
            String cacheApkPath = tempApk.getAbsolutePath();
            execRoot("cat '" + apkPath + "' > '" + cacheApkPath + "'");
            execRoot("chmod 666 '" + cacheApkPath + "'");

            if (!tempApk.exists() || tempApk.length() == 0) {
                Log.e("LibReplacer", "Failed to copy APK! Check root access.");
                return;
            }
            Log.i("LibReplacer", "APK copied: " + tempApk.length() + " bytes");

            // Step 2: Replace libil2cpp.so inside APK (in cache dir, no root needed)
            replaceInZip(tempApk, newApk, "lib/arm64-v8a/libil2cpp.so", modLib);
            Log.i("LibReplacer", "New APK: " + newApk.length() + " bytes");

            // Make new APK readable for root copy
            execRoot("chmod 666 '" + newApk.getAbsolutePath() + "'");

            // Step 3: Backup original + install modified using ROOT
            // Backup
            execRoot("cp '" + apkPath + "' '" + apkPath + ".bak'");
            Log.i("LibReplacer", "Backup created");

            // Get original owner and SELinux context
            String owner = execRoot("stat -c '%U:%G' '" + apkPath + "' 2>/dev/null");
            if (owner == null || owner.trim().isEmpty()) {
                owner = "system:system";
            }
            owner = owner.trim();
            Log.i("LibReplacer", "Owner: " + owner);

            // Copy modified APK to /data/app/ using ROOT
            execRoot("cat '" + newApk.getAbsolutePath() + "' > '" + apkPath + "'");

            // Set permissions using ROOT (not Java!)
            execRoot("chmod 644 '" + apkPath + "'");
            execRoot("chown " + owner + " '" + apkPath + "'");

            // Restore SELinux context (Android 15 critical!)
            execRoot("restorecon '" + apkPath + "'");

            // Cleanup
            tempApk.delete();
            newApk.delete();
            modLib.delete();

            Log.i("LibReplacer", "SUCCESS: libil2cpp.so replaced!");
            Log.i("LibReplacer", "Backup: " + apkPath + ".bak");

        } catch (Exception e) {
            Log.e("LibReplacer", "Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Switch OFF: Restore original from backup
     */
    public static void restoreLib(Context context) {
        try {
            String apkPath = findApkPath();
            if (apkPath == null) {
                Log.e("LibReplacer", "APK not found!");
                return;
            }

            String backupPath = apkPath + ".bak";

            // Check backup
            String check = execRoot("test -f '" + backupPath + "' && echo YES");
            if (check == null || !check.contains("YES")) {
                Log.e("LibReplacer", "No backup found!");
                return;
            }

            // Get original owner
            String owner = execRoot("stat -c '%U:%G' '" + backupPath + "' 2>/dev/null");
            if (owner == null || owner.trim().isEmpty()) {
                owner = "system:system";
            }
            owner = owner.trim();

            // Restore using ROOT
            execRoot("cat '" + backupPath + "' > '" + apkPath + "'");
            execRoot("chmod 644 '" + apkPath + "'");
            execRoot("chown " + owner + " '" + apkPath + "'");
            execRoot("restorecon '" + apkPath + "'");

            Log.i("LibReplacer", "SUCCESS: Original restored!");

        } catch (Exception e) {
            Log.e("LibReplacer", "Error: " + e.getMessage());
        }
    }

    /**
     * Find APK path - use pm command (Android 15)
     */
    private static String findApkPath() {
        try {
            // pm path gives exact path on Android 15
            String result = execRoot("pm path " + PACKAGE);
            if (result != null) {
                for (String line : result.split("\n")) {
                    line = line.trim();
                    if (line.contains("split_config.arm64_v8a.apk")) {
                        return line.replace("package:", "").trim();
                    }
                }
            }

            // Fallback: find command
            result = execRoot("find /data/app -name 'split_config.arm64_v8a.apk' 2>/dev/null | head -1");
            if (result != null && result.trim().length() > 0) {
                return result.trim();
            }
        } catch (Exception e) {
            Log.e("LibReplacer", "findApkPath: " + e.getMessage());
        }
        return null;
    }

    /**
     * Execute as root (su) - all shell commands properly quoted
     */
    private static String execRoot(String command) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            process.waitFor();
            reader.close();
            return output.toString();
        } catch (Exception e) {
            Log.e("LibReplacer", "su: " + e.getMessage());
            return null;
        }
    }

    /**
     * Replace file inside ZIP (APK)
     */
    private static void replaceInZip(File originalApk, File outputApk, String entryName, File replacementFile) throws Exception {
        byte[] buffer = new byte[8192];
        byte[] replacementData = readFile(replacementFile);

        ZipInputStream zis = new ZipInputStream(new FileInputStream(originalApk));
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputApk));

        ZipEntry entry;
        boolean replaced = false;

        while ((entry = zis.getNextEntry()) != null) {
            String name = entry.getName();

            if (name.equals(entryName)) {
                ZipEntry newEntry = new ZipEntry(entryName);
                newEntry.setMethod(ZipEntry.STORED);
                newEntry.setSize(replacementData.length);
                newEntry.setCompressedSize(replacementData.length);
                newEntry.setCrc(computeCRC(replacementData));
                zos.putNextEntry(newEntry);
                zos.write(replacementData);
                zos.closeEntry();
                replaced = true;
                Log.i("LibReplacer", "Replaced: " + entryName);
            } else {
                ZipEntry newEntry = new ZipEntry(name);
                if (entry.getMethod() == ZipEntry.STORED) {
                    newEntry.setMethod(ZipEntry.STORED);
                    newEntry.setSize(entry.getSize());
                    newEntry.setCompressedSize(entry.getCompressedSize());
                    newEntry.setCrc(entry.getCrc());
                }
                zos.putNextEntry(newEntry);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
                zos.closeEntry();
            }
        }

        zis.close();
        zos.close();

        if (!replaced) {
            Log.e("LibReplacer", "libil2cpp.so not found in APK!");
        }
    }

    private static long computeCRC(byte[] data) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(data);
        return crc.getValue();
    }

    private static byte[] readFile(File file) throws Exception {
        byte[] data = new byte[(int) file.length()];
        FileInputStream fis = new FileInputStream(file);
        fis.read(data);
        fis.close();
        return data;
    }
}