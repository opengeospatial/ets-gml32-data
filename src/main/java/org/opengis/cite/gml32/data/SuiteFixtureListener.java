package org.opengis.cite.gml32.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import org.opengis.cite.gml32.data.util.ClientUtils;
import org.opengis.cite.gml32.data.util.TestSuiteLogger;
import org.opengis.cite.gml32.data.util.URIUtils;
import org.testng.ISuite;
import org.testng.ISuiteListener;

import com.sun.jersey.api.client.Client;

/**
 * A listener that performs various tasks before and after a test suite is run,
 * usually concerned with maintaining a shared test suite fixture. Since this
 * listener is loaded using the ServiceLoader mechanism, its methods will be
 * called before those of other suite listeners listed in the test suite
 * definition and before any annotated configuration methods.
 *
 * Attributes set on an ISuite instance are not inherited by constituent test
 * group contexts (ITestContext). However, suite attributes are still accessible
 * from lower contexts.
 *
 * @see org.testng.ISuite ISuite interface
 */
public class SuiteFixtureListener implements ISuiteListener {

    @Override
    public void onStart(ISuite suite) {
        processSuiteParameters(suite);
        getSchematronSchema(suite);
        registerClientComponent(suite);
    }

    @Override
    public void onFinish(ISuite suite) {
        deleteTempFiles(suite);
    }

    /**
     * Processes test suite arguments and sets suite attributes accordingly. The
     * entity referenced by the {@link TestRunArg#IUT iut} argument is retrieved
     * and written to a File that is set as the value of the suite attribute
     * {@link SuiteAttribute#TEST_SUBJ_FILE testSubjectFile}.
     * 
     * @param suite
     *            An ISuite object representing a TestNG test suite.
     */
    void processSuiteParameters(ISuite suite) {
        Map<String, String> params = suite.getXmlSuite().getParameters();
        TestSuiteLogger.log(Level.CONFIG, "Suite parameters\n" + params.toString());
        String iutParam = params.get(TestRunArg.IUT.toString());
        if ((null == iutParam) || iutParam.isEmpty()) {
            throw new IllegalArgumentException("Required test run parameter not found: " + TestRunArg.IUT.toString());
        }
        URI iutRef = URI.create(iutParam.trim());
        suite.setAttribute(SuiteAttribute.TEST_SUBJ_URI.getName(), iutRef);
        File entityFile = null;
        try {
            entityFile = URIUtils.dereferenceURI(iutRef);
        } catch (IOException iox) {
            throw new RuntimeException("Failed to dereference resource located at " + iutRef, iox);
        }
        TestSuiteLogger.log(Level.FINE, String.format("Wrote test subject to file: %s (%d bytes)",
                entityFile.getAbsolutePath(), entityFile.length()));
        suite.setAttribute(SuiteAttribute.TEST_SUBJ_FILE.getName(), entityFile);
        if (TestSuiteLogger.isLoggable(Level.FINE)) {
            StringBuilder logMsg = new StringBuilder("Parsed resource retrieved from ");
            logMsg.append(iutRef).append("\n");
            logMsg.append(entityFile.getAbsolutePath());
            TestSuiteLogger.log(Level.FINE, logMsg.toString());
        }
    }

    /**
     * Adds a URI reference specifying the location of a Schematron schema; this
     * may be given by (a) the test run argument {@link TestRunArg#SCH sch}, or
     * (b) an <code>xml-model</code> processing instruction in the instance
     * document. The processing instruction takes precedence if both references
     * are found.
     * 
     * <pre>
    * {@code
    * <?xml version="1.0" encoding="UTF-8"?>
    * <?xml-model href="http://www.example.org/constraints.sch" 
    *             schematypens="http://purl.oclc.org/dsdl/schematron" 
    *             phase="#ALL"?>
    * }
     * </pre>
     * 
     * @param suite
     *            An ISuite object representing a TestNG test suite.
     */
    void getSchematronSchema(ISuite suite) {
        Map<String, String> params = suite.getXmlSuite().getParameters();
        String schRef = params.get(TestRunArg.SCH.toString());
        if ((null != schRef) && !schRef.isEmpty()) {
            suite.setAttribute(SuiteAttribute.SCHEMATRON_URI.getName(), URI.create(schRef));
        }
        File gmlFile = (File) suite.getAttribute(SuiteAttribute.TEST_SUBJ_FILE.getName());
        if (null == gmlFile) {
            return;
        }
        Map<String, String> piData = getXmlModelPIData(gmlFile);
        if (null != piData) {
            URI schURI = URI.create(piData.get("href"));
            if (!schURI.isAbsolute()) {
                // resolve relative URI against location of GML data
                String dataURI = suite.getParameter(TestRunArg.IUT.toString());
                URI baseURI = URI.create(dataURI);
                schURI = baseURI.resolve(schURI);
            }
            suite.setAttribute(SuiteAttribute.SCHEMATRON_URI.getName(), schURI);
        }
    }

    /**
     * A client component is added to the suite fixture as the value of the
     * {@link SuiteAttribute#CLIENT} attribute; it may be subsequently accessed
     * via the {@link org.testng.ITestContext#getSuite()} method.
     *
     * @param suite
     *            The test suite instance.
     */
    void registerClientComponent(ISuite suite) {
        Client client = ClientUtils.buildClient();
        if (null != client) {
            suite.setAttribute(SuiteAttribute.CLIENT.getName(), client);
        }
    }

    /**
     * Deletes temporary files created during the test run if TestSuiteLogger is
     * enabled at the INFO level or higher (they are left intact at the CONFIG
     * level or lower).
     *
     * @param suite
     *            The test suite.
     */
    void deleteTempFiles(ISuite suite) {
        if (TestSuiteLogger.isLoggable(Level.CONFIG)) {
            return;
        }
        File testSubjFile = (File) suite.getAttribute(SuiteAttribute.TEST_SUBJ_FILE.getName());
        if (testSubjFile.exists()) {
            testSubjFile.delete();
        }
    }

    /**
     * Extracts the data items from the {@code xml-model} processing
     * instruction. The PI must appear before the document element.
     * 
     * @param dataFile
     *            A File containing the GML instance.
     * @return A Map containing the supplied pseudo-attributes, or {@code null}
     *         if the PI is not present.
     */
    Map<String, String> getXmlModelPIData(File dataFile) {
        Map<String, String> piData = null;
        try (FileInputStream input = new FileInputStream(dataFile)) {
            // input = new FileInputStream(dataFile);
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader reader = factory.createXMLStreamReader(input);
            int event = reader.getEventType();
            // Now in START_DOCUMENT state. Stop at document element.
            while (event != XMLStreamReader.START_ELEMENT) {
                event = reader.next();
                if (event == XMLStreamReader.PROCESSING_INSTRUCTION) {
                    if (reader.getPITarget().equals("xml-model")) {
                        String[] pseudoAttrs = reader.getPIData().split("\\s+");
                        piData = new HashMap<String, String>();
                        for (String pseudoAttr : pseudoAttrs) {
                            String[] nv = pseudoAttr.split("=");
                            piData.put(nv[0].trim(), nv[1].replace('"', ' ').trim());
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            TestSuiteLogger.log(Level.WARNING, "Failed to parse document at " + dataFile.getAbsolutePath(), e);
            return null; // not an XML document
        }
        if (null == piData || !piData.get("schematypens").equals(Namespaces.SCH.toString())) {
            piData = null;
        }
        return piData;
    }
}
