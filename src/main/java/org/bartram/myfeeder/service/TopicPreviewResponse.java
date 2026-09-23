package org.bartram.myfeeder.service;

/**
 * The topic preview result (D-13): the raw noul in [0, 1] for the previewed topic and the Jev
 * model id. It carries no computed points; the client applies the R6 hinge and the draft weight.
 */
public record TopicPreviewResponse(Double noul, String model) {}
