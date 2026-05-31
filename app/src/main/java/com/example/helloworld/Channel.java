package com.example.helloworld;

final class Channel {
    final int streamId;
    final String name;
    final String categoryId;
    boolean selected;

    Channel(int streamId, String name, String categoryId) {
        this.streamId = streamId;
        this.name = name;
        this.categoryId = categoryId;
        this.selected = false;
    }
}
