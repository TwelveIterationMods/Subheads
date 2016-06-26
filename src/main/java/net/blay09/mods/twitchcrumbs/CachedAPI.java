package net.blay09.mods.twitchcrumbs;

import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.URL;

public class CachedAPI {

	private static final long DEFAULT_CACHE_TIME = 1000*60*60*24;

	public static InputStream loadCachedAPI(String url, String fileName) throws IOException {
		return loadCachedAPI(url, fileName, DEFAULT_CACHE_TIME);
	}

	public static InputStream loadCachedAPI(String url, String fileName, long maxCacheTime) throws IOException {
		return loadCachedAPI(url, new File(getCacheDirectory(), fileName), maxCacheTime);
	}

	public static InputStream loadCachedAPI(String url, File cacheFile, long maxCacheTime) throws IOException {
		InputStream in = loadLocal(cacheFile, false, maxCacheTime);
		if(in == null) {
			in = loadRemote(url);
			if(in == null) {
				in = loadLocal(cacheFile, true, maxCacheTime);
				if(in == null) {
					throw new IOException("Could not grab remote source and no local cache present.");
				}
			} else {
				try {
					IOUtils.copy(in, new FileOutputStream(cacheFile));
				} catch (IOException e) {
					Twitchcrumbs.logger.error("Failed to cache {}: {}", url, e);
				}
			}
		}
		return in;
	}

	private static InputStream loadLocal(File file, boolean force, long maxCacheTime) throws IOException {
		if(file.exists() && (force || System.currentTimeMillis() - file.lastModified() < maxCacheTime)) {
			return new FileInputStream(file);
		}
		return null;
	}

	private static InputStream loadRemote(String url) throws IOException {
		URL apiURL = new URL(url);
		return apiURL.openStream();
	}

	public static File getCacheDirectory() {
		File file = new File("twitchcrumbs-cache/");
		//noinspection ResultOfMethodCallIgnored
		file.mkdirs();
		return file;
	}

}