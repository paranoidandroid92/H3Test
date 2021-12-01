package com.example.h3spring;

import com.uber.h3core.H3Core;
import com.uber.h3core.exceptions.LineUndefinedException;
import com.uber.h3core.util.GeoCoord;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import lombok.SneakyThrows;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.wololo.geojson.Feature;
import org.wololo.geojson.FeatureCollection;
import org.wololo.geojson.GeoJSON;
import org.wololo.geojson.Geometry;
import org.wololo.jts2geojson.GeoJSONReader;
import org.wololo.jts2geojson.GeoJSONWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class H3Utils {
  public static H3Core H3_CORE;


  static {
    try {
      H3_CORE = H3Core.newInstance();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static GeoJSONWriter writer = new GeoJSONWriter();
  private static GeoJSONReader reader = new GeoJSONReader();
  private static GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4236);

  public static Feature h3IndexToGeoJSON(long h3Index, HashMap properties) {
    final List<Coordinate> coordinates = new ArrayList<>();
    List<GeoCoord> geoCoords = H3_CORE.h3ToGeoBoundary(h3Index);
    geoCoords.forEach(coord -> {
      coordinates.add(new Coordinate(coord.lng, coord.lat));
    });
    coordinates.add(new Coordinate(geoCoords.get(0).lng, geoCoords.get(0).lat));
    Polygon polygon = factory.createPolygon(coordinates.toArray(new Coordinate[0]));

    Geometry write = writer.write(polygon);
    Feature feature = new Feature(write, properties != null ? properties : new HashMap<>());
    return feature;
  }

  public static FeatureCollection h3IndexListToGeoJSON(Feature polygon,
      Collection<Long> h3IndexList, HashMap properties) {
    List<Feature> featureList = new ArrayList<>();

    for (Long h3Index : h3IndexList) {
      Feature feature = h3IndexToGeoJSON(h3Index, properties);
      featureList.add(feature);
    }
    featureList.add(polygon);
    return new FeatureCollection(featureList.toArray(new Feature[0]));
  }

  public static FeatureCollection geoJSONPolyfill(GeoJSON geoJSON, Integer resolution) {
    List<GeoCoord> geoCoordList = new ArrayList<>();
    Feature polygon = ((FeatureCollection) geoJSON).getFeatures()[0];

    double[][][] coordinates = ((org.wololo.geojson.Polygon) polygon.getGeometry()).getCoordinates();

    for (int i = 0; i < coordinates[0].length; i++) {
      double lon = coordinates[0][i][0];
      double lat = coordinates[0][i][1];
      geoCoordList.add(new GeoCoord(lat, lon));
    }

    List<Long> polyfill = H3_CORE.polyfill(geoCoordList, new ArrayList<>(), resolution);
    FeatureCollection featureCollection = h3IndexListToGeoJSON(polygon, polyfill, new HashMap());
    return featureCollection;
  }

  public static FeatureCollection getPolyfillAndKring(GeoJSON geoJSON, int resolution) {
    List<GeoCoord> geoCoordList = new ArrayList<>();
    Feature polygon = ((FeatureCollection) geoJSON).getFeatures()[0];

    double[][][] coordinates = ((org.wololo.geojson.Polygon) polygon.getGeometry()).getCoordinates();

    for (int i = 0; i < coordinates[0].length; i++) {
      double lon = coordinates[0][i][0];
      double lat = coordinates[0][i][1];
      geoCoordList.add(new GeoCoord(lat, lon));
    }

    List<Long> polyfill = H3_CORE.polyfill(geoCoordList, new ArrayList<>(), resolution);
    Set<Long> kring = new HashSet<>(polyfill);
    for (Long h3Index : polyfill) {
      kring.addAll(H3_CORE.kRing(h3Index, 1));
    }
    FeatureCollection featureCollection = h3IndexListToGeoJSON(polygon, new ArrayList<>(kring), new HashMap<>());
    return featureCollection;
  }

  private static FeatureCollection mergeCollections(FeatureCollection f1, FeatureCollection f2) {
    Feature[] features = f1.getFeatures();
    Feature[] features2 = f2.getFeatures();
    List<Feature> featureList = new ArrayList<>();
    featureList.addAll(Arrays.asList(features));
    featureList.addAll(Arrays.asList(features2));

    return new FeatureCollection(featureList.toArray(new Feature[0]));
  }

  private static HashMap<String, Object> createColorProperties(String stroke, String fill) {
    HashMap<String, Object> properties = new HashMap<>();
    properties.put("stroke", stroke);
    properties.put("stroke-width", 2);
    properties.put("stroke-opacity", 1);
    properties.put("fill", fill);
    properties.put("fill-opacity", 0.5);
    return properties;
  }

  public static FeatureCollection pinotProduction(GeoJSON geoJSON, int resolution) {
    List<Coordinate> coordinates = new ArrayList<>();
    Feature polygon = null;
    if (geoJSON instanceof FeatureCollection) {
      polygon = ((FeatureCollection) geoJSON).getFeatures()[0];
    } else if (geoJSON instanceof org.wololo.geojson.Feature) {
      polygon = ((Feature) geoJSON);
    } else if (geoJSON instanceof org.wololo.geojson.Polygon) {
      polygon = new Feature((org.wololo.geojson.Polygon) geoJSON, new HashMap<>());
    }

    double[][][] coords = ((org.wololo.geojson.Polygon) polygon.getGeometry()).getCoordinates();

    for (int i = 0; i < coords[0].length; i++) {
      double lon = coords[0][i][0];
      double lat = coords[0][i][1];
      coordinates.add(new Coordinate(lat, lon));
    }

    Set<Long> alwaysMatchH3Ids = getAlwaysMatchH3Ids(coordinates, resolution);
    Set<Long> possibleMatchH3Ids = getPossibleMatchH3Ids(coordinates, resolution);
    String color1 = "#f17e7e";
    String color2 = "#7ebff1";
    HashMap<String, Object> colorProperties1 = createColorProperties(color1, color1);
    HashMap<String, Object> colorProperties2 = createColorProperties(color2, color2);
    FeatureCollection featureCollection1 = h3IndexListToGeoJSON(polygon, alwaysMatchH3Ids, colorProperties1);
    FeatureCollection featureCollection2 = h3IndexListToGeoJSON(polygon, possibleMatchH3Ids, colorProperties2);

    FeatureCollection featureCollection = mergeCollections(featureCollection1, featureCollection2);
    return featureCollection;
  }

  private static Set<Long> getAlwaysMatchH3Ids(List<Coordinate> coordinates, int resolution) {
    List<GeoCoord> geoCoordList = new ArrayList<>();
    for (Coordinate coord : coordinates) {
      geoCoordList.add(new GeoCoord(coord.x, coord.y));
    }
    List<Long> polyfillH3Indices = H3_CORE.polyfill(geoCoordList, new ArrayList<>(), resolution);
    polyfillH3Indices.removeAll(getPossibleMatchH3Ids(coordinates, resolution));
    return new HashSet<>(polyfillH3Indices);
  }

  private static Set<Long> getPossibleMatchH3Ids(List<Coordinate> coordinates, int resolution) {
    Set<Long> possibleMatches = new HashSet<>();
    for (int i = 0; i < coordinates.size() - 1; i++) {
      long p1 = H3_CORE.geoToH3(coordinates.get(i).x, coordinates.get(i).y, resolution);
      long p2 = H3_CORE.geoToH3(coordinates.get(i + 1).x, coordinates.get(i + 1).y, resolution);
      try {
        List<Long> line = H3_CORE.h3Line(p1, p2);
        possibleMatches.addAll(line);
      } catch (LineUndefinedException e) {
        e.printStackTrace();
      }
    }
    return possibleMatches;
  }

  ////////////////////////
  public static FeatureCollection withinDistance(FeatureCollection geoJSON, int resolution) {
    Feature feature = geoJSON.getFeatures()[0];
    List<Feature> featureList = new ArrayList<>();

    org.locationtech.jts.geom.Geometry geom = reader.read(feature.getGeometry(), factory);
    org.locationtech.jts.geom.Geometry buffer = buffer(geom, 2000);

    Set<Long> innerHexagons = getInnerHexagons(buffer, resolution);
    Set<Long> outerHexagons = getOuterHexagons(buffer, resolution);
    innerHexagons.addAll(outerHexagons);

    Set<Long> originalInnerHexagons = getInnerHexagons(geom, resolution);
    Set<Long> originalOuterHexagons = getOuterHexagons(geom, resolution);
    originalInnerHexagons.removeAll(originalOuterHexagons);

    innerHexagons.removeAll(originalInnerHexagons);
    outerHexagons.addAll(originalOuterHexagons);

    String color1 = "#f17e7e";
    String color2 = "#7ebff1";
    HashMap<String, Object> colorProperties1 = createColorProperties(color1, color1);
    HashMap<String, Object> colorProperties2 = createColorProperties(color2, color2);
    FeatureCollection featureCollection1 = h3IndexListToGeoJSON(feature, innerHexagons, colorProperties1);
    FeatureCollection featureCollection2 = h3IndexListToGeoJSON(feature, outerHexagons, colorProperties2);

    featureList.addAll(Arrays.asList(featureCollection1.getFeatures()));
    featureList.addAll(Arrays.asList(featureCollection2.getFeatures()));
    featureList.add(new Feature(writer.write(buffer), new HashMap<>()));

    FeatureCollection featureCollection = new FeatureCollection(featureList.toArray(new Feature[0]));
    return featureCollection;
  }

  private static Set<Long> getInnerHexagons(org.locationtech.jts.geom.Geometry geometry, int resolution) {
    List<GeoCoord> geoCoordList = new ArrayList<>();
    for (Coordinate coord : geometry.getCoordinates()) {
      geoCoordList.add(new GeoCoord(coord.y, coord.x));
    }
    List<Long> polyfillH3Indices = H3Utils.H3_CORE.polyfill(geoCoordList, new ArrayList<>(), resolution);
    return new HashSet<>(polyfillH3Indices);
  }

  private static Set<Long> getOuterHexagons(org.locationtech.jts.geom.Geometry geometry, int resolution) {
    Set<Long> possibleMatches = new HashSet<>();
    Coordinate[] coordinates = geometry.getCoordinates();
    for (int i = 0; i < coordinates.length - 1; i++) {
      long p1 = H3Utils.H3_CORE.geoToH3(coordinates[i].y, coordinates[i].x, resolution);
      long p2 = H3Utils.H3_CORE.geoToH3(coordinates[i + 1].y, coordinates[i + 1].x, resolution);
      try {
        List<Long> line = H3Utils.H3_CORE.h3Line(p1, p2);
        possibleMatches.addAll(line);
      } catch (LineUndefinedException e) {
        e.printStackTrace();
      }
    }
    return possibleMatches;
  }

  /**
   * @param geometry to be buffered
   * @param distanceInMeters buffer distance
   * @return returns a buffered polygon
   */
  private static org.locationtech.jts.geom.Geometry buffer(org.locationtech.jts.geom.Geometry geometry,
      double distanceInMeters) {
    String code =
        "AUTO:42001," + geometry.getCentroid().getCoordinate().x + "," + geometry.getCentroid().getCoordinate().y;
    org.locationtech.jts.geom.Geometry bufferedGeometry = null;

    try {
      CoordinateReferenceSystem bufferReferenceSystem = CRS.decode(code);

      MathTransform toTransform = CRS.findMathTransform(DefaultGeographicCRS.WGS84, bufferReferenceSystem);
      MathTransform fromTransform = CRS.findMathTransform(bufferReferenceSystem, DefaultGeographicCRS.WGS84);

      org.locationtech.jts.geom.Geometry pGeom = JTS.transform(geometry, toTransform);
      BufferParameters bufferParameters = new BufferParameters();
      bufferParameters.setEndCapStyle(BufferParameters.CAP_SQUARE);
      bufferParameters.setJoinStyle(BufferParameters.JOIN_MITRE);
      org.locationtech.jts.geom.Geometry buffer = BufferOp.bufferOp(pGeom,distanceInMeters,bufferParameters);
      bufferedGeometry = JTS.transform(buffer, fromTransform);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return bufferedGeometry;
  }

  public static GeoJSON getBuffer(FeatureCollection geojson) {
    Geometry geometry = geojson.getFeatures()[0].getGeometry();

    org.locationtech.jts.geom.Geometry geom = reader.read(geometry,factory);
    org.locationtech.jts.geom.Geometry buffer = buffer(geom, 2000);

    return new FeatureCollection(new Feature[]{new Feature(writer.write(buffer), new HashMap<>())});
  }


  public static GeoJSON getLines(FeatureCollection geojson, int resolution) {
    Geometry geometry = geojson.getFeatures()[0].getGeometry();

    org.locationtech.jts.geom.Geometry read = reader.read(geometry, factory);
    Set<Long> outerHexagons = getOuterHexagons(read, resolution);
    String color1 = "#f17e7e";
    HashMap<String, Object> colorProperties1 = createColorProperties(color1, color1);
    FeatureCollection featureCollection1 = h3IndexListToGeoJSON(geojson.getFeatures()[0], outerHexagons, colorProperties1);
    return featureCollection1;
  }

}


