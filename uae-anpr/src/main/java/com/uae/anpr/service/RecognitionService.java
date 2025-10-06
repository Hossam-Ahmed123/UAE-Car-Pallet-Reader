package com.uae.anpr.service;

import com.uae.anpr.model.RecognitionResponse;
import net.sf.javaanpr.imageanalysis.CarSnapshot;
import net.sf.javaanpr.intelligence.Intelligence;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;

@Service
public class RecognitionService {
    private final Intelligence intelligence;

    public RecognitionService(Intelligence intelligence) {
        this.intelligence = intelligence;
    }

    public RecognitionResponse recognize(BufferedImage image) {
        try {
            CarSnapshot snapshot = new CarSnapshot(image);
            String plate = intelligence.recognize(snapshot, false);
            long duration = intelligence.getLastProcessDuration();
            return new RecognitionResponse(plate, duration);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to process image", exception);
        }
    }
}
