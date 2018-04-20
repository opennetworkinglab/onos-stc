/*
 * Copyright 2015-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onlab.stc;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.io.InputStream;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Representation of a re-usable test scenario.
 */
public final class Scenario {

    private static final String SCENARIO = "scenario";
    private static final String NAME = "[@name]";
    private static final String DESCRIPTION = "[@description]";

    private final String name;
    private final String description;
    private final HierarchicalConfiguration definition;

    // Creates a new scenario from the specified definition.
    private Scenario(String name, String description, HierarchicalConfiguration definition) {
        this.name = checkNotNull(name, "Name cannot be null");
        this.description = checkNotNull(description, "Description cannot be null");
        this.definition = checkNotNull(definition, "Definition cannot be null");
    }

    /**
     * Loads a new scenario from the specified hierarchical configuration.
     *
     * @param definition scenario definition
     * @return loaded scenario
     */
    public static Scenario loadScenario(HierarchicalConfiguration definition) {
        String name = definition.getString(NAME);
        String description = definition.getString(DESCRIPTION, "");
        checkState(name != null, "Scenario name must be specified");
        return new Scenario(name, description, definition);
    }

    private static DocumentBuilder validationBuilder() throws ParserConfigurationException, SAXException {
        Schema schema = SchemaFactory
                .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new StreamSource(Scenario.class.getClassLoader().getResourceAsStream("stc.xsd")));
        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        docBuilderFactory.setSchema(schema);
        DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();

        docBuilder.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void error(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void fatalError(SAXParseException exception)  throws SAXException {
                throw exception;
            }
        });
        return docBuilder;
    }

    /**
     * Loads a new scenario from the specified input stream.
     *
     * @param stream scenario definition stream
     * @return loaded scenario
     */
    public static Scenario loadScenario(InputStream stream) {
        XMLConfiguration cfg = new XMLConfiguration();
        cfg.setAttributeSplittingDisabled(true);
        cfg.setDelimiterParsingDisabled(true);
        cfg.setRootElementName(SCENARIO);
        try {
            cfg.setDocumentBuilder(validationBuilder());
            cfg.load(stream);
            return loadScenario(cfg);
        } catch (ConfigurationException | ParserConfigurationException | SAXException e) {
            throw new IllegalArgumentException("Unable to load scenario from the stream", e);
        }
    }

    /**
     * Returns the scenario name.
     *
     * @return scenario name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the scenario description.
     *
     * @return scenario description
     */
    public String description() {
        return description;
    }

    /**
     * Returns the scenario definition.
     *
     * @return scenario definition
     */
    public HierarchicalConfiguration definition() {
        return definition;
    }

}
