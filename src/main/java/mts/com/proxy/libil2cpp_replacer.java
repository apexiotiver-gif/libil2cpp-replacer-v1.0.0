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

            execRoot("chmod 666 '" + outFile.getAbsolutePath() + "'");

            Log.i("LibReplacer", "Bundled .so extracted: " + outFile.length() + " bytes");
            return outFile;
        } catch (Exception e) {
            Log.e("LibReplacer", "extractBundledLib: " + e.getMessage());
            return null;
        }
    }

    public static void replaceLib(Context context) {
        try {
            File modLib = extractBundledLib(context);
            if (modLib == null || !modLib.exists() || modLib.length() == 0) {
                Log.e("LibReplacer", "ERROR: Could not extract bundled libil2cpp.so from JAR!");
                return;
            }
            Log.i("LibReplacer", "Modified .so: " + modLib.length() + " bytes");

            String apkPath = findApkPath();
            if (apkPath == null) {
                Log.e("LibReplacer", "APK not found!");
                return;
            }
            Log.i("LibReplacer", "APK: " + apkPath);

            File cacheDir = context.getCacheDir();
            File tempApk = new File(cacheDir, "temp_orig.apk");
            File newApk = new File(cacheDir, "temp_new.apk");

            String cacheApkPath = tempApk.getAbsolutePath();
            execRoot("cat '" + apkPath + "' > '" + cacheApkPath + "'");
            execRoot("chmod 666 '" + cacheApkPath + "'");

            if (!tempApk.exists() || tempApk.length() == 0) {
                Log.e("LibReplacer", "Failed to copy APK! Check root access.");
                return;
            }
            Log.i("LibReplacer", "APK copied: " + tempApk.length() + " bytes");

            replaceInZip(tempApk, newApk, "lib/arm64-v8a/libil2cpp.so", modLib);
            Log.i("LibReplacer", "New APK: " + newApk.length() + " bytes");

            execRoot("chmod 666 '" + newApk.getAbsolutePath() + "'");

            execRoot("cp '" + apkPath + "' '" + apkPath + ".bak'");
            Log.i("LibReplacer", "Backup created");

            String owner = execRoot("stat -c '%U:%G' '" + apkPath + "' 2>/dev/null");
            if (owner == null || owner.trim().isEmpty()) {
                owner = "system:system";
            }
            owner = owner.trim();
            Log.i("LibReplacer", "Owner: " + owner);

            execRoot("cat '" + newApk.getAbsolutePath() + "' > '" + apkPath + "'");

            execRoot("chmod 644 '" + apkPath + "'");
            execRoot("chown " + owner + " '" + apkPath + "'");

            execRoot("restorecon '" + apkPath + "'");

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

    public static void restoreLib(Context context) {
        try {
            String apkPath = findApkPath();
            if (apkPath == null) {
                Log.e("LibReplacer", "APK not found!");
                return;
            }

            String backupPath = apkPath + ".bak";

            String check = execRoot("test -f '" + backupPath + "' && echo YES");
            if (check == null || !check.contains("YES")) {
                Log.e("LibReplacer", "No backup found!");
                return;
            }

            String owner = execRoot("stat -c '%U:%G' '" + backupPath + "' 2>/dev/null");
            if (owner == null || owner.trim().isEmpty()) {
                owner = "system:system";
            }
            owner = owner.trim();

            execRoot("cat '" + backupPath + "' > '" + apkPath + "'");
            execRoot("chmod 644 '" + apkPath + "'");
            execRoot("chown " + owner + " '" + apkPath + "'");
            execRoot("restorecon '" + apkPath + "'");

            Log.i("LibReplacer", "SUCCESS: Original restored!");

        } catch (Exception e) {
            Log.e("LibReplacer", "Error: " + e.getMessage());
        }
    }

    private static String findApkPath() {
        try {
            String result = execRoot("pm path " + PACKAGE);
            if (result != null) {
                for (String line : result.split("\n")) {
                    line = line.trim();
                    if (line.contains("split_config.arm64_v8a.apk")) {
                        return line.replace("package:", "").trim();
                    }
                }
            }

            result = execRoot("find /data/app -name 'split_config.arm64_v8a.apk' 2>/dev/null | head -1");
            if (result != null && result.trim().length() > 0) {
                return result.trim();
            }
        } catch (Exception e) {
            Log.e("LibReplacer", "findApkPath: " + e.getMessage());
        }
        return null;
    }

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
