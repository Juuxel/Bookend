/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package juuxel.bookend.template;

import io.pebbletemplates.pebble.PebbleEngine;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

public final class TemplateManager {
    private static final String PATH = "templates/";
    private static final String EXTENSION = ".peb.html";

    private final PebbleEngine engine = new PebbleEngine.Builder()
        .build();

    public TemplateManager() {
        engine.getLoader().setPrefix(PATH);
    }

    public String loadTemplate(String name) {
        return loadTemplate(name, Map.of());
    }

    public String loadTemplate(String name, Map<String, Object> context) {
        var template = engine.getTemplate(name + EXTENSION);
        var writer = new StringWriter();

        try {
            template.evaluate(writer, context);
        } catch (IOException e) {
        }

        return writer.toString();
    }
}
