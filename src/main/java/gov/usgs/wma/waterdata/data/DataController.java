package gov.usgs.wma.waterdata.data;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import gov.usgs.wma.waterdata.BaseController;
import gov.usgs.wma.waterdata.OgcException;
import gov.usgs.wma.waterdata.collections.CollectionParams;
import gov.usgs.wma.waterdata.discrete.DiscreteDao;
import gov.usgs.wma.waterdata.format.InheritNamespaceAnnotationInspector;
import gov.usgs.wma.waterdata.format.WaterMLPointToXmlResultHandler;
import gov.usgs.wma.waterdata.openapi.schema.timeseries.TimeSeriesGeoJSON;
import gov.usgs.wma.waterdata.parameter.ContentType;
import gov.usgs.wma.waterdata.parameter.DataType;
import gov.usgs.wma.waterdata.parameter.Domain;
import gov.usgs.wma.waterdata.timeseries.TimeSeriesDao;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.*;
import javax.xml.stream.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Observations Data Sets", description = "Data sets such as Statistical Time Series")
@RestController
public class DataController extends BaseController {
	protected TimeSeriesDao timeSeriesDao;
	protected DiscreteDao discreteDao;

	protected final String contentTypeDesc = "Content format returned, currently only WaterML";

	@Autowired
	public DataController(TimeSeriesDao timeSeriesDao, DiscreteDao discreteDao) {
		this.timeSeriesDao = timeSeriesDao;
		this.discreteDao = discreteDao;
	}

	@Operation(description = "Return GeoJSON or Water ML Data specific to the requested Monitoring Location and data type.", responses = {
			@ApiResponse(responseCode = "200", description = "GeoJSON representation of the Time Series.", content = @Content(schema = @Schema(implementation = TimeSeriesGeoJSON.class))),
			@ApiResponse(responseCode = "400", description = HTTP_400_DESCRIPTION, content = @Content(schema = @Schema(implementation = OgcException.class))),
			@ApiResponse(responseCode = "404", description = HTTP_404_DESCRIPTION, content = @Content(schema = @Schema(implementation = OgcException.class))),
			@ApiResponse(responseCode = "500", description = HTTP_500_DESCRIPTION, content = @Content(schema = @Schema(implementation = OgcException.class))) },
				externalDocs = @ExternalDocumentation(url = "https://github.com/opengeospatial/omsf-profile/tree/master/omsf-json"))
	@GetMapping(value = "data", produces = { MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE })
	public void getTimeSeries(
			@Parameter(description = "Monitoring location Identifier") @RequestParam(value = "monitoringLocationID", required = false) String monLocIdentifier,
			@Parameter(description = "Data type requested") @RequestParam(value = "type", required = true) DataType dataType,
			@Parameter(description = "Limits results to time series marked as best = true|false") @RequestParam(value = "best", required = false) Boolean best,
			@Parameter(description = "Limits data to specfied area") @RequestParam(value = "domain", required = true) List<Domain> domains,
			@Parameter(in = ParameterIn.QUERY, description = contentTypeDesc, schema = @Schema(type = "string"), examples = {
					@ExampleObject(name = "waterML", value = "WaterML", description = "Water ML") }) @RequestParam(value = "f", required = false, defaultValue = "waterml") String mimeType,
			HttpServletResponse response) throws HttpMediaTypeNotAcceptableException, IOException {

		// not using the ContentType returned yet, since we only have waterML
		determineContentType(mimeType, List.of(ContentType.waterml));
		String rtn = null;
		String bestTS = best == null ? CollectionParams.PARAM_MATCH_ANY : best.toString().toLowerCase();

		if (Domain.includesGroundWaterLevels(domains) && dataType.isStatisticalTimeSeries()) {
			rtn = timeSeriesDao.getTimeSeriesWaterML(monLocIdentifier, bestTS);
			response.setContentType(MediaType.APPLICATION_XML_VALUE);
		}

		if (rtn == null) {
			response.setStatus(HttpStatus.NOT_FOUND.value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			rtn = ogc404Payload;
		}

		response.getWriter().print(rtn);
	}

	public void getDiscrete(String monLocIdentifier, DataType dataType, Boolean best, List<Domain> domains,
				String mimeType, HttpServletResponse response) throws HttpMediaTypeNotAcceptableException, IOException, XMLStreamException, JAXBException {

		//Configure an XMLInputFactory and XMLStreamWriter
		//I think the XMLInputFactory is used to consume a stream of XML events from the
		//XmlMapper.  Then the XMLStreamWriter is used to convert those XML events into
		//a character stream.
		XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();
		XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newFactory();
		xmlOutputFactory.setProperty("javax.xml.stream.isRepairingNamespaces", "true");
		XMLStreamWriter sw = xmlOutputFactory.createXMLStreamWriter(response.getWriter());

		//Configure FasterXML XmlMapper
		XmlMapper mapper = new XmlMapper(xmlInputFactory);
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		mapper.findAndRegisterModules();	//Probably should be done in a more spring way so its not redone each time
		mapper.setAnnotationIntrospector(new InheritNamespaceAnnotationInspector());	//Custom:  Allows fields to inherit the NS of the parent

		WaterMLPointToXmlResultHandler formatter = new WaterMLPointToXmlResultHandler(mapper, sw);

		sw.setPrefix("wml2", "http://www.opengis.net/waterml/2.0");
		sw.setPrefix("om", "http://www.opengis.net/om/2.0");

		sw.writeStartDocument("utf-8", "1.0");
		sw.writeStartElement("wml2", "Collection", "http://www.opengis.net/waterml/2.0");
		sw.writeNamespace("wml2", "http://www.opengis.net/waterml/2.0");
		sw.writeNamespace("om", "http://www.opengis.net/om/2.0");
		sw.writeDefaultNamespace("http://www.opengis.net/waterml/2.0");
		sw.setDefaultNamespace("http://www.opengis.net/waterml/2.0");

		discreteDao.getDiscreteGWMLPoint(monLocIdentifier, formatter);

		sw.writeEndElement();
		sw.writeEndDocument();
	}
}