/*
 *    Copyright (c) 2018 - 2020, Thales DIS CPL Canada, Inc
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package com.thales.chaos.exception;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.reflections.Reflections;

import java.util.*;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

@RunWith(Enclosed.class)
public class ErrorCodeTest {
    private static <T extends ErrorCode> Collection<ErrorCode> getAllErrorCodes (Class<T> clazz) {
        return Arrays.asList(clazz.getEnumConstants());
    }

    public static class USLocaleOnly {
        private Collection<Collection<ErrorCode>> allErrorCodes = new HashSet<>();

        @Before
        public void setUp () {
            Locale.setDefault(Locale.US);
            allErrorCodes = new Reflections("com.thales.chaos").getSubTypesOf(ErrorCode.class)
                                                               .stream()
                                                               .map(ErrorCodeTest::getAllErrorCodes)
                                                               .peek(errorCodes -> errorCodes.forEach(ErrorCode::clearCachedResourceBundle))
                                                               .collect(Collectors.toList());
        }

        @Test
        public void noDuplicateErrorCodes () {
            Set<Integer> usedErrorCodes = new HashSet<>();
            allErrorCodes.stream().flatMap(Collection::stream).map(ErrorCode::getErrorCode).forEach(integer -> assertTrue("Reused error code " + integer, usedErrorCodes.add(integer)));
        }

        @Test
        public void getFormattedMessage () {
            allErrorCodes.stream()
                         .flatMap(Collection::stream)
                         .peek(errorCode -> assertThat(errorCode.getFormattedMessage(), startsWith(String.valueOf(errorCode
                                 .getErrorCode()))))
                         .forEach(errorCode -> assertThat(errorCode.getFormattedMessage(), endsWith(errorCode.getLocalizedMessage())));
        }

        @Test
        public void errorCodeRangeOverlap () {
            final Map<? extends Class<? extends ErrorCode>, Set<Integer>> moduleIntegerSetMap = allErrorCodes.stream()
                                                                                                             .flatMap(Collection::stream)
                                                                                                             .collect(Collectors
                                                                                                                     .groupingBy(ErrorCode::getClass, Collectors
                                                                                                                             .mapping(ErrorCode::getErrorCode, Collectors
                                                                                                                                     .toSet())));
            final List<Class<? extends ErrorCode>> classes = new ArrayList<>(moduleIntegerSetMap.keySet());
            for (int i = 0; i < classes.size(); i++) {
                final Class<? extends ErrorCode> firstClass = classes.get(i);
                for (int j = i + 1; j < classes.size(); j++) {
                    final Class<? extends ErrorCode> secondClass = classes.get(j);
                    final int firstClassMax = moduleIntegerSetMap.get(firstClass)
                                                                 .stream()
                                                                 .max(Comparator.naturalOrder())
                                                                 .orElse(-1);
                    final int firstClassMin = moduleIntegerSetMap.get(firstClass)
                                                                 .stream()
                                                                 .min(Comparator.naturalOrder())
                                                                 .orElse(-1);
                    final int secondClassMax = moduleIntegerSetMap.get(secondClass)
                                                                  .stream()
                                                                  .max(Comparator.naturalOrder())
                                                                  .orElse(-1);
                    final int secondClassMin = moduleIntegerSetMap.get(secondClass)
                                                                  .stream()
                                                                  .min(Comparator.naturalOrder())
                                                                  .orElse(-1);
                    assertTrue(String.format("Ranges %s-%s (%s) and %s-%s (%s) overlap", firstClassMin, firstClassMax, firstClass, secondClassMin, secondClassMax, secondClass), firstClassMin < secondClassMax && secondClassMin > firstClassMax || secondClassMin < firstClassMax && firstClassMin > secondClassMax);
                }
            }
        }
    }

    @RunWith(Parameterized.class)
    public static class LocaleTests {
        private final Locale locale;
        private static boolean containsUSTranslation = false;
        private Collection<Collection<ErrorCode>> allErrorCodes = new HashSet<>();

        public LocaleTests (Locale locale) {
            this.locale = locale;
        }

        @Parameterized.Parameters(name = "{0}")
        public static Collection<Object[]> getLocales () {
            final List<Object[]> locales = new ArrayList<>();
            locales.add(new Locale[]{ Locale.US });
            for (Locale availableLocale : Locale.getAvailableLocales()) {
                locales.add(new Locale[]{ availableLocale });
            }
            return locales;
        }

        @Before
        public void setUp () {
            Locale.setDefault(locale);
            allErrorCodes = new Reflections("com.thales.chaos").getSubTypesOf(ErrorCode.class)
                                                               .stream()
                                                               .map(ErrorCodeTest::getAllErrorCodes)
                                                               .peek(errorCodes -> errorCodes.forEach(ErrorCode::clearCachedResourceBundle))
                                                               .collect(Collectors.toList());
        }

        @Test
        public void containsTranslation () {
            final boolean checkingUSLocale = Locale.US.equals(locale);
            assumeTrue(checkingUSLocale || containsUSTranslation);
            allErrorCodes.stream().flatMap(Collection::stream).forEach(errorCode -> {
                final String message = errorCode.getMessage();
                final String localizedMessage = errorCode.getLocalizedMessage();
                assertNotEquals(message + " should contain a translation for locale " + locale, message, localizedMessage);
            });
            containsUSTranslation |= checkingUSLocale;
        }
    }
}