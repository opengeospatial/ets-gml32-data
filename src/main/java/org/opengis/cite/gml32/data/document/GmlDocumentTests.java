package org.opengis.cite.gml32.data.document;

import static org.testng.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Set;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.Validator;

import org.opengis.cite.gml32.data.CommonFixture;
import org.opengis.cite.gml32.data.ErrorMessage;
import org.opengis.cite.gml32.data.ErrorMessageKeys;
import org.opengis.cite.gml32.data.SuiteAttribute;
import org.opengis.cite.gml32.data.util.ValidationUtils;
import org.opengis.cite.gml32.data.util.XMLUtils;
import org.opengis.cite.validation.SchematronValidator;
import org.opengis.cite.validation.ValidationErrorHandler;
import org.opengis.cite.validation.XmlSchemaCompiler;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.xml.sax.SAXException;

/**
 * Includes tests that apply to a GML instance document as a whole.
 */
public class GmlDocumentTests extends CommonFixture {

    private URI gmlDataUri;
    private File gmlDataFile;
    private Schema appSchema;
    private XMLInputFactory staxFactory;

    /**
     * Sets the test subject. This method is intended to facilitate unit
     * testing.
     *
     * @param testSubject
     *            A File containing GML data.
     */
    void setTestSubject(File testSubject) {
        this.gmlDataFile = testSubject;
        this.gmlDataUri = testSubject.getParentFile().toURI();
    }

    /**
     * Obtains the test subject from the ISuite context. The suite attribute
     * {@link org.opengis.cite.gml32.data.SuiteAttribute#TEST_SUBJ_FILE} should
     * evaluate to a File object that contains a GML document.
     * 
     * @param testContext
     *            The test (group) context.
     */
    @BeforeClass
    public void initFixture(ITestContext testContext) {
        Object obj = testContext.getSuite().getAttribute(SuiteAttribute.TEST_SUBJ_FILE.getName());
        if ((null != obj) && File.class.isAssignableFrom(obj.getClass())) {
            this.gmlDataFile = File.class.cast(obj);
        }
        this.gmlDataUri = (URI) testContext.getSuite().getAttribute(SuiteAttribute.TEST_SUBJ_URI.getName());
        this.staxFactory = XMLInputFactory.newInstance();
        staxFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.TRUE);
        staxFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        staxFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
        staxFactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
    }

    /**
     * Verify the existence of a reference to a GML application schema, as
     * indicated by the value of the xsi:schemaLocation attribute on the
     * document element.
     */
    @Test(description = "See ATC: A.3.1, A.3.2")
    public void hasAppSchemaReference() {
        String baseURI = this.gmlDataUri.toString();
        Set<URI> schemaRefs;
        try {
            schemaRefs = ValidationUtils.extractSchemaReferences(new StreamSource(this.gmlDataFile), baseURI);
        } catch (XMLStreamException e) {
            throw new AssertionError("Failed to read GML document: " + e.getMessage());
        }
        assertNotNull(schemaRefs, ErrorMessage.get(ErrorMessageKeys.MISSING_SCHEMA_LOC));
        URL entityCatalog = ValidationUtils.class.getResource(ROOT_PKG_PATH + "schema-catalog.xml");
        XmlSchemaCompiler xsdCompiler = new XmlSchemaCompiler(entityCatalog);
        try {
            this.appSchema = xsdCompiler.compileXmlSchema(schemaRefs.toArray(new URI[schemaRefs.size()]));
        } catch (SAXException | IOException e) {
            throw new AssertionError("Failed to compile GML app schema: " + e.getMessage());
        }
        ValidationErrorHandler errHandler = xsdCompiler.getErrorHandler();
        assertFalse(errHandler.errorsDetected(),
                ErrorMessage.format(ErrorMessageKeys.XSD_INVALID, errHandler.getErrorCount(), errHandler.toString()));
    }

    /**
     * Verify that the GML document is valid with respect to the referenced
     * application schema(s).
     *
     * @throws SAXException
     *             If the resource cannot be parsed.
     * @throws IOException
     *             If the resource is not accessible.
     */
    @Test(description = "See ATC: A.3.4", dependsOnMethods = "hasAppSchemaReference")
    public void checkXMLSchemaValidity() throws SAXException, IOException {
        Validator validator = this.appSchema.newValidator();
        ValidationErrorHandler errHandler = new ValidationErrorHandler();
        validator.setErrorHandler(errHandler);
        FileInputStream fis = new FileInputStream(this.gmlDataFile);
        try {
            XMLStreamReader reader = this.staxFactory.createXMLStreamReader(fis, "UTF-8");
            validator.validate(new StAXSource(reader));
        } catch (XMLStreamException e) {
            throw new AssertionError("Error reading GML document: " + e.getMessage());
        }
        assertFalse(errHandler.errorsDetected(), ErrorMessage.format(ErrorMessageKeys.NOT_SCHEMA_VALID,
                errHandler.getErrorCount(), errHandler.toString()));
    }

    /**
     * Verify that the GML document satisfies the additional constraints defined
     * in a Schematron schema. All phases are active.
     * 
     * <p style="margin-bottom: 0.5em">
     * <strong>Sources</strong>
     * </p>
     * <ul>
     * <li><a href=
     * "http://standards.iso.org/ittf/PubliclyAvailableStandards/c040833_ISO_IEC_19757-3_2006(E).zip"
     * >ISO 19757-3:2006</a></li>
     * <li><a href="http://www.w3.org/TR/xml-model/">Associating Schemas with
     * XML documents 1.0 (Second Edition)</a></li>
     * </ul>
     * 
     * @param testContext
     *            Information about the test run
     * @throws Exception
     *             If the Schematron schema cannot be read for some reason.
     * 
     */
    @Test(description = "ISO 19757-3")
    public void checkSchematronConstraints(ITestContext testContext) throws Exception {
        Object schRef = testContext.getSuite().getAttribute(SuiteAttribute.SCHEMATRON_URI.getName());
        if (null == schRef) {
            throw new SkipException("No Schematron schema reference for data resource at " + this.gmlDataUri);
        }
        Source schema = new StreamSource(schRef.toString());
        SchematronValidator validator = new SchematronValidator(schema, null);
        Source gmlSource = new StreamSource(this.gmlDataFile);
        DOMResult result = validator.validate(gmlSource);
        assertFalse(validator.ruleViolationsDetected(), ErrorMessage.format(ErrorMessageKeys.NOT_SCHEMA_VALID,
                validator.getRuleViolationCount(), XMLUtils.writeNodeToString(result.getNode())));
    }

}
