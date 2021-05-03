/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.alvearie.imaging.ingestion;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;

import org.alvearie.imaging.ingestion.event.Element;
import org.alvearie.imaging.ingestion.event.Image;
import org.alvearie.imaging.ingestion.event.ImageStoredEvent;
import org.alvearie.imaging.ingestion.event.Store;
import org.alvearie.imaging.ingestion.model.result.DicomQueryModel;
import org.alvearie.imaging.ingestion.model.result.DicomQueryModel.Scope;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.quarkus.funqy.knative.events.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class QueryResourceTest {
    @Inject
    EventProcessorFunction processor;

    @BeforeEach
    public void setup() {
        createData();
    }

    @Test
    public void testGetResultsMissingSourceQueryParam() {
        given().log().all(true).get("/query/studies/123").then().log().all(true).statusCode(400);
    }

    @Test
    public void testGetResultsSourceQueryParam() {
        given().log().all(true).get("/query/studies/123?source=test").then().log().all(true).statusCode(200);
    }

    @Test
    public void testGetStudyResults() {
        DicomQueryModel model = new DicomQueryModel();
        model.setScope(Scope.STUDY);
        given().log().all(true).headers("Content-Type", MediaType.APPLICATION_JSON).body(model)
                .post("/query/studies?source=test").then().log().all(true).statusCode(200).body("size()", is(1));
    }

    @Test
    public void testGetSeriesResults() {
        DicomQueryModel model = new DicomQueryModel();
        model.setStudyUid("study1");
        model.setScope(Scope.SERIES);
        given().log().all(true).headers("Content-Type", MediaType.APPLICATION_JSON).body(model)
                .post("/query/studies?source=test").then().log().all(true).statusCode(200).body("size()", is(1));
    }

    @Test
    public void testGetInstanceResults() {
        DicomQueryModel model = new DicomQueryModel();
        model.setStudyUid("study1");
        model.setSeriesUid("series1");
        model.setScope(Scope.INSTANCE);
        given().log().all(true).headers("Content-Type", MediaType.APPLICATION_JSON).body(model)
                .post("/query/studies?source=test").then().log().all(true).statusCode(200).body("size()", is(1));
    }

    private void createData() {
        ImageStoredEvent data = new ImageStoredEvent();

        List<Element> elements = new ArrayList<>();

        elements.add(buildElement(Tag.StudyInstanceUID, VR.UI, "study1"));
        elements.add(buildElement(Tag.SeriesInstanceUID, VR.UI, "series1"));
        elements.add(buildElement(Tag.SOPInstanceUID, VR.UI, "instance1"));
        elements.add(buildElement(Tag.Modality, VR.CS, "CT"));
        elements.add(buildElement(Tag.PatientID, VR.LO, "patient1"));
        elements.add(buildElement(Tag.SeriesDescription, VR.LO, "series desc"));
        elements.add(buildElement(Tag.SeriesNumber, VR.IS, "1"));
        elements.add(buildElement(Tag.InstanceNumber, VR.IS, "1"));

        Image image = new Image();
        image.setElements(elements);
        image.setTransferSyntaxUID("tsuid");

        data.setImage(image);

        Store store = new Store();
        store.setProvider("test");

        data.setStore(store);

        CloudEvent event = Mockito.mock(CloudEvent.class);
        processor.imageStoredEventChain(data, event);
    }

    private Element buildElement(int tag, VR vr, String value) {
        String ts = String.format("%08X", tag);

        Element elem = new Element();
        elem.setGroup(ts.substring(0, 4));
        elem.setElement(ts.substring(4));
        elem.setVR(vr.name());
        elem.setValue(value);

        return elem;
    }
}
