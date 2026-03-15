package org.bartram.myfeeder.controller;

import lombok.Data;
import java.util.List;

@Data
public class MarkReadRequest {
    private List<Long> articleIds;
    private Long feedId;
}
