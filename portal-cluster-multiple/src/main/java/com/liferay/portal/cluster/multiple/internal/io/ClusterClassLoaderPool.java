/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portal.cluster.multiple.internal.io;

import com.liferay.petra.lang.ClassLoaderPool;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.osgi.framework.Version;

/**
 * @author Lance Ji
 */
public class ClusterClassLoaderPool {

	public static ClassLoader getClassLoader(String contextName) {
		ClassLoader classLoader = null;

		Thread currentThread = Thread.currentThread();

		ClassLoader contextClassLoader = currentThread.getContextClassLoader();

		if ((contextName != null) && !contextName.equals("null")) {
			try {
				currentThread.setContextClassLoader(null);

				classLoader = ClassLoaderPool.getClassLoader(contextName);
			}
			finally {
				currentThread.setContextClassLoader(contextClassLoader);
			}

			if (classLoader == null) {
				int pos = contextName.indexOf(StringPool.UNDERLINE);

				if (pos > 0) {
					String symbolicName = contextName.substring(0, pos);

					List<VersionedClassLoader> versionedClassLoaderList =
						_fallbackClassLoaders.get(symbolicName);

					if (versionedClassLoaderList != null) {
						VersionedClassLoader latestVersionClassLoader =
							versionedClassLoaderList.get(0);

						classLoader = latestVersionClassLoader.getClassLoader();

						if (_log.isWarnEnabled()) {
							Version version =
								latestVersionClassLoader.getVersion();

							_log.warn(
								StringBundler.concat(
									"Unable to find ClassLoader for ",
									contextName, ", ClassLoader ", symbolicName,
									StringPool.UNDERLINE, version.toString(),
									" is provided instead"));
						}
					}
				}
			}

			if ((classLoader == null) && _log.isDebugEnabled()) {
				_log.debug(
					StringBundler.concat(
						"Unable to find ClassLoader for ", contextName,
						", fall back to current thread's context classLoader"));
			}
		}

		if (classLoader == null) {
			classLoader = contextClassLoader;
		}

		return classLoader;
	}

	public static String getContextName(ClassLoader classLoader) {
		String contextName = ClassLoaderPool.getContextName(classLoader);

		if ((classLoader != null) && contextName.equals("null")) {
			if (_log.isDebugEnabled()) {
				_log.debug(
					StringBundler.concat(
						"Unable to find contextName for ",
						classLoader.toString(),
						", send 'null' as contextName instead"));
			}
		}

		return contextName;
	}

	public static void registerFallback(
		String symbolicName, Version version, ClassLoader classLoader) {

		VersionedClassLoader versionedClassLoader = new VersionedClassLoader(
			classLoader, version);

		List<VersionedClassLoader> versionedClassLoaderList =
			_fallbackClassLoaders.get(symbolicName);

		if (versionedClassLoaderList == null) {
			versionedClassLoaderList = new CopyOnWriteArrayList<>();

			_fallbackClassLoaders.put(symbolicName, versionedClassLoaderList);
		}

		versionedClassLoaderList.add(versionedClassLoader);

		Collections.sort(versionedClassLoaderList, Collections.reverseOrder());
	}

	public static void unregisterFallback(
		String symbolicName, Version version) {

		List<VersionedClassLoader> versionedClassLoaderList =
			_fallbackClassLoaders.get(symbolicName);

		if (versionedClassLoaderList == null) {
			return;
		}

		for (VersionedClassLoader versionedClassLoader :
				versionedClassLoaderList) {

			if (version.equals(versionedClassLoader.getVersion())) {
				versionedClassLoaderList.remove(versionedClassLoader);

				if (versionedClassLoaderList.isEmpty()) {
					_fallbackClassLoaders.remove(symbolicName);
				}
			}
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		ClusterClassLoaderPool.class);

	private static final Map<String, List<VersionedClassLoader>>
		_fallbackClassLoaders = new ConcurrentHashMap<>();

	private static class VersionedClassLoader
		implements Comparable<VersionedClassLoader> {

		@Override
		public int compareTo(VersionedClassLoader versionedClassLoader) {
			return _version.compareTo(versionedClassLoader._version);
		}

		public ClassLoader getClassLoader() {
			return _classLoader;
		}

		public Version getVersion() {
			return _version;
		}

		private VersionedClassLoader(ClassLoader classLoader, Version version) {
			_classLoader = classLoader;
			_version = version;
		}

		private final ClassLoader _classLoader;
		private final Version _version;

	}

}