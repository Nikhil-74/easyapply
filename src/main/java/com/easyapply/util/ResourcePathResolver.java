package com.easyapply.util;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class ResourcePathResolver {

	private final ResourceLoader resourceLoader;

	public ResourcePathResolver(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	public Resource loadResource(String location) {
		return resourceLoader.getResource(location);
	}

	public String readResourceAsString(String location) throws IOException {
		Resource resource = loadResource(location);
		return resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
	}

	public Path resolveExternalPath(String path) {
		Path resolved = Paths.get(path);
		if (resolved.isAbsolute()) {
			return resolved;
		}
		return Paths.get(System.getProperty("user.dir")).resolve(resolved);
	}
}
