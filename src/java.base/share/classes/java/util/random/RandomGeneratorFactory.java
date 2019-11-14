/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.util.random;

import java.util.function.Function;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * This is a factory class for generating random number generators of a specific
 * catagory and algorithm.
 */
public class RandomGeneratorFactory<T> {
    /**
     * Instance provider class of random number algorithm.
     */
    private final Provider<T> provider;

    /**
     * Map of provider classes.
     */
    private static Map<String, Provider<RandomGenerator>> providerMap;

    /**
     * Private constructor.
     *
     * @param provider  Provider class to wrap.
     */
    private RandomGeneratorFactory(Provider<T> provider) {
        this.provider = provider;
    }

    /**
     * Returns the provider map, lazily constructing map on first call.
     *
     * @return Map of provider classes.
     */
    private static Map<String, Provider<RandomGenerator>> getProviderMap() {
        if (providerMap == null) {
            synchronized (RandomGeneratorFactory.class) {
                if (providerMap == null) {
                    providerMap =
                        ServiceLoader
                            .load(RandomGenerator.class)
                            .stream()
                            .filter(p -> !p.type().isInterface())
                            .collect(Collectors.toMap(p -> p.type().getSimpleName().toUpperCase(),
                                    Function.identity()));
                }
            }
        }
        return providerMap;
    }

    private static Provider<RandomGenerator> findProvider(String name, Class<? extends RandomGenerator> category)
            throws IllegalArgumentException {
        Map<String, Provider<RandomGenerator>> pm = getProviderMap();
        Provider<RandomGenerator> provider = pm.get(name.toUpperCase());
        if (provider == null || provider.type().isAssignableFrom(category)) {
            throw new IllegalArgumentException(name + " is an unknown random number generator");
        }
        return provider;
    }

    /**
     * Returns a {@link RandomGenerator} that utilizes the {@code name} algorithm.
     *
     * @param name  Name of random number algorithm to use
     * @param category Sub-interface of {@link RandomGenerator} to type check
     * @param <T> Sub-interface of {@link RandomGenerator} to produce
     * @return An instance of {@link RandomGenerator}
     * @throws IllegalArgumentException when either the name or category is null
     */
    static <T> T of(String name, Class<? extends RandomGenerator> category)
            throws IllegalArgumentException {
        @SuppressWarnings("unchecked")
        T uncheckedRandomGenerator = (T)findProvider(name, category).get();
        return uncheckedRandomGenerator;
    }

    /**
     * Returns a {@link RandomGeneratorFactory} that will produce instances
     * of {@link RandomGenerator} that utilizes the {@code name} algorithm.
     *
     * @param name  Name of random number algorithm to use
     * @param category Sub-interface of {@link RandomGenerator} to type check
     * @param <T> Sub-interface of {@link RandomGenerator} to produce
     * @return Factory of {@link RandomGenerator}
     * @throws IllegalArgumentException when either the name or category is null
     */
    static <T> RandomGeneratorFactory<T> factoryOf(String name, Class<? extends RandomGenerator> category)
            throws IllegalArgumentException {
        @SuppressWarnings("unchecked")
        Provider<T> uncheckedProvider = (Provider<T>)findProvider(name, category);
        return new RandomGeneratorFactory<T>(uncheckedProvider);
    }

    /**
     * Create an instance of {@link RandomGenerator}.
     *
     * @return Instance of {@link RandomGenerator}.
     */
    public T create() {
        return provider.get();
    }
}


