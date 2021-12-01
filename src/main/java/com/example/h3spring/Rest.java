package com.example.h3spring;

import org.springframework.web.bind.annotation.*;
import org.wololo.geojson.Feature;
import org.wololo.geojson.FeatureCollection;
import org.wololo.geojson.GeoJSON;

@RestController
public class Rest {

    private Integer resolution = 8;
    @RequestMapping(value = "/polyfill", method = RequestMethod.POST, produces = {"application/json", "application/xml"}
            , consumes = {"application/json", "application/x-www-form-urlencoded"})
    @ResponseBody
    public GeoJSON getPolyfill(@RequestBody FeatureCollection geojson) {
        return H3Utils.geoJSONPolyfill(geojson, resolution);
    }

    @RequestMapping(value = "/polyfill-and-kring", method = RequestMethod.POST, produces = {"application/json", "application/xml"}
        , consumes = {"application/json", "application/x-www-form-urlencoded"})
    @ResponseBody
    public GeoJSON getPolyfillAndKring(@RequestBody FeatureCollection geojson) {
        return H3Utils.getPolyfillAndKring(geojson, resolution);
    }

    @RequestMapping(value = "/pinot-prod", method = RequestMethod.POST, produces = {"application/json", "application/xml"}
        , consumes = {"application/json", "application/x-www-form-urlencoded"})
    @ResponseBody
    public GeoJSON pinotProduction(@RequestBody FeatureCollection geojson) {
        return H3Utils.pinotProduction(geojson, resolution);
    }

    @RequestMapping(value = "/pinot-wdist", method = RequestMethod.POST, produces = {"application/json", "application/xml"}
        , consumes = {"application/json", "application/x-www-form-urlencoded"})
    @ResponseBody
    public GeoJSON pinotWDist(@RequestBody FeatureCollection geojson) {
        return H3Utils.withinDistance(geojson, resolution);
    }

    @RequestMapping(value = "/get-buffer", method = RequestMethod.POST, produces = {"application/json", "application/xml"}
        , consumes = {"application/json", "application/x-www-form-urlencoded"})
    @ResponseBody
    public GeoJSON getBuffer(@RequestBody FeatureCollection geojson) {
        return H3Utils.getBuffer(geojson);
    }

}
