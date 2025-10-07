package com.uae.anpr.service;

import com.uae.anpr.config.AnprProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
public class ResourceScanner {

    private static final Logger log = LoggerFactory.getLogger(ResourceScanner.class);

    private final AnprProperties properties;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public ResourceScanner(AnprProperties properties) {
        this.properties = properties;
    }

    public List<Path> loadResourceTree() {
        List<Path> resourcePaths = new ArrayList<>();
        collectPaths("classpath:" + properties.resources().alphabets(), resourcePaths);
        collectPaths("classpath:" + properties.resources().neuralNetworks(), resourcePaths);
        collectPaths("classpath:" + properties.resources().configs(), resourcePaths);
        if (properties.resources().enhancementKernels() != null) {
            properties.resources().enhancementKernels().forEach(pattern ->
                    collectPaths("classpath:" + pattern, resourcePaths));
        }
        log.info("Discovered {} resource artifacts for the ANPR pipeline", resourcePaths.size());
        return resourcePaths;
    }

    private void collectPaths(String pattern, List<Path> accumulator) {
        try {
            Resource[] resources = resolver.getResources(pattern);
            for (Resource resource : resources) {
                if (resource.exists() && resource.isReadable()) {
                    try {
                        Path tempFile = Files.createTempFile("anpr-resource-", resource.getFilename());
                        tempFile.toFile().deleteOnExit();
                        Files.copy(resource.getInputStream(), tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        accumulator.add(tempFile);
                    } catch (IOException ex) {
                        log.warn("Failed to cache resource {}: {}", resource.getFilename(), ex.getMessage());
                    }
                }
            }
        } catch (IOException ex) {
            log.warn("Unable to resolve resources for pattern {}: {}", pattern, ex.getMessage());
        }
    }

    public String describeResources() {
        return loadResourceTree().stream()
                .map(Path::toString)
                .collect(Collectors.joining("\n"));
    }
}
