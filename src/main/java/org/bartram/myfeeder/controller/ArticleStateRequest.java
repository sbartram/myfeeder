package org.bartram.myfeeder.controller;

import lombok.Data;

@Data
public class ArticleStateRequest {
    private Boolean read;
    private Boolean starred;
}
