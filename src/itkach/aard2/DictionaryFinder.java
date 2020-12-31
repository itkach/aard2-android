package itkach.aard2;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import android.os.Environment;

public class DictionaryFinder {

    public static List<String> cardDirectories() {

        final List<String> dirNames = new LinkedList<String>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader("/proc/self/mounts"));
            String line;
            while ((line = reader.readLine()) != null) {
                final String[] parts = line.split("\\s+");
                if (parts.length >= 4 &&
                        parts[3].indexOf("rw") >= 0) {
                    final File fsDir = new File(parts[1]);
                    final String dirName = fsDir.getPath();
                    final String dirNameLower = dirName.toLowerCase();
                    if (fsDir.isDirectory() && fsDir.canRead() && (dirNameLower.contains("storage")
                            || dirNameLower.contains("media")) ) {
                        dirNames.add(dirName);
                    }
                }
            }
        } catch (Throwable e) {
        } finally {
            try {
                reader.close();
            } catch (Throwable t) {
            }
        }

        if (dirNames.size() == 0) {
            dirNames.add(Environment.getExternalStorageDirectory().getPath());
        }
        return dirNames;
    }


    private static void maybeAddDir(List<File> dirs, String path) {
        for (File d : dirs) {
            if (d.getPath().equals(path)) {
                return;
            }
        }
        File file = new File(path);
        if (file.isDirectory() && file.exists() && file.canRead()) {
            dirs.add(file);
        }
    }

    public static File[] getFallbackRootLs() {
        List<File> dirs = new LinkedList<File>();
        maybeAddDir(dirs, "/sdcard");
        maybeAddDir(dirs, "/mnt");
        maybeAddDir(dirs, "/storage");
        for (String path : cardDirectories()) {
            maybeAddDir(dirs, path);
        }
        return dirs.toArray(new File[dirs.size()]);
    }

    private final static String T = "DictionaryFinder";

    private Set<String>         excludedScanDirs   = new HashSet<String>() {
        {
            add("/proc");
            add("/dev");
            add("/etc");
            add("/sys");
            add("/acct");
            add("/cache");
        }
    };

    private boolean cancelRequested;

    private FilenameFilter fileFilter = new FilenameFilter() {
        public boolean accept(File dir, String filename) {
            return filename.toLowerCase().endsWith(
                    ".slob") || new File(dir, filename).isDirectory();
        }
    };

    private List<File> discover() {
        File scanRoot = new File("/");
        File[] files = scanRoot.listFiles(fileFilter);
        List<File> result = new ArrayList<File>();

        if (files == null || files.length == 0) {
            files = getFallbackRootLs();
        }
        for (File f : files) {
            result.addAll(scanDir(f));
        }
        return result;
    }

    private List<File> scanDir(File dir) {
        if (cancelRequested) {
            return Collections.emptyList();
        }
        String absolutePath = dir.getAbsolutePath();
        if (excludedScanDirs.contains(absolutePath)) {
            Log.d(T, String.format("%s is excluded", absolutePath));
            return Collections.emptyList();
        }

        if (dir.isHidden()) {
            Log.d(T, String.format("%s is hidden", absolutePath));
            return Collections.emptyList();
        }
        Log.d(T, "Scanning " + absolutePath);
        List<File> candidates = new ArrayList<File>();
        File[] files = dir.listFiles(fileFilter);
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                if (cancelRequested) {
                    break;
                }
                File file = files[i];
                Log.d(T, "Considering " + file.getAbsolutePath());
                if (file.isDirectory()) {
                    Log.d(T, "Directory: " + file.getAbsolutePath());
                    candidates.addAll(scanDir(file));
                } else {
                    if (!file.isHidden() && file.isFile()) {
                        Log.d(T, "Candidate: " + file.getAbsolutePath());
                        candidates.add(file);
                    }
                    else {
                        Log.d(T, "Hidden or not a file: " + file.getAbsolutePath());
                    }
                }
            }
        }
        return candidates;
    }

    synchronized List<SlobDescriptor> findDictionaries() {
        cancelRequested = false;
        Log.d(T, "starting dictionary discovery");
        long t0 = System.currentTimeMillis();
        List<File> candidates = discover();
        Log.d(T, "dictionary discovery took " + (System.currentTimeMillis() - t0));
        List<SlobDescriptor> descriptors = new ArrayList<SlobDescriptor>();
        Set<String> seen = new HashSet<String>();
        for (File f : candidates) {
            SlobDescriptor sd = SlobDescriptor.fromFile(f);
            if (sd.id != null && seen.contains(sd.id)) {
                continue;
            }
            seen.add(sd.id);
            long currentTime = System.currentTimeMillis();
            sd.createdAt = currentTime;
            sd.lastAccess = currentTime;
            descriptors.add(sd);
        }
        return descriptors;
    }

    public void cancel() {
        cancelRequested = true;
    }
}
