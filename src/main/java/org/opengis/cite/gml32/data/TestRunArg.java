package org.opengis.cite.gml32.data;

/**
 * An enumerated type defining all recognized test run arguments.
 */
public enum TestRunArg {

    /**
     * An absolute URI that refers to a representation of the test subject or
     * metadata about it.
     */
    IUT,
    /**
     * A URI that refers to a Schematron schema (ISO 19757-3) that defines
     * supplementary data constraints.
     */
    SCH;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
