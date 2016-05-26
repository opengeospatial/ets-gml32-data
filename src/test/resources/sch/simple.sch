<?xml version="1.0" encoding="UTF-8"?>
<iso:schema id="SimpleFeature" 
  xmlns:iso="http://purl.oclc.org/dsdl/schematron" 
  xml:lang="en"
  queryBinding="xslt2"
  defaultPhase="MainPhase">

  <iso:title>Simple Feature Constraints</iso:title>

  <iso:ns prefix="tns" uri="http://example.org/ns1" />
  <iso:ns prefix="gml" uri="http://www.opengis.net/gml/3.2" />

  <iso:phase id="MainPhase">
    <iso:active pattern="SimpleFeaturePattern"/>
  </iso:phase>

  <iso:pattern id="SimpleFeaturePattern">
    <iso:title>General rules that apply to any SimpleFeature instance</iso:title>
    <iso:rule context="tns:SimpleFeature">
      <iso:assert test="not(gml:boundedBy) or tns:surfaceProperty or tns:curveProperty" 
        diagnostics="gml-id">Expected tns:surfaceProperty or tns:curveProperty if gml:boundedBy is present.</iso:assert>
    </iso:rule>
  </iso:pattern>

  <iso:diagnostics>
    <iso:diagnostic id="gml-id" xml:lang="en">GML identifier: '<iso:value-of select="@gml:id"/>'"/></iso:diagnostic>
  </iso:diagnostics>
</iso:schema>
