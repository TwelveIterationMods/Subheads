package net.blay09.mods.twitchcrumbs;

import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.URL;

public class CachedAPI {

	private static final long DEFAULT_CACHE_TIME = 1000*60*60*24;

	public static InputStream loadCachedAPI(String url, String fileName) {
		return loadCachedAPI(url, fileName, DEFAULT_CACHE_TIME);
	}

	public static InputStream loadCachedAPI(String url, String fileName, long maxCacheTime) {
		return loadCachedAPI(url, new File(getCacheDirectory(), fileName), maxCacheTime);
	}

	public static InputStream loadCachedAPI(String url, File cacheFile, long maxCacheTime) {
		InputStream in = loadLocal(cacheFile, false, maxCacheTime);
		if(in == null) {
			in = loadRemote(url);
			if(in == null) {
				in = loadLocal(cacheFile, true, maxCacheTime);
			} else {
				try {
					IOUtils.copy(in, new FileOutputStream(cacheFile));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return in;
	}

	private static InputStream loadLocal(File file, boolean force, long maxCacheTime) {
		if(file.exists() && (force || file.lastModified() - System.currentTimeMillis() < maxCacheTime)) {
			try {
				return new FileInputStream(file);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	private static InputStream loadRemote(String url) {
		try {
			URL apiURL = new URL(url);
			return apiURL.openStream();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static File getCacheDirectory() {
		File file = new File("twitchcrumbs-cache/");
		//noinspection ResultOfMethodCallIgnored
		file.mkdirs();
		return file;
	}

}