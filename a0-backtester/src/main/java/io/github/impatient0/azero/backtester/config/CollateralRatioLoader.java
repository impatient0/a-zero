package io.github.impatient0.azero.backtester.config;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

/**
 * A utility class responsible for loading collateral ratio configurations from a YAML file.
 * <p>
 * This loader is a critical component for the portfolio margin calculation, providing the
 * necessary data to value the equity of the portfolio.
 */
@Slf4j
public final class CollateralRatioLoader {

    private static final String RATIOS_FILE_PATH = "/collateral-ratios.yaml";

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private CollateralRatioLoader() {
    }

    /**
     * Loads and parses the collateral ratios from the {@code collateral-ratios.yaml}
     * file located in the classpath resources.
     *
     * @return An unmodifiable {@link Map} where the key is the asset symbol (e.g., "BTC")
     * and the value is its collateral ratio as a {@link BigDecimal}.
     * @throws IllegalStateException if the configuration file cannot be found or parsed,
     *                               as this is considered a fatal, unrecoverable error for the
     *                               backtesting engine.
     */
    public static Map<String, BigDecimal> load() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        TypeReference<Map<String, BigDecimal>> typeRef = new TypeReference<>() {};

        try (InputStream inputStream = CollateralRatioLoader.class.getResourceAsStream(RATIOS_FILE_PATH)) {
            if (inputStream == null) {
                log.error("FATAL: Collateral configuration file not found at classpath:{}", RATIOS_FILE_PATH);
                throw new IllegalStateException("Could not find collateral-ratios.yaml. This file is mandatory for portfolio margin calculations.");
            }

            Map<String, BigDecimal> ratios = mapper.readValue(inputStream, typeRef);
            log.info("Successfully loaded {} collateral ratio entries.", ratios.size());
            return Collections.unmodifiableMap(ratios);

        } catch (Exception e) {
            log.error("FATAL: Failed to parse collateral configuration file at classpath:{}", RATIOS_FILE_PATH, e);
            throw new IllegalStateException("Failed to parse collateral-ratios.yaml.", e);
        }
    }
}