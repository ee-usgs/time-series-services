package gov.usgs.wma.waterdata.openapi.schema.observations;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonPropertyOrder(value={"observationTypes"})
@Schema(description="Observations data in JSON format.")
public class ObservationsJSON {
	@Schema(description="available observations")
	public ObservationJSON[] getObservations() {
		return null;
	}
}