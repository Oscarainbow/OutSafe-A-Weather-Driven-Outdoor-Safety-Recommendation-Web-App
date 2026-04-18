package com.outsafe.backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.outsafe.backend.model.PredictionRequest;
import com.outsafe.backend.model.PredictionResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Service
public class RfWeatherService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ModelRoot modelRoot;

    @PostConstruct
    public void loadModel() throws Exception {
        ClassPathResource resource = new ClassPathResource("models/rf_weather_24h.json");
        try (InputStream in = resource.getInputStream()) {
            modelRoot = objectMapper.readValue(in, ModelRoot.class);
        }
    }

    public PredictionResponse predict24h(PredictionRequest req) {
        double temp = predictForest("next_temp_max", req);
        double tempMin = predictForest("next_temp_min", req);
        double apparent = predictForest("next_apparent_min", req);
        double humidity = predictForest("next_humidity_max", req);
        double precipitation = predictForest("next_precip_sum", req);
        double wind = predictForest("next_wind_max", req);
        double gust = predictForest("next_gust_max", req);

        String summary = "Mild";
        if (precipitation >= 8) summary = "Rain likely";
        else if (wind >= 35) summary = "Windy";
        else if (temp >= 28) summary = "Warm";
        else if (tempMin <= 0) summary = "Cold";

        return new PredictionResponse(summary, temp, tempMin, apparent, humidity, precipitation, wind, gust);
    }

    private double predictForest(String modelKey, PredictionRequest req) {
        Forest forest = modelRoot.getModels().get(modelKey);
        double sum = 0.0;
        for (Tree tree : forest.getTrees()) {
            sum += predictTree(tree, forest.getFeature_names(), req);
        }
        return sum / forest.getTrees().size();
    }

    private double predictTree(Tree tree, List<String> featureNames, PredictionRequest req) {
        int node = 0;

        while (true) {
            int left = tree.getChildren_left().get(node);
            int right = tree.getChildren_right().get(node);

            if (left == -1 && right == -1) {
                return tree.getValue().get(node);
            }

            int featureIndex = tree.getFeature().get(node);
            double threshold = tree.getThreshold().get(node);
            String featureName = featureNames.get(featureIndex);
            double value = getFeatureValue(req, featureName);

            node = value <= threshold ? left : right;
        }
    }

    private double getFeatureValue(PredictionRequest req, String featureName) {
        return switch (featureName) {
            case "temp_max" -> safe(req.getTemp_max());
            case "temp_min" -> safe(req.getTemp_min());
            case "apparent_min" -> safe(req.getApparent_min());
            case "humidity_max" -> safe(req.getHumidity_max());
            case "precip_sum" -> safe(req.getPrecip_sum());
            case "wind_max" -> safe(req.getWind_max());
            case "gust_max" -> safe(req.getGust_max());
            default -> 0.0;
        };
    }

    private double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModelRoot {
        private List<String> feature_names;
        private Map<String, Forest> models;

        public List<String> getFeature_names() { return feature_names; }
        public void setFeature_names(List<String> feature_names) { this.feature_names = feature_names; }

        public Map<String, Forest> getModels() { return models; }
        public void setModels(Map<String, Forest> models) { this.models = models; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Forest {
        private List<String> feature_names;
        private List<Tree> trees;

        public List<String> getFeature_names() { return feature_names; }
        public void setFeature_names(List<String> feature_names) { this.feature_names = feature_names; }

        public List<Tree> getTrees() { return trees; }
        public void setTrees(List<Tree> trees) { this.trees = trees; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Tree {
        private List<Integer> children_left;
        private List<Integer> children_right;
        private List<Integer> feature;
        private List<Double> threshold;
        private List<Double> value;

        public List<Integer> getChildren_left() { return children_left; }
        public void setChildren_left(List<Integer> children_left) { this.children_left = children_left; }

        public List<Integer> getChildren_right() { return children_right; }
        public void setChildren_right(List<Integer> children_right) { this.children_right = children_right; }

        public List<Integer> getFeature() { return feature; }
        public void setFeature(List<Integer> feature) { this.feature = feature; }

        public List<Double> getThreshold() { return threshold; }
        public void setThreshold(List<Double> threshold) { this.threshold = threshold; }

        public List<Double> getValue() { return value; }
        public void setValue(List<Double> value) { this.value = value; }
    }
}