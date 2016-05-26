package org.opengis.cite.gml32.data.document;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.testng.ISuite;
import org.testng.ITestContext;
import org.xml.sax.SAXException;

/**
 * Verifies the behavior of the Capability1Tests test class. Test stubs replace
 * fixture constituents where appropriate.
 */
public class VerifyGmlDocumentTests {

    private static final String TEST_FILE = "testSubjectFile";
    private static ITestContext testContext;
    private static ISuite suite;
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    public VerifyGmlDocumentTests() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        testContext = mock(ITestContext.class);
        suite = mock(ISuite.class);
        when(testContext.getSuite()).thenReturn(suite);
    }

    @Test
    public void noSchemaReference() throws URISyntaxException, SAXException, IOException {
        thrown.expect(AssertionError.class);
        thrown.expectMessage("Unable to determine schema location from document element");
        URL url = this.getClass().getResource("/atom-feed.xml");
        File dataFile = new File(url.toURI());
        GmlDocumentTests iut = new GmlDocumentTests();
        iut.setTestSubject(dataFile);
        iut.hasAppSchemaReference();
    }

    @Test
    public void isXMLSchemaValid() throws URISyntaxException, SAXException, IOException {
        URL url = this.getClass().getResource("/SimpleFeature-1.xml");
        File dataFile = new File(url.toURI());
        when(suite.getAttribute(TEST_FILE)).thenReturn(dataFile);
        when(suite.getAttribute("testSubjectURI")).thenReturn(dataFile.getParentFile().toURI());
        GmlDocumentTests iut = new GmlDocumentTests();
        iut.initFixture(testContext);
        iut.hasAppSchemaReference();
        iut.checkXMLSchemaValidity();
    }

    @Test
    public void notXMLSchemaValid() throws URISyntaxException, SAXException, IOException {
        thrown.expect(AssertionError.class);
        thrown.expectMessage("2 schema validation error(s) detected");
        URL url = this.getClass().getResource("/SimpleFeature-2.xml");
        File dataFile = new File(url.toURI());
        when(suite.getAttribute(TEST_FILE)).thenReturn(dataFile);
        when(suite.getAttribute("testSubjectURI")).thenReturn(dataFile.getParentFile().toURI());
        GmlDocumentTests iut = new GmlDocumentTests();
        iut.initFixture(testContext);
        iut.hasAppSchemaReference();
        iut.checkXMLSchemaValidity();
    }

    @Test
    public void notSchematronValid() throws Exception {
        thrown.expect(AssertionError.class);
        thrown.expectMessage("1 schema validation error(s) detected");
        URL dataUrl = this.getClass().getResource("/SimpleFeature-xml-model.xml");
        URL schUrl = this.getClass().getResource("/sch/simple.sch");
        File dataFile = new File(dataUrl.toURI());
        when(suite.getAttribute(TEST_FILE)).thenReturn(dataFile);
        when(suite.getAttribute("testSubjectURI")).thenReturn(dataFile.getParentFile().toURI());
        when(testContext.getSuite().getAttribute("schematronURI")).thenReturn(schUrl.toURI());
        GmlDocumentTests iut = new GmlDocumentTests();
        iut.initFixture(testContext);
        iut.checkSchematronConstraints(testContext);
    }
}
